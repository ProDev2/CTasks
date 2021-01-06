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

import annotation.NonNull;
import annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

@SuppressWarnings("unused")
public class TaskStack implements Closeable {
    @NonNull
    public final Object mLock;

    private Deque<Task> mTasks;

    public TaskStack() {
        this(null);
    }

    protected TaskStack(@Nullable Object lock) {
        if (lock == null) {
            lock = this;
        }

        mLock = lock;

        mTasks = new ArrayDeque<>(4);
    }

    public final boolean isClosed() {
        return mTasks == null;
    }

    protected final void throwIfClosed() {
        if (mTasks == null) {
            throw new IllegalStateException("Task stack is closed");
        }
    }

    @NonNull
    private Deque<Task> getTasksOrThrow() {
        Deque<Task> tasks = mTasks;
        if (tasks == null) {
            throw new IllegalStateException("Task stack is closed");
        }
        return tasks;
    }

    protected void update() {
        synchronized (mLock) {
            Iterator<Task> taskIterator = getTasksOrThrow().descendingIterator();
            while (taskIterator.hasNext()) {
                Task task = taskIterator.next();
                if (task != null
                        && task.isStarted()
                        && !task.isDone())
                    continue;
                if (task != null)
                    task.cancel();
                taskIterator.remove();
            }
        }
    }

    public int taskCount() {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() > 0) update();
            return tasks.size();
        }
    }

    @NonNull
    public Task execute() {
        return execute(null, null);
    }

    @NonNull
    public Task execute(@Nullable Executable<Task> exec) {
        return execute(exec, null);
    }

    @NonNull
    public Task execute(@Nullable Executable<Task> exec,
                        @Nullable Handleable postExec) {
        synchronized (mLock) {
            Task task = next(exec, postExec);
            task.execute();
            return task;
        }
    }

    @NonNull
    public Task next() {
        return next(null, null);
    }

    @NonNull
    public Task next(@Nullable Executable<Task> exec) {
        return next(exec, null);
    }

    @NonNull
    public Task next(@Nullable Executable<Task> exec,
                     @Nullable Handleable postExec) {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() > 0) update();

            Task task = new Task(exec, postExec, mLock) {
                @Override
                protected void onExecute(@NonNull Runnable runnable) throws Exception {
                    TaskStack.this.onExecute(runnable);
                }

                @Override
                protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
                    TaskStack.this.onPostExecute(runnable);
                }
            };
            if (!tasks.offerFirst(task)) {
                throw new IllegalStateException("Unable to preserve task");
            }
            return task;
        }
    }

    @NonNull
    public TaskStack notifyTasks() {
        synchronized (mLock) {
            mLock.notifyAll();
            return this;
        }
    }

    @Nullable
    public Task getPrimaryTask() {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() > 0) update();
            return tasks.peekFirst();
        }
    }

    public boolean cancel() {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() <= 0) return false;
            Task task = tasks.removeFirst();
            if (task == null) return false;
            return task.cancel();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TaskStack cancelAll() {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() <= 0) return this;

            Iterator<Task> taskIterator = tasks.descendingIterator();
            while (taskIterator.hasNext()) {
                Task task = taskIterator.next();
                if (task == null) continue;
                task.cancel();
            }
            tasks.clear();
            return this;
        }
    }

    @NonNull
    public TaskStack cancelPrevious() {
        synchronized (mLock) {
            Deque<Task> tasks = getTasksOrThrow();
            if (tasks.size() <= 0) return this;

            Task primTask = tasks.removeFirst();
            Iterator<Task> taskIterator = tasks.descendingIterator();
            while (taskIterator.hasNext()) {
                Task task = taskIterator.next();
                if (task == null) continue;
                task.cancel();
            }
            tasks.clear();
            if (primTask != null && !tasks.offerFirst(primTask)) {
                primTask.cancel();
            }
            return this;
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            cancelAll();
            mTasks = null;
        }
    }

    @SuppressWarnings("RedundantThrows")
    protected void onExecute(@NonNull Runnable runnable) throws Exception {
        new Thread(runnable).start();
    }

    @SuppressWarnings("RedundantThrows")
    protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
        runnable.run();
    }
}
