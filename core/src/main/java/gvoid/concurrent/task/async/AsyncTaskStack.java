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

package gvoid.concurrent.task.async;

import annotation.NonNull;
import annotation.Nullable;
import gvoid.concurrent.exec.loop.Handler;
import gvoid.concurrent.task.executor.ExecutorTaskStack;
import gvoid.concurrent.util.ExecutorHelper;
import java.util.concurrent.Executor;

@SuppressWarnings("unused")
public class AsyncTaskStack extends ExecutorTaskStack {
    @Nullable
    public Handler mHandler;

    public AsyncTaskStack() {
        this(null, null, null);
    }

    public AsyncTaskStack(@Nullable Executor executor,
                          @Nullable Handler handler) {
        this(executor, handler, null);
    }

    protected AsyncTaskStack(@Nullable Executor executor,
                             @Nullable Handler handler,
                             @Nullable Object lock) {
        super(executor, lock);

        mHandler = handler;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            try {
                super.close();
            } finally {
                mHandler = null;
            }
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
        synchronized (mLock) {
            throwIfClosed();
            Handler handler = mHandler;
            if (handler == null) {
                throw new NullPointerException("No handler attached");
            }
            handler.post(runnable);
        }
    }

    /* -------- Initialization -------- */
    @NonNull
    public static AsyncTaskStack with(@Nullable Executor executor,
                                      @Nullable Handler handler) {
        return with(executor, handler, true);
    }

    @NonNull
    public static AsyncTaskStack with(@Nullable Executor executor,
                                      @Nullable Handler handler,
                                      boolean isShared) {
        AsyncTaskStack stack = new AsyncTaskStack();
        stack.mExecutor = executor;
        stack.mHandler = handler;
        stack.mShutdown = !isShared;
        return stack;
    }

    @NonNull
    public static AsyncTaskStack create(@Nullable Handler handler) {
        return with(ExecutorHelper.create(), handler, false);
    }

    @NonNull
    public static AsyncTaskStack create(@Nullable Handler handler,
                                        int corePoolSize) {
        return with(ExecutorHelper.create(
                corePoolSize
        ), handler, false);
    }

    @NonNull
    public static AsyncTaskStack create(@Nullable Handler handler,
                                        int corePoolSize,
                                        int queueCapacity) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity
        ), handler, false);
    }

    @NonNull
    public static AsyncTaskStack create(@Nullable Handler handler,
                                        int corePoolSize,
                                        int queueCapacity,
                                        long keepAliveTime) {
        return with(ExecutorHelper.create(
                corePoolSize,
                queueCapacity,
                keepAliveTime
        ), handler, false);
    }
}
