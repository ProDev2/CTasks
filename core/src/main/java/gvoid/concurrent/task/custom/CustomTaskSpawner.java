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

package gvoid.concurrent.task.custom;

import annotation.NonNull;
import annotation.Nullable;
import gvoid.concurrent.exec.loop.Handler;
import gvoid.concurrent.task.TaskSpawner;

@SuppressWarnings("unused")
public class CustomTaskSpawner extends TaskSpawner {
    @Nullable
    public Handler mHandler;
    @Nullable
    public Handler mPostHandler;
    public boolean mShutdown;

    public CustomTaskSpawner() {
        this(null, null, null);
    }

    public CustomTaskSpawner(@Nullable Handler handler,
                             @Nullable Handler postHandler) {
        this(handler, postHandler, null);
    }

    protected CustomTaskSpawner(@Nullable Handler handler,
                                @Nullable Handler postHandler,
                                @Nullable Object lock) {
        super(lock);

        mHandler = handler;
        mPostHandler = postHandler;
        mShutdown = false;
    }

    @Override
    public void close() {
        try {
            super.close();
        } finally {
            Handler handler = mHandler;
            mHandler = mPostHandler = null;

            if (handler != null
                    && mShutdown) {
                try {
                    handler.close();
                } catch (Throwable tr) {
                    tr.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    protected void onExecute(@NonNull Runnable runnable) throws Exception {
        synchronized (mLock) {
            throwIfClosed();
            Handler handler = mHandler;
            if (handler == null) {
                throw new NullPointerException("No handler attached");
            }
            handler.post(runnable);
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
        synchronized (mLock) {
            throwIfClosed();
            Handler postHandler = mPostHandler;
            if (postHandler == null) {
                throw new NullPointerException("No post handler attached");
            }
            postHandler.post(runnable);
        }
    }

    /* -------- Initialization -------- */
    @NonNull
    public static CustomTaskSpawner with(@Nullable Handler handler,
                                         @Nullable Handler postHandler) {
        return with(handler, postHandler, true);
    }

    @NonNull
    public static CustomTaskSpawner with(@Nullable Handler handler,
                                         @Nullable Handler postHandler,
                                         boolean isShared) {
        CustomTaskSpawner spawner = new CustomTaskSpawner();
        spawner.mHandler = handler;
        spawner.mPostHandler = postHandler;
        spawner.mShutdown = !isShared;
        return spawner;
    }
}
