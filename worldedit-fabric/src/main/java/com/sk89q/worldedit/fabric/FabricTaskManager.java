package com.sk89q.worldedit.fabric;

import com.fastasyncworldedit.core.util.TaskManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class FabricTaskManager extends TaskManager {


    private final FabricWorldEdit mod;

    public FabricTaskManager(final FabricWorldEdit mod) {
        this.mod = mod;
    }


    @Override
    public int repeat(@NotNull final Runnable runnable, final int interval) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(), FabricWorldEdit.server.getTickCount(),
                runnable, interval, false
                , true
        ));
        return FabricTickListener.tasks.size();
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(), FabricWorldEdit.server.getTickCount(),
                runnable, interval, true
                , true
        ));
        return FabricTickListener.tasks.size();
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(), FabricWorldEdit.server.getTickCount(),
                runnable, 0, false
                , false
        ));
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(),
                FabricWorldEdit.server.getTickCount(),
                runnable, 0, false
                , false
        ));
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(),
                FabricWorldEdit.server.getTickCount() + delay,
                runnable, 0, false
                , false
        ));

    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        FabricTickListener.tasks.add(new FabricTickListener.Task(FabricTickListener.tasks.size(),
                FabricWorldEdit.server.getTickCount() + delay,
                runnable, 0, true
                , false
        ));
    }

    @Override
    public void cancel(final int task) {
        if (task != -1) {
            FabricTickListener.tasks.remove(FabricTickListener.tasks.stream().filter(t -> t.taskId == task).findFirst().orElse(null));
        }
    }

}
