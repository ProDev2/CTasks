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

package gvoid.concurrent.exec;

import annotation.GuardedBy;
import annotation.NonNull;
import annotation.Nullable;
import java.io.Closeable;

@SuppressWarnings("unused")
public class Request implements Runnable, Closeable {
    public static final int STATE_NONE = 0x0;
    public static final int STATE_STARTED = 0x1 << 25;
    public static final int STATE_READY = STATE_STARTED | 0x1 << 24;
    public static final int STATE_DONE = STATE_STARTED | 0x1 << 31;
    public static final int STATE_RUNNING = STATE_READY | 0x1 << 26;
    public static final int STATE_CANCELED = STATE_DONE | 0x1 << 27;
    public static final int STATE_SUCCESS = STATE_DONE | 0x1 << 28;
    public static final int STATE_FAILED = STATE_DONE | 0x1 << 29;
    public static final int STATE_POST_FAILED = STATE_DONE | 0x1 << 30;

    @NonNull
    public final Object mLock;
    @Nullable
    public volatile Executable<Request> mExec;
    @Nullable
    public volatile Handleable mPostExec;

    @GuardedBy("mLock")
    private volatile int mState;
    @GuardedBy("mLock")
    private volatile Throwable mThrow;

    public Request() {
        this(null, null, null);
    }

    public Request(@Nullable Executable<Request> exec) {
        this(exec, null, null);
    }

    public Request(@Nullable Executable<Request> exec,
                   @Nullable Handleable postExec) {
        this(exec, postExec, null);
    }

    protected Request(@Nullable Executable<Request> exec,
                      @Nullable Handleable postExec,
                      @Nullable Object lock) {
        if (lock == null) {
            lock = this;
        }

        mLock = lock;
        mExec = exec;
        mPostExec = postExec;

        synchronized (mLock) {
            mState = STATE_NONE;
            mThrow = null;
        }
    }

    public int getState() {
        return mState;
    }

    public final boolean isState(int s) {
        return (getState() & s) == s;
    }

    public final boolean isStarted() {
        return (getState() & STATE_READY) != 0;
    }

    public final boolean isReady() {
        return (getState() & STATE_READY) == STATE_READY;
    }

    public final boolean isWaiting() {
        return (getState()
                & (STATE_RUNNING | STATE_DONE)
                & ~STATE_READY
        ) == 0;
    }

    public final boolean isRunning() {
        return (getState() & STATE_RUNNING) == STATE_RUNNING;
    }

    public final boolean isDone() {
        return (getState() & STATE_DONE) == STATE_DONE;
    }

    public final boolean isCanceled() {
        return (getState() & STATE_CANCELED) == STATE_CANCELED;
    }

    public final boolean isSuccess() {
        return (getState() & STATE_SUCCESS & ~STATE_DONE) != 0;
    }

    public final boolean isFailed() {
        return (getState() & STATE_FAILED & ~STATE_DONE) != 0;
    }

    public final boolean isFailedPost() {
        return (getState() & STATE_POST_FAILED & ~STATE_DONE) != 0;
    }

    @Nullable
    public Throwable getCause() {
        return mThrow;
    }

    public void start() {
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_READY) != 0) return;
            state = STATE_STARTED;
            mState = state;
            mThrow = null;
            mLock.notifyAll();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean cancel() {
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_CANCELED) == STATE_CANCELED) return true;
            if ((state & STATE_DONE) == STATE_DONE) return false;
            state |= STATE_CANCELED;
            mState = state;
            mLock.notifyAll();
            return true;
        }
    }

    public boolean ready() {
        synchronized (mLock) {
            int state = mState;
            if ((state & STATE_READY) == STATE_READY) return true;
            if ((state & STATE_DONE) != STATE_STARTED) return false;
            state |= STATE_READY;
            state &= ~STATE_STARTED;
            mState = state;
        }

        Throwable throwable = null;
        boolean ready = false, failed = false;
        try {
            ready = onPrepare();
        } catch (Throwable tr) {
            failed = true;
            throwable = tr;
        } finally {
            synchronized (mLock) {
                int state = mState & ~STATE_READY;
                state |= STATE_STARTED;
                if ((state & STATE_DONE) == STATE_DONE) {
                    ready = false;
                } else if (ready) {
                    state |= STATE_READY;
                } else if (ready = failed) {
                    state |= STATE_READY;
                    state |= STATE_FAILED & ~STATE_DONE;
                    mThrow = throwable;
                }
                mState = state;
                mLock.notifyAll();
            }
        }
        return ready;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean execute() {
        boolean skip;
        synchronized (mLock) {
            int state = mState;
            if ((state
                    & (STATE_RUNNING | STATE_DONE)
            ) != STATE_READY) return false;
            state |= STATE_RUNNING;
            mState = state;
            mLock.notifyAll();

            skip = (state
                    & (STATE_FAILED | STATE_SUCCESS)
                    & ~STATE_DONE
            ) != 0;
        }

        Handleable postHandle = null;
        Throwable throwable = null;
        boolean success = false, end = false;
        try {
            Executable<Request> exec = mExec;
            if (exec == null && !skip) {
                synchronized (mLock) {
                    mLock.wait(20L);
                    if ((mState & STATE_DONE) == STATE_DONE) return false;
                    exec = mExec;
                    postHandle = mPostExec;
                }
            } else {
                postHandle = mPostExec;
            }

            if (!skip) {
                if (exec == null) {
                    throw new NullPointerException("No executable attached");
                }

                exec.execute(this);
                success = true;
            }
        } catch (Throwable tr) {
            throwable = tr;
        } finally {
            synchronized (mLock) {
                int state = mState;
                if (throwable instanceof InterruptedException) {
                    state |= STATE_CANCELED;
                }

                if ((state & STATE_DONE) == STATE_DONE) {
                    success = false;
                    end = true;
                } else if (!skip) {
                    state |= success ? STATE_SUCCESS : STATE_FAILED;
                    mThrow = throwable;
                }

                state &= ~STATE_RUNNING;
                state |= STATE_READY | STATE_DONE;
                mState = state;
                mLock.notifyAll();
            }

            if (postHandle == null) {
                postHandle = mPostExec;
            }
        }
        if (end) return false;

        Handleable postH = postHandle;
        if (postH == null) return success;

        Runnable postExec = () -> {
            try {
                int st;
                Throwable tr;
                synchronized (mLock) {
                    st = mState;
                    tr = mThrow;
                }
                postH.handle(st, tr);
            } catch (Throwable tr) {
                synchronized (mLock) {
                    mState |= STATE_POST_FAILED;
                    if (mThrow != null) mThrow.addSuppressed(tr);
                    else mThrow = tr;
                    mLock.notifyAll();
                }
            }
        };

        try {
            onPostExecute(postExec);
        } catch (Throwable tr) {
            synchronized (mLock) {
                mState |= STATE_POST_FAILED;
                if (mThrow != null) mThrow.addSuppressed(tr);
                else mThrow = tr;
                mLock.notifyAll();
            }
        }

        return success;
    }

    @Override
    public void run() {
        execute();
    }

    @Override
    public void close() {
        cancel();
    }

    protected boolean onPrepare() {
        return true;
    }

    @SuppressWarnings("RedundantThrows")
    protected void onPostExecute(@NonNull Runnable runnable) throws Exception {
        runnable.run();
    }
}
