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

import annotation.GuardedBy;
import annotation.NonNull;
import annotation.Nullable;
import java.io.Closeable;

@SuppressWarnings("unused")
public class Task implements Runnable, Closeable {
    public static final int STATE_NONE = 0x0;
    public static final int STATE_STARTED = 0x1 << 25;
    public static final int STATE_DONE = STATE_STARTED | 0x1 << 31;
    public static final int STATE_RUNNING = STATE_STARTED | 0x1 << 26;
    public static final int STATE_CANCELED = STATE_DONE | 0x1 << 27;
    public static final int STATE_SUCCESS = STATE_DONE | 0x1 << 28;
    public static final int STATE_FAILED = STATE_DONE | 0x1 << 29;
    public static final int STATE_POST_FAILED = STATE_DONE | 0x1 << 30;

    @NonNull
    public final Object mLock;
    @Nullable
    public volatile Executable<Task> mExec;
    @Nullable
    public volatile Handleable mPostExec;

    @GuardedBy("mLock")
    private volatile int mState;
    @GuardedBy("mLock")
    private volatile Throwable mThrow;

    public Task() {
        this(null, null, null);
    }

    public Task(@Nullable Executable<Task> exec) {
        this(exec, null, null);
    }

    public Task(@Nullable Executable<Task> exec,
                @Nullable Handleable postExec) {
        this(exec, postExec, null);
    }

    protected Task(@Nullable Executable<Task> exec,
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
        return (getState() & STATE_STARTED) == STATE_STARTED;
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

    @SuppressWarnings("UnusedReturnValue")
    public boolean execute() {
        Runnable runnable = () -> {
            boolean skip;
            synchronized (mLock) {
                int state = mState;
                if ((state & STATE_STARTED) != STATE_STARTED) return;
                if ((state & STATE_DONE) == STATE_DONE) return;
                if ((state & STATE_RUNNING) == STATE_RUNNING) return;
                state |= STATE_RUNNING;
                mState = state;
                mLock.notifyAll();

                skip = (state
                        & (STATE_FAILED | STATE_SUCCESS)
                        & ~STATE_DONE
                ) != 0;
            }

            Runnable postRun = null;
            Handleable postHandle = null;
            Throwable throwable = null;
            boolean success = false, end = false;
            try {
                Executable<Task> exec = mExec;
                if (exec == null && !skip) {
                    synchronized (mLock) {
                        mLock.wait(20L);
                        if ((mState & STATE_DONE) == STATE_DONE) return;
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

                    postRun = exec.execute(this);
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
                    state |= STATE_DONE;
                    mState = state;
                    mLock.notifyAll();
                }

                if (postHandle == null) {
                    postHandle = mPostExec;
                }
            }
            if (end) return;

            Runnable postR = success ? postRun : null;
            Handleable postH = postHandle;
            if ((!success || postR == null)
                    && postH == null) return;

            Runnable postExec = () -> {
                if (postR != null) {
                    try {
                        postR.run();
                    } catch (Throwable tr) {
                        synchronized (mLock) {
                            mState |= STATE_POST_FAILED;
                            if (mThrow != null) mThrow.addSuppressed(tr);
                            else mThrow = tr;
                            mLock.notifyAll();
                        }
                    }
                }
                if (postH != null) {
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
                }
            };

            try {
                if (skip) postExec.run();
                else onPostExecute(postExec);
            } catch (Throwable tr) {
                synchronized (mLock) {
                    mState |= STATE_POST_FAILED;
                    if (mThrow != null) mThrow.addSuppressed(tr);
                    else mThrow = tr;
                    mLock.notifyAll();
                }
            }
        };

        exec: {
            synchronized (mLock) {
                int state = mState;
                if ((state & STATE_STARTED) == STATE_STARTED) break exec;
                state = STATE_STARTED;
                mState = state;
                mThrow = null;
                mLock.notifyAll();
            }

            try {
                onExecute(runnable);
                break exec;
            } catch (Throwable tr) {
                synchronized (mLock) {
                    int state = mState;
                    if ((state & (STATE_DONE | STATE_RUNNING)) == STATE_STARTED) {
                        state |= STATE_FAILED & ~STATE_DONE;
                    } else break exec;
                    mState = state;
                    mThrow = tr;
                }
            }
            try {
                onPostExecute(runnable);
                break exec;
            } catch (Throwable tr) {
                synchronized (mLock) {
                    int state = mState;
                    if ((state & (STATE_DONE | STATE_RUNNING)) == STATE_STARTED) {
                        state = (STATE_FAILED | STATE_POST_FAILED) & ~STATE_DONE;
                    } else break exec;
                    mState = state;
                    mThrow.addSuppressed(tr);
                    mLock.notifyAll();
                }
            }
            return false;
        }
        synchronized (mLock) {
            return (mState & STATE_CANCELED) != STATE_CANCELED;
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

    @Override
    public void run() {
        execute();
    }

    @Override
    public void close() {
        cancel();
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
