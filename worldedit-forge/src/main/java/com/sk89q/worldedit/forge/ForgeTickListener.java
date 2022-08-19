package com.sk89q.worldedit.forge;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

public class ForgeTickListener {

    public static long TickTime = 0;
    public static HashSet<Task> tasks = new HashSet<>();


    @SubscribeEvent
    public void OnServerTick(TickEvent.ServerTickEvent event) {
        for (Task task : tasks) {
            if (task.time == TickTime) {
                if(task.async) {
                    CompletableFuture.runAsync(task.runnable);
                } else {
                    task.runnable.run();
                }
                if (task.repeat) {
                    task.time = task.time + task.interval;
                } else {
                    tasks.remove(task);
                }
            }
        }
        TickTime = TickTime + 1;
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
