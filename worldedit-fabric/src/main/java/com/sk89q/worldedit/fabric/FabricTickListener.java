package com.sk89q.worldedit.fabric;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

public class FabricTickListener {

    public static HashSet<Task> tasks = new HashSet<>();

    public static void OnServerTick() {
        ImmutableList.copyOf(tasks).forEach(task -> {
            if (task.time == FabricWorldEdit.server.getTickCount()) {
                if (task.repeat) {
                    task.time = task.time + task.interval;
                }
                if (task.async) {
                    CompletableFuture.runAsync(() -> task.runnable.run());
                } else {
                    task.runnable.run();
                }
                if(!task.repeat) {
                    tasks.remove(task);
                }
            }
        });
    }


    public static class Task {
        int taskId;
        long time;
        Runnable runnable;
        int interval;
        boolean repeat;
        boolean async;

        public Task(int taskId, long time, Runnable runnable, int interval, boolean async, boolean repeat) {
            this.taskId = taskId;
            this.time = time;
            this.runnable = runnable;
            this.interval = interval;
            this.repeat = repeat;
            this.async = async;
        }

    }
}
