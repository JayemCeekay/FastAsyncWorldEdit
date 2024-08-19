/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.fabric;

import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.IFawe;
import com.fastasyncworldedit.core.queue.implementation.QueueHandler;
import com.fastasyncworldedit.core.queue.implementation.preloader.AsyncPreloader;
import com.fastasyncworldedit.core.queue.implementation.preloader.Preloader;
import com.fastasyncworldedit.core.regions.FaweMaskManager;
import com.fastasyncworldedit.core.util.TaskManager;
import com.fastasyncworldedit.core.util.WEManager;
import com.mojang.brigadier.CommandDispatcher;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.PermissionCondition;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.event.platform.PlatformUnreadyEvent;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.event.platform.SessionIdleEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.fabric.fawe.FabricFaweAdapter;
import com.sk89q.worldedit.fabric.fawe.FabricPlatformAdapter;
import com.sk89q.worldedit.fabric.fawe.plotsquared.PlotSquaredFeature;
import com.sk89q.worldedit.fabric.net.handler.WECUIPacketHandler;
import com.sk89q.worldedit.internal.anvil.ChunkDeleter;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.lifecycle.Lifecycled;
import com.sk89q.worldedit.util.lifecycle.SimpleLifecycled;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockCategory;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.fluid.FluidType;
import com.sk89q.worldedit.world.item.ItemCategory;
import com.sk89q.worldedit.world.item.ItemType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Logger;
import org.enginehub.piston.Command;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.item.ItemUseEvent;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.fabric.FabricAdapter.adaptPlayer;
import static com.sk89q.worldedit.internal.anvil.ChunkDeleter.DELCHUNKS_FILE_NAME;

/**
 * The Fabric implementation of WorldEdit.
 */
public class FabricWorldEdit implements ModInitializer, IFawe {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    public static final String MOD_ID = "worldedit";
    public static final String CUI_PLUGIN_CHANNEL = "cui";

    private boolean initialized = false;
    public static final Lifecycled<MinecraftServer> LIFECYCLED_SERVER;

    static {
        SimpleLifecycled<MinecraftServer> lifecycledServer = SimpleLifecycled.invalid();
        ServerLifecycleEvents.SERVER_STARTED.register(lifecycledServer::newValue);
        ServerLifecycleEvents.SERVER_STOPPING.register(__ -> lifecycledServer.invalidate());
        LIFECYCLED_SERVER = lifecycledServer;
    }

    public static MinecraftServer server;
    private FabricPermissionsProvider provider;

    private FabricPlatformAdapter platformAdapter;

    private Preloader preloader;

    public static FabricWorldEdit inst;

    private FabricPlatform platform;

    private FabricFaweAdapter adapter;
    private FabricConfiguration config;
    private Path workingDir;

    public Map<UUID, FabricPlayer> FabricPlayerCache = new HashMap<>();

    private ModContainer container;

    public FabricWorldEdit() {
        inst = this;
    }

