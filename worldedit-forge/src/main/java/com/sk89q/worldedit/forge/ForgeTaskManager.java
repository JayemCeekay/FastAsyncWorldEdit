package com.sk89q.worldedit.forge;

import com.fastasyncworldedit.core.util.TaskManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;


public class ForgeTaskManager extends TaskManager {

    private final ForgeWorldEdit mod;

    public ForgeTaskManager(final ForgeWorldEdit mod) {
        this.mod = mod;
    }


    @Override
    public int repeat(@NotNull final Runnable runnable, final int interval) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1, ForgeTickListener.TickTime,
                runnable, interval, false
                , true
        ));
        return ForgeTickListener.tasks.size();
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1, ForgeTickListener.TickTime,
                runnable, interval, true
                , true
        ));
        return ForgeTickListener.tasks.size();
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1, ForgeTickListener.TickTime,
                runnable, 0, false
                , false
        ));
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1, ForgeTickListener.TickTime,
                runnable, 0, false
                , false
        ));
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1,
                ForgeTickListener.TickTime + delay,
                runnable, 0, false
                , false
        ));

    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        ForgeTickListener.tasks.add(new ForgeTickListener.Task(ForgeTickListener.tasks.size() + 1,
                ForgeTickListener.TickTime + delay,
                runnable, 0, true
                , false
        ));
    }

    @Override
    public void cancel(final int task) {
        if (task != -1) {
            ForgeTickListener.tasks.remove(ForgeTickListener.tasks.stream().filter(t -> t.taskId == task).findFirst().orElse(null));
        }
    }

}
