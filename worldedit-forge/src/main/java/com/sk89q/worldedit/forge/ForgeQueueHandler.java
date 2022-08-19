package com.sk89q.worldedit.forge;

import co.aikar.timings.Timings;
import com.fastasyncworldedit.core.queue.implementation.QueueHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ForgeQueueHandler extends QueueHandler {
    private volatile boolean timingsEnabled;
    private static boolean alertTimingsChange = true;

    private static Method timingsCheck;
    private static Field asyncCatcher;

    static {
        try {
            timingsCheck = Class.forName("co.aikar.timings.TimingsManager").getDeclaredMethod("recheckEnabled");
            timingsCheck.setAccessible(true);
        } catch (Throwable ignored) {
        }
        try {
            asyncCatcher = Class.forName("org.spigotmc.AsyncCatcher").getDeclaredField("enabled");
            asyncCatcher.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void startSet(boolean parallel) {
        if (parallel) {
            try {
                asyncCatcher.setBoolean(asyncCatcher, false);
                timingsEnabled = Timings.isTimingsEnabled();
                if (timingsEnabled) {
                    if (alertTimingsChange) {
                        alertTimingsChange = false;
                    }
                    Timings.setTimingsEnabled(false);
                    timingsCheck.invoke(null);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void endSet(boolean parallel) {
        if (parallel) {
            try {
                asyncCatcher.setBoolean(asyncCatcher, true);
                if (timingsEnabled) {
                    Timings.setTimingsEnabled(true);
                    timingsCheck.invoke(null);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

}
