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
import gvoid.concurrent.task.TaskStack;
import gvoid.concurrent.util.ExecutorHelper;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("unused")
public class ExecutorTaskStack extends TaskStack {
    @Nullable
    public Executor mExecutor;
    public boolean mShutdown;

    public ExecutorTaskStack() {
        this(null, null);
    }

    public ExecutorTaskStack(@Nullable Executor executor) {
        this(executor, null);
    }

    protected ExecutorTaskStack(@Nullable Executor executor,
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
    public static ExecutorTaskStack with(@Nullable Executor executor) {
        return with(executor, true);
    }

    @NonNull
    public static ExecutorTaskStack with(@Nullable Executor executor,
                                         boolean isShared) {
        ExecutorTaskStack stack = new ExecutorTaskStack();
        stack.mExecutor = executor;
        stack.mShutdown = !isShared;
        return stack;
    }

    @NonNull
    public static ExecutorTaskStack create() {
        return with(ExecutorHelper.create(), false);
    }

    @NonNull
    public static ExecutorTaskStack create(int corePoolSize) {
        return with(ExecutorHelper.create(
                corePoolSize
        ), false);
    }

    @NonNull
    public static ExecutorTaskStack create(int corePoolSize,
                                           int queueCapacity) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity
        ), false);
    }

    @NonNull
    public static ExecutorTaskStack create(int corePoolSize,
                                           int queueCapacity,
                                           long keepAliveTime) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity,
                keepAliveTime
        ), false);
    }
}
