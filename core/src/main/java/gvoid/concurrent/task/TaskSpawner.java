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

@SuppressWarnings("unused")
public class TaskSpawner implements Closeable {
    @NonNull
    public final Object mLock;

    private boolean mClosed;

    public TaskSpawner() {
        this(null);
    }

    protected TaskSpawner(@Nullable Object lock) {
        if (lock == null) {
            lock = this;
        }

        mLock = lock;

        mClosed = false;
    }

    public final boolean isClosed() {
        return mClosed;
    }

    protected final void throwIfClosed() {
        if (mClosed) {
            throw new IllegalStateException("Task spawner is closed");
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
            Task task = spawn(exec, postExec);
            task.execute();
            return task;
        }
    }

    @NonNull
    public Task spawn() {
        return spawn(null, null);
    }

    @NonNull
    public Task spawn(@Nullable Executable<Task> exec) {
        return spawn(exec, null);
    }

    @NonNull
    public Task spawn(@Nullable Executable<Task> exec,
                      @Nullable Handleable postExec) {
        synchronized (mLock) {
            throwIfClosed();
            return new Task(exec, postExec, mLock) {
                @Override
                protected void onExecute(@NonNull Runnable runnable) throws Exception {
                    TaskSpawner.this.onExecute(runnable);
                }

                @Override
                protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
                    TaskSpawner.this.onPostExecute(runnable);
                }
            };
        }
    }

    @NonNull
    public TaskSpawner notifyTasks() {
        synchronized (mLock) {
            mLock.notifyAll();
            return this;
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            mClosed = true;
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
