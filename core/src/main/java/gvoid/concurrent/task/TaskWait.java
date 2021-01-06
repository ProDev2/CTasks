/*
 * Copyright (c) 2021 GVoid (Pascal Gerner)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gvoid.concurrent.task;

import annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("unused")
public final class TaskWait {
    static final long UPDATE_PERIOD = 50L;

    /* -------- Wait for completion -------- */
    public static void awaitCompletion(Task task)
            throws InterruptedException {
        awaitState(task, Task.STATE_DONE);
    }

    public static void awaitCompletionOrThrow(Task task, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException, TimeoutException {
        awaitStateOrThrow(task, Task.STATE_DONE, timeout, unit);
    }

    public static boolean awaitCompletion(Task task, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException {
        return awaitState(task, Task.STATE_DONE, timeout, unit);
    }

    /* -------- Wait for state ------------- */
    public static void awaitState(Task task, int state)
            throws InterruptedException {
        awaitState(task, state, -1, null);
    }

    public static void awaitStateOrThrow(Task task, int state, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException, TimeoutException {
        if (awaitState(task, state, timeout, unit)) {
            throw new TimeoutException("Timed out");
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static boolean awaitState(Task task, int state, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException {
        if (task == null) return false;
        if (unit == null) unit = TimeUnit.MILLISECONDS;

        Object lock = task.mLock;
        synchronized (lock) {
            Long rt = null;
            long ct = getTime(), to = timeout >= 0L ? unit.toMillis(timeout) : -1L;
            while (!task.isState(state) && (to < 0L || (rt = ct - getTime() + to) > 0L)) {
                long wt = UPDATE_PERIOD;
                if (rt != null) wt = Math.min(wt, rt);
                lock.wait(wt);
            }
            return rt != null && rt <= 0L;
        }
    }

    /* -------- Wait while state ----------- */
    public static void waitWhileState(Task task, int state)
            throws InterruptedException {
        waitWhileState(task, state, -1, null);
    }

    public static void waitWhileStateOrThrow(Task task, int state, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException, TimeoutException {
        if (waitWhileState(task, state, timeout, unit)) {
            throw new TimeoutException("Timed out");
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static boolean waitWhileState(Task task, int state, long timeout, @Nullable TimeUnit unit)
            throws InterruptedException {
        if (task == null) return false;
        if (unit == null) unit = TimeUnit.MILLISECONDS;

        Object lock = task.mLock;
        synchronized (lock) {
            Long rt = null;
            long ct = getTime(), to = timeout >= 0L ? unit.toMillis(timeout) : -1L;
            while (task.isState(state) && (to < 0L || (rt = ct - getTime() + to) > 0L)) {
                long wt = UPDATE_PERIOD;
                if (rt != null) wt = Math.min(wt, rt);
                lock.wait(wt);
            }
            return rt != null && rt <= 0L;
        }
    }

    /* -------- Utilities ------------------ */
    static long getTime() {
        try {
            long time = System.currentTimeMillis();
            if (time < 0L) time = -1L;
            return time;
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private TaskWait() {
        throw new UnsupportedOperationException();
    }
}
