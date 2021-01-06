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

package gvoid.concurrent.util;

import annotation.NonNull;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public final class ExecutorHelper {
    /* -------- Defaults -------------- */
    public static int DEFAULT_CORE_POOL_SIZE = 6;
    public static int DEFAULT_QUEUE_CAPACITY = 20;
    public static long DEFAULT_KEEP_ALIVE_TIME = 20L * 1000L;

    /* -------- Initialization -------- */
    @NonNull
    public static ExecutorService create() {
        return create(
                DEFAULT_CORE_POOL_SIZE,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_KEEP_ALIVE_TIME
        );
    }

    @NonNull
    public static ExecutorService create(int corePoolSize) {
        return create(
                corePoolSize,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_KEEP_ALIVE_TIME
        );
    }

    @NonNull
    public static ExecutorService create(int corePoolSize,
                                         int queueCapacity) {
        return create(
                corePoolSize,
                queueCapacity,
                DEFAULT_KEEP_ALIVE_TIME
        );
    }

    @NonNull
    public static ExecutorService create(int corePoolSize,
                                         int queueCapacity,
                                         long keepAliveTime) {
        corePoolSize = Math.max(corePoolSize, 0);
        keepAliveTime = Math.max(keepAliveTime, 0L);

        int maxPoolSize;
        BlockingQueue<Runnable> queue;
        if (queueCapacity <= 0) {
            maxPoolSize = Integer.MAX_VALUE;
            queue = new SynchronousQueue<>();
        } else if (queueCapacity >= Integer.MAX_VALUE) {
            maxPoolSize = Math.max(corePoolSize, 1);
            queue = new LinkedBlockingQueue<>(Integer.MAX_VALUE);
        } else {
            maxPoolSize = Integer.MAX_VALUE;
            queue = new LinkedBlockingQueue<>(queueCapacity);
        }

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                queue
        );
        if (corePoolSize > 0 && keepAliveTime > 0L) {
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    private ExecutorHelper() {
        throw new UnsupportedOperationException();
    }
}