    @Override
    public void onInitialize() {
        this.container = FabricLoader.getInstance().getModContainer("worldedit").orElseThrow(
                () -> new IllegalStateException("WorldEdit mod missing in Fabric")
        );

        // Setup working directory
        workingDir = FabricLoader.getInstance().getConfigDir().resolve("worldedit");
        if (!Files.exists(workingDir)) {
            try {
                Files.createDirectory(workingDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        this.platform = new FabricPlatform(this);

        WorldEdit.getInstance().getPlatformManager().register(platform);

        config = new FabricConfiguration(this);
        config.load();

        this.provider = getInitialPermissionsProvider();
        this.platformAdapter = new FabricPlatformAdapter();
        WECUIPacketHandler.init();
        try {
            this.adapter = new FabricFaweAdapter();
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        ServerTickEvents.END_SERVER_TICK.register(ThreadSafeCache.getInstance());
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        ServerLifecycleEvents.SERVER_STARTING.register(this::onStartingServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onStopServer);
        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnect);
        AttackBlockCallback.EVENT.register(this::onLeftClickBlock);
        UseBlockCallback.EVENT.register(this::onRightClickBlock);
        //UseItemCallback.EVENT.register(this::onRightClickAir);
        Stimuli.global().listen(ItemUseEvent.EVENT, this::onRightClickAir);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        LOGGER.info("WorldEdit for Fabric (version " + getInternalVersion() + ") is loaded");
    }

    private void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        WorldEdit.getInstance().getEventBus().post(new PlatformsRegisteredEvent());
        PlatformManager manager = WorldEdit.getInstance().getPlatformManager();
        Platform commandsPlatform = manager.queryCapability(Capability.USER_COMMANDS);
        if (commandsPlatform != platform || !platform.isHookingEvents()) {
            // We're not in control of commands/events -- do not register.
            return;
        }

        List<Command> commands = manager.getPlatformCommandManager().getCommandManager()
                .getAllCommands().toList();
        for (Command command : commands) {
            CommandWrapper.register(dispatcher, command);
            Set<String> perms = command.getCondition().as(PermissionCondition.class)
                    .map(PermissionCondition::getPermissions)
                    .orElseGet(Collections::emptySet);
            if (!perms.isEmpty()) {
                perms.forEach(getPermissionsProvider()::registerPermission);
            }
        }
    }

    public Actor wrapCommandSender(CommandSourceStack sender) {
        if (sender.isPlayer()) {
            return wrapPlayer(sender.getPlayer());
        } else if (!sender.isPlayer()) {
            return new FabricBlockCommandSender(WorldEdit.getInstance(), sender);
        }

        return new FabricCommandSender(WorldEdit.getInstance(), sender);
    }

    private FabricPermissionsProvider getInitialPermissionsProvider() {
        try {
            Class.forName("me.lucko.fabric.api.permissions.v0.Permissions", false, getClass().getClassLoader());
            return new FabricPermissionsProvider.LuckoFabricPermissionsProvider(platform);
        } catch (ClassNotFoundException ignored) {
            // fallback to vanilla
        }
        return new FabricPermissionsProvider.VanillaPermissionsProvider(platform);
    }

    private void setupRegistries(MinecraftServer server) {
        // Items
        for (ResourceLocation name : BuiltInRegistries.ITEM.keySet()) {
            if (ItemType.REGISTRY.get(name.toString()) == null) {
                ItemType.REGISTRY.register(name.toString(), new ItemType(name.toString()));
            }
        }
        // Entities
        for (ResourceLocation name : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (EntityType.REGISTRY.get(name.toString()) == null) {
                EntityType.REGISTRY.register(name.toString(), new EntityType(name.toString()));
            }
        }
        // Biomes
        for (ResourceLocation name : server.registryAccess().registryOrThrow(Registries.BIOME).keySet()) {
            if (BiomeType.REGISTRY.get(name.toString()) == null) {
                BiomeType.REGISTRY.register(name.toString(), new BiomeType(name.toString()));
            }
        }
        // Tags
        BuiltInRegistries.BLOCK.getTagNames().map(TagKey::location).forEach(name -> {
            if (BlockCategory.REGISTRY.get(name.toString()) == null) {
                BlockCategory.REGISTRY.register(name.toString(), new BlockCategory(name.toString()));
            }
        });
        BuiltInRegistries.ITEM.getTagNames().map(TagKey::location).forEach(name -> {
            if (ItemCategory.REGISTRY.get(name.toString()) == null) {
                ItemCategory.REGISTRY.register(name.toString(), new ItemCategory(name.toString()));
            }
        });
        for (ResourceLocation name : BuiltInRegistries.FLUID.keySet()) {
            if (FluidType.REGISTRY.get(name.toString()) == null) {
                FluidType.REGISTRY.register(name.toString(), new FluidType(name.toString()));
            }
        }
    }

    private void onServerStarted(MinecraftServer minecraftServer) {
        TaskManager.taskManager().later(this::setupPlotSquared, 0);
    }

    private void onStartingServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
        // if(!initialized) {
        ServerTickEvents.END_SERVER_TICK.register(FabricTaskManager.tickListener::OnServerTick);
        try {
            Fawe.set(this);
            Fawe.setupInjector();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        final Path delChunks = workingDir.resolve(DELCHUNKS_FILE_NAME);
        if (Files.exists(delChunks)) {
            ChunkDeleter.runFromFile(delChunks, true);
        }
        this.setupRegistries(minecraftServer);
        WorldEdit.getInstance().getEventBus().post(new PlatformReadyEvent(this.platform));
        WorldEdit.getInstance().loadMappings();
        // if (!this.getFaweAdapter().isInitialised()) {
        this.getFaweAdapter().init();
        //  }
        // this.initialized = true;
        //   }
        Fawe.instance().setMainThread();
        //   }
    }

    private void onStopServer(MinecraftServer minecraftServer) {
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getSessionManager().unload();
        WorldEdit.getInstance().getEventBus().post(new PlatformUnreadyEvent(platform));
    }

    /**
     * Used to wrap a Fabric Player as a WorldEdit Player.
     *
     * @param player a player
     * @return a wrapped player
     */
    public FabricPlayer wrapPlayer(ServerPlayer player) {
        //FAWE start - Use cache over returning a direct FabricPlayer
        FabricPlayer wePlayer = getCachedPlayer(player);
        if (wePlayer == null) {
            synchronized (player) {
                wePlayer = getCachedPlayer(player);
                if (wePlayer == null) {
                    wePlayer = new FabricPlayer(player);
                    FabricPlayerCache.put(player.getUUID(), wePlayer);
                    return wePlayer;
                }
            }
        }
        return wePlayer;
        //FAWE end
    }

    FabricPlayer getCachedPlayer(ServerPlayer player) {
        return FabricPlayerCache.get(player.getUUID());
    }

    FabricPlayer reCachePlayer(ServerPlayer player) {
        synchronized (player) {
            FabricPlayer wePlayer = new FabricPlayer(player);
            FabricPlayerCache.put(player.getUUID(), wePlayer);
            return wePlayer;
        }
    }

    private boolean shouldSkip() {
        if (platform == null) {
            return true;
        }

        return !platform.isHookingEvents(); // We have to be told to catch these events
    }

    private InteractionResult onLeftClickBlock(
            Player playerEntity,
            Level world,
            InteractionHand hand,
            BlockPos blockPos,
            Direction direction
    ) {
        if (shouldSkip() || hand == InteractionHand.OFF_HAND || world.isClientSide) {
            return InteractionResult.PASS;
        }

        WorldEdit we = WorldEdit.getInstance();
        FabricPlayer player = adaptPlayer((ServerPlayer) playerEntity);
        FabricWorld localWorld = getWorld(world.getServer().getLevel(world.dimension()));
        Location pos = new Location(
                localWorld,
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ()
        );
        com.sk89q.worldedit.util.Direction weDirection = FabricAdapter.adaptEnumFacing(direction);

        if (we.handleBlockLeftClick(player, pos, weDirection)) {
            return InteractionResult.SUCCESS;
        }

        if (we.handleArmSwing(player)) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private InteractionResult onRightClickBlock(
            Player playerEntity,
            Level world,
            InteractionHand hand,
            BlockHitResult blockHitResult
    ) {
        if (shouldSkip() || hand == InteractionHand.OFF_HAND || world.isClientSide) {
            return InteractionResult.PASS;
        }
        WorldEdit we = WorldEdit.getInstance();
        FabricPlayer player = adaptPlayer((ServerPlayer) playerEntity);
        FabricWorld localWorld = getWorld(world.getServer().getLevel(world.dimension()));
        Location pos = new Location(
                localWorld,
                blockHitResult.getBlockPos().getX(),
                blockHitResult.getBlockPos().getY(),
                blockHitResult.getBlockPos().getZ()
        );
        com.sk89q.worldedit.util.Direction direction = FabricAdapter.adaptEnumFacing(blockHitResult.getDirection());

        if (we.handleBlockRightClick(player, pos, direction)) {
            return InteractionResult.SUCCESS;
        }

        if (we.handleRightClick(player)) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private InteractionResultHolder<ItemStack> onRightClickAir(Player playerEntity, InteractionHand hand) {
        if (isPlayerLookingAtBlock(playerEntity, playerEntity.level())) {
            return InteractionResultHolder.pass(playerEntity.getItemInHand(hand));
        }

        ItemStack stackInHand = playerEntity.getItemInHand(hand);
        if (shouldSkip() || hand == InteractionHand.OFF_HAND || playerEntity.level().isClientSide) {
            return InteractionResultHolder.pass(stackInHand);
        }
        WorldEdit we = WorldEdit.getInstance();
        FabricPlayer player = adaptPlayer((ServerPlayer) playerEntity);

        if (we.handleRightClick(player)) {
            return InteractionResultHolder.success(stackInHand);
        }
        return InteractionResultHolder.pass(stackInHand);
    }

    // TODO Pass empty left click to server

    private boolean isPlayerLookingAtBlock(Player player, Level level) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookVector = player.getViewVector(1.0f); // Get the player's looking direction

        // Create a raycast context
        ClipContext raycastContext = new ClipContext(
                playerPos,
                playerPos.add(lookVector.scale(5.0f)), // Adjust the multiplier for desired ray length
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );

        // Perform the raycast and check if it hits the target block
        BlockHitResult blockHitResult = level.clip(raycastContext);

        return blockHitResult.getType() != HitResult.Type.MISS;
    }

    private void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
       FabricAdapter.adaptPlayer(handler.player).unregister();

        WorldEdit.getInstance().getEventBus()
                .post(new SessionIdleEvent(new FabricPlayer.SessionKeyImpl(handler.player)));
    }

    private void onPlayerJoin(
            ServerGamePacketListenerImpl serverGamePacketListener,
            PacketSender packetSender,
            MinecraftServer minecraftServer
    ) {
        FabricPlayer player = this.wrapPlayer(serverGamePacketListener.player);
        //If plugins do silly things like teleport, deop (anything that requires a perm-recheck) (anything that ultimately
        // requires a FabricPlayer at some point) then the retention of metadata by the server (as it's stored based on a
        // string value indescriminate of player a player relogging) means that a FabricPlayer caching an old player object
        // will be kept, cached and retrieved by FAWE. Adding a simple memory-based equality check when the player rejoins,
        // and then "invaliding" (redoing) the cache if the players are not equal, fixes this.
        if(player.getPlayer() != serverGamePacketListener.player) {
            player = this.reCachePlayer(serverGamePacketListener.player);
        }
        LocalSession session;
        if ((session = WorldEdit.getInstance().getSessionManager().getIfPresent(player)) != null) {
            session.loadDefaults(player, true);
        }
        //UpdateNotification.doUpdateNotification(player);
    }

    /**
     * Get the configuration.
     *
     * @return the Fabric configuration
     */
    FabricConfiguration getConfig() {
        return this.config;
    }

    /**
     * Get the session for a player.
     *
     * @param player the player
     * @return the session
     */
    public LocalSession getSession(ServerPlayer player) {
        checkNotNull(player);
        return WorldEdit.getInstance().getSessionManager().get(adaptPlayer(player));
    }

    /**
     * Get the WorldEdit proxy for the given world.
     *
     * @param world the world
     * @return the WorldEdit world
     */
    public FabricWorld getWorld(ServerLevel world) {
        checkNotNull(world);
        return new FabricWorld(world);
    }

    @Override
    public File getDirectory() {
        Path FaweDirectory = FabricLoader.getInstance().getConfigDir().resolve("FastAsyncWorldEdit");
        if (!Files.exists(FaweDirectory)) {
            try {
                Files.createDirectory(FaweDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return FaweDirectory.toFile();
    }

    @Override
    public TaskManager getTaskManager() {
        return new FabricTaskManager(inst);
    }

    @Override
    public Collection<FaweMaskManager> getMaskManagers() {
        final ArrayList<FaweMaskManager> managers = new ArrayList<>();
        return managers;
    }

    /**
     * Get the WorldEdit proxy for the platform.
     *
     * @return the WorldEdit platform
     */
    public String getPlatform() {
        return this.platform.getPlatformName();
    }

    public FabricPlatform getFabricPlatform() {
        return this.platform;
    }

    @Override
    public UUID getUUID(final String name) {
        return null;
    }

    @Override
    public String getName(final UUID uuid) {
        return null;
    }

    @Override
    public QueueHandler getQueueHandler() {
        return new FabricQueueHandler();
    }


    @Override
    public Preloader getPreloader(final boolean initialise) {
        return this.preloader == null && initialise ? (this.preloader = new AsyncPreloader()) : this.preloader;
    }

    @Override
    public FAWEPlatformAdapterImpl getPlatformAdapter() {
        return this.platformAdapter;
    }

    /**
     * Get the working directory where WorldEdit's files are stored.
     *
     * @return the working directory
     */
    public Path getWorkingDir() {
        return this.workingDir;
    }

    /**
     * Get the version of the WorldEdit-Fabric implementation.
     *
     * @return a version string
     */
    String getInternalVersion() {
        return container.getMetadata().getVersion().getFriendlyString();
    }

    public void setPermissionsProvider(FabricPermissionsProvider provider) {
        this.provider = provider;
    }

    public FabricPermissionsProvider getPermissionsProvider() {
        return provider;
    }

    public FabricFaweAdapter getFaweAdapter() {
        return this.adapter;
    }

    private void setupPlotSquared() {
        if (FabricLoader.getInstance().getModContainer("plotsquared-fabric").isPresent()) {
            WEManager.weManager().addManager(new PlotSquaredFeature());
            LOGGER.info("Mod 'PlotSquared-Fabric' found. Using it now.");
        }

    }

}
