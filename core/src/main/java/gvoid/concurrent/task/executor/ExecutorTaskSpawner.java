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

package gvoid.concurrent.task.executor;

import annotation.NonNull;
import annotation.Nullable;
import gvoid.concurrent.task.TaskSpawner;
import gvoid.concurrent.util.ExecutorHelper;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("unused")
public class ExecutorTaskSpawner extends TaskSpawner {
    @Nullable
    public Executor mExecutor;
    public boolean mShutdown;

    public ExecutorTaskSpawner() {
        this(null, null);
    }

    public ExecutorTaskSpawner(@Nullable Executor executor) {
        this(executor, null);
    }

    protected ExecutorTaskSpawner(@Nullable Executor executor,
                                  @Nullable Object lock) {
        super(lock);

        mExecutor = executor;
        mShutdown = false;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            try {
                super.close();
            } finally {
                Executor executor = mExecutor;
                mExecutor = null;

                if (executor instanceof ExecutorService
                        && mShutdown) {
                    try {
                        ((ExecutorService) executor).shutdown();
                    } catch (Throwable tr) {
                        tr.printStackTrace();
                    }
                }
            }
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    protected void onExecute(@NonNull Runnable runnable) throws Exception {
        synchronized (mLock) {
            throwIfClosed();
            Executor executor = mExecutor;
            if (executor == null) {
                throw new NullPointerException("No executor attached");
            }
            executor.execute(runnable);
        }
    }

    /* -------- Initialization -------- */
    @NonNull
    public static ExecutorTaskSpawner with(@Nullable Executor executor) {
        return with(executor, true);
    }

    @NonNull
    public static ExecutorTaskSpawner with(@Nullable Executor executor,
                                           boolean isShared) {
        ExecutorTaskSpawner spawner = new ExecutorTaskSpawner();
        spawner.mExecutor = executor;
        spawner.mShutdown = !isShared;
        return spawner;
    }

    @NonNull
    public static ExecutorTaskSpawner create() {
        return with(ExecutorHelper.create(), false);
    }

    @NonNull
    public static ExecutorTaskSpawner create(int corePoolSize) {
        return with(ExecutorHelper.create(
                corePoolSize
        ), false);
    }

    @NonNull
    public static ExecutorTaskSpawner create(int corePoolSize,
                                             int queueCapacity) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity
        ), false);
    }

    @NonNull
    public static ExecutorTaskSpawner create(int corePoolSize,
                                             int queueCapacity,
                                             long keepAliveTime) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity,
                keepAliveTime
        ), false);
    }
}
