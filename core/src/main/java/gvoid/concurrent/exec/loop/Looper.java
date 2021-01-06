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

package gvoid.concurrent.exec.loop;

import annotation.GuardedBy;
import annotation.NonNull;
import annotation.Nullable;
import gvoid.concurrent.exec.Request;

@SuppressWarnings("unused")
public class Looper implements Runnable {
    public static final int STATE_NONE = 0x0;
    public static final int STATE_STARTED = 0x1 << 25;
    public static final int STATE_READY = STATE_STARTED | 0x1 << 24;

    public static final long IMMEDIATE_TIMEOUT = -1L;
    public static final long NO_TIMEOUT = 0L;

    public static long DEFAULT_TIMEOUT = 700L;

    @NonNull
    public final Object mLock;
    @Nullable
    public volatile Handler mHandler;
    @Nullable
    public volatile FailHandler mFailHandler;

    @GuardedBy("mLock")
    private volatile int mState;

    public Looper() {
        this(null, null, null);
    }

    public Looper(@Nullable Handler handler) {
        this(handler, null, null);
    }

    public Looper(@Nullable Handler handler,
                  @Nullable FailHandler failHandler) {
        this(handler, failHandler, null);
    }

    protected Looper(@Nullable Handler handler,
                     @Nullable FailHandler failHandler,
                     @Nullable Object lock) {
        if (lock == null) {
            lock = this;
        }

        mLock = lock;
        mHandler = handler;
        mFailHandler = failHandler;

        synchronized (mLock) {
            mState = STATE_NONE;
        }
    }

    public int getState() {
        return mState;
    }

    public final boolean isState(int s) {
        return (getState() & s) == s;
    }

    public final boolean isStarted() {
        return (getState() & STATE_STARTED) == STATE_STARTED;
    }

    public final boolean isReady() {
        return (getState() & STATE_READY) == STATE_READY;
    }

    public void start() {
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_STARTED) == STATE_STARTED) return;
            state = STATE_READY;
            mState = state;
            mLock.notifyAll();
        }
    }

    public void stop() {
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_STARTED) != STATE_STARTED) return;
            state &= ~STATE_READY;
            mState = state;
            mLock.notifyAll();
        }

        Handler handler = mHandler;
        if (handler != null) {
            synchronized (handler.mLock) {
                handler.mLock.notifyAll();
            }
        }
    }

    public void handleNonBlocking() {
        try {
            handle(IMMEDIATE_TIMEOUT);
        } catch (InterruptedException ignored) {
        }
    }

    public void handleBlocking() throws InterruptedException {
        handle(DEFAULT_TIMEOUT);
    }

    public void handle(long timeout) throws InterruptedException {
        Handler handler = mHandler;
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_READY) != STATE_READY) return;
            state &= ~STATE_READY;
            if (handler != null && !handler.isClosed()) {
                state |= STATE_STARTED;
            }
            mState = state;
            mLock.notifyAll();
            if ((state & STATE_STARTED) != STATE_STARTED) return;
        }

        Request request;
        Throwable throwable = null;
        handle:
        try {
            //noinspection ConstantConditions
            request = handler.next(timeout);
            if (request == null) return;

            synchronized (mLock) {
                if ((mState & STATE_STARTED) != STATE_STARTED) break handle;
            }

            try {
                if (request.execute()) return;
            } catch (Throwable tr) {
                throwable = tr;
            }
        } finally {
            handler = mHandler;
            reset:
            synchronized (mLock) {
                int state = mState;
                if ((state & STATE_STARTED) != STATE_STARTED) break reset;
                if (handler == null || handler.isClosed()) {
                    state &= ~STATE_READY;
                } else state |= STATE_READY;
                mState = state;
                mLock.notifyAll();
            }
        }

        FailHandler failHandler = mFailHandler;
        if (failHandler != null) {
            try {
                failHandler.handle(request, throwable);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void run() {
        long timeout = DEFAULT_TIMEOUT;
        timeout = Math.max(timeout, 0L);

        try {
            while (isReady()) {
                handle(timeout);
            }
        } catch (InterruptedException ignored) {
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public static Thread startOnThread(@NonNull Looper looper) {
        looper.start();
        Thread thread = new Thread(looper);
        thread.start();
        return thread;
    }

    public interface FailHandler {
        void handle(@NonNull Request request, @Nullable Throwable throwable);
    }
}
