package com.sk89q.worldedit.fabric.fawe.plotsquared;

import com.fastasyncworldedit.core.util.TaskManager;
import com.plotsquared.core.command.Command;
import com.plotsquared.core.command.CommandCategory;
import com.plotsquared.core.command.CommandDeclaration;
import com.plotsquared.core.command.MainCommand;
import com.plotsquared.core.command.RequiredType;
import com.plotsquared.core.configuration.caption.TranslatableCaption;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.util.StringMan;
import com.plotsquared.core.util.task.RunnableVal2;
import com.plotsquared.core.util.task.RunnableVal3;
import com.plotsquared.fabric.FabricPlatform;
import com.plotsquared.fabric.util.FabricUtil;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.SuggestionHelper;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.biome.Biomes;
import com.sk89q.worldedit.world.registry.BiomeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@CommandDeclaration(command = "generatebiome",
        permission = "plots.generatebiome",
        category = CommandCategory.APPEARANCE,
        requiredType = RequiredType.PLAYER,
        description = "Generate a biome in your plot",
        aliases = {"bg", "gb"},
        usage = "/plots generatebiome <biome>")
public class PlotSetBiome extends Command {

    public PlotSetBiome() {
        super(MainCommand.getInstance(), true);
    }

    @Override
    public CompletableFuture<Boolean> execute(
            final PlotPlayer<?> player,
            String[] args,
            RunnableVal3<Command, Runnable, Runnable> confirm,
            RunnableVal2<Command, CommandResult> whenDone
    ) throws CommandException {
        final Plot plot = check(player.getCurrentPlot(), TranslatableCaption.of("errors.not_in_plot"));
        checkTrue(
                plot.isOwner(player.getUUID()) || FabricWorldEdit.inst.getPermissionsProvider().hasPermission(
                        (ServerPlayer) player.getPlatformPlayer(),
                        "plots.admin.command" +
                                ".generatebiome"
                ),
                TranslatableCaption.of("permission.no_plot_perms")
        );
        if (plot.getRunning() != 0) {
            player.sendMessage(TranslatableCaption.of("errors.wait_for_timer"));
            return null;
        }
        checkTrue(args.length == 1, TranslatableCaption.of("commandconfig.command_syntax"),
                TagResolver.builder().tag("value", Tag.inserting(Component.text(getUsage()))).build()
        );
        final Set<CuboidRegion> regions = plot.getRegions();
        BiomeRegistry biomeRegistry =
                WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getRegistries()
                        .getBiomeRegistry();
        Collection<BiomeType> knownBiomes = BiomeTypes.values();
        final BiomeType biome = Biomes.findBiomeByName(knownBiomes, args[0], biomeRegistry);
        if (biome == null) {
            String biomes = StringMan.join(
                    BiomeType.REGISTRY.values(),
                    TranslatableCaption.of("blocklist.block_list_separator").getComponent(player)
            );
            player.sendMessage(TranslatableCaption.of("biome.need_biome"));
            player.sendMessage(
                    TranslatableCaption.of("commandconfig.subcommand_set_options_header"),
                    TagResolver.builder().tag("values", Tag.inserting(Component.text(biomes))).build()
            );
            return CompletableFuture.completedFuture(false);
        }
        confirm.run(this, () -> {
            if (plot.getRunning() != 0) {
                player.sendMessage(TranslatableCaption.of("errors.wait_for_timer"));
                return;
            }
            plot.addRunning();
            TaskManager.taskManager().async(() -> {
                EditSession session =
                        WorldEdit
                                .getInstance()
                                .newEditSessionBuilder()
                                .world(FabricAdapter.adapt(FabricUtil.getWorld(plot
                                        .getArea()
                                        .getWorldName())))
                                .checkMemory(false)
                                .allowedRegionsEverywhere()
                                .actor(FabricAdapter.adaptPlayer(FabricPlatform.SERVER.getPlayerList().getPlayer(player.getUUID())))
                                .limitUnlimited()
                                .build();
                long seed = ThreadLocalRandom.current().nextLong();
                for (CuboidRegion region : regions) {
                    session.regenerate(region, biome, seed);
                }
                session.flushQueue();
                plot.removeRunning();
            });
        }, null);

        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Collection<Command> tab(final PlotPlayer<?> player, final String[] args, final boolean space) {
        return SuggestionHelper.getNamespacedRegistrySuggestions(BiomeType.REGISTRY, args[0])
                .map(value -> value.toLowerCase(Locale.ENGLISH).replace("minecraft:", ""))
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ENGLISH)))
                .map(value -> new Command(null, false, value, "", RequiredType.PLAYER, null) {
                }).collect(Collectors.toList());
    }

}
