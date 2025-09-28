package com.proclaws;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class Scheduler {
    private final ScheduledExecutorService scheduler =
       Executors.newScheduledThreadPool(1);
    private final HashMap<UUID, ScheduledFuture<?>> tasks = new HashMap<>();

    /** Schedules a task to run at a fixed rate on interval [start, end]. */
    public ScheduledFuture<?> scheduleTask(UUID id, Runnable action, LocalDateTime start, long period, TimeUnit unit) {
        long duration = java.time.Duration.between(LocalDateTime.now(), start).toMillis();
        // assumption: start immediately if start time is in the past
        if (duration < 0) { 
            System.out.println("start time is in the past, starting immediately.");
            duration = 0; 
        }
        long delay = unit.convert(duration, TimeUnit.MILLISECONDS);

        final ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(action, delay, period, unit);
        tasks.put(id, task);
        return task;
    }

    public void cancelTask(UUID id, long delay, TimeUnit unit) {
        if (!tasks.containsKey(id)) {
            throw new IllegalArgumentException("No task scheduled with ID: " + id);
        }
        scheduler.schedule(() -> tasks.get(id).cancel(true), delay, unit);
    }

    public void cancelAllTasks(long delay, TimeUnit unit) {
        tasks.keySet().forEach(id -> cancelTask(id, delay, unit));
        tasks.clear();
    }
}