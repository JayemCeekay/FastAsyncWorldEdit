package com.sk89q.worldedit.fabric;

import net.minecraft.server.MinecraftServer;

import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FabricTickListener {

    private final PriorityQueue<Task> tasksQueue = new PriorityQueue<>();
    private final Lock lock = new ReentrantLock();

    public void addTask(Task task) {
        lock.lock();
        try {
            tasksQueue.add(task);
        } finally {
            lock.unlock();
        }
    }

    public void OnServerTick(MinecraftServer server) {
        long currentTick = server.getTickCount();

        lock.lock();
        try {
            while (!tasksQueue.isEmpty() && tasksQueue.peek().getTime() <= currentTick) {
                Task task = tasksQueue.poll();

                if (task != null) {
                    task.run();
                    if (task.isRepeat()) {
                        task.setTime(task.getTime() + task.interval);
                        tasksQueue.add(task);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public PriorityQueue<Task> getTasksQueue() {
        return tasksQueue;
    }

    public static class Task implements Comparable<Task> {
        public final int taskId;
        private long time;
        private final Runnable runnable;
        private final int interval;
        private final boolean repeat;
        private final boolean async;

        public Task(int taskId, long time, Runnable runnable, int interval, boolean async, boolean repeat) {
            this.taskId = taskId;
            this.time = time;
            this.runnable = runnable;
            this.interval = interval;
            this.repeat = repeat;
            this.async = async;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public boolean isRepeat() {
            return repeat;
        }

        @Override
        public int compareTo(Task other) {
            return Long.compare(this.time, other.time);
        }

        public void run() {
            if (async) {
                CompletableFuture.runAsync(runnable);
            } else {
                runnable.run();
            }
        }
    }
}

/*
    public static Set<Task> tasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void OnServerTick(MinecraftServer server) {
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
*/
