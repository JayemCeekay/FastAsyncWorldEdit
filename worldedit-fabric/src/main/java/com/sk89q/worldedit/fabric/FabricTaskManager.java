package com.sk89q.worldedit.fabric;

import com.fastasyncworldedit.core.util.TaskManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class FabricTaskManager extends TaskManager {

    public static FabricTickListener tickListener = new FabricTickListener();

    private final FabricWorldEdit mod;

    public FabricTaskManager(final FabricWorldEdit mod) {
        this.mod = mod;
    }


    @Override
    public int repeat(@NotNull final Runnable runnable, final int interval) {
        tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount()+1,
                runnable, interval, false
                , true
        ));
        return tickListener.getTasksQueue().size();
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount()+1,
                runnable, interval, true
                , true
        ));
        return tickListener.getTasksQueue().size();
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount()+1,
                runnable, 0, true
                , false
        ));
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount()+1,
                runnable, 0, false
                , false
        ));
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
       tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount() + delay+1,
                runnable, 0, false
                , false
        ));

    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        tickListener.addTask(new FabricTickListener.Task(tickListener.getTasksQueue().size(),
                FabricWorldEdit.server.getTickCount() + delay+1,
                runnable, 0, true
                , false
        ));
    }


    @Override
    public void cancel(final int task) {
        if (task != -1) {
            tickListener.getTasksQueue().remove(tickListener.getTasksQueue().stream().filter(t -> t.taskId == task).findFirst().orElse(null));
        }
    }




}
