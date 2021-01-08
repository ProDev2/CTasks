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
import gvoid.concurrent.exec.Executable;
import gvoid.concurrent.exec.Request;
import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

@SuppressWarnings("unused")
public class Handler implements Closeable {
    public static long RETRY_TIMEOUT = 20L;

    @NonNull
    public final Object mLock;

    @GuardedBy("mLock")
    private volatile boolean mBusy;
    @GuardedBy("mLock")
    private final Deque<Request> mTasks;
    @GuardedBy("mLock")
    private final List<Entry> mTimedTasks;

    private volatile boolean mClosed;

    public Handler() {
        this(null);
    }

    protected Handler(@Nullable Object lock) {
        if (lock == null) {
            lock = this;
        }

        mLock = lock;

        synchronized (mLock) {
            mBusy = false;

            mTasks = new ArrayDeque<>(8);
            mTimedTasks = new ArrayList<>(8);
        }

        mClosed = false;
    }

    public boolean isClosed() {
        return mClosed;
    }

    protected void throwIfClosed() {
        if (mClosed)
            throw new IllegalStateException("Handler is closed");
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public final Request post(@NonNull Object runnable) {
        return push(runnable, null, null);
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public final Request postDelayed(@NonNull Object runnable, long delay) {
        return push(runnable, delay, null);
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public final Request postAtTime(@NonNull Object runnable, long time) {
        return push(runnable, null, time);
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected Request push(@NonNull Object runnable, @Nullable Long delay, @Nullable Long time) {
        throwIfClosed();

        //noinspection ConstantConditions
        if (runnable == null) {
            throw new NullPointerException("No runnable object attached");
        }

        Request request;
        prepare: {
            if (runnable instanceof Request) {
                request = (Request) runnable;
                break prepare;
            }

            if (runnable instanceof Runnable) {
                Runnable run = (Runnable) runnable;
                runnable = (Executable<Request>) r -> run.run();
            }
            if (runnable instanceof Executable<?>) {
                Executable<Request> exec;
                try {
                    //noinspection unchecked
                    exec = (Executable<Request>) runnable;
                } catch (ClassCastException ignored) {
                    Executable<?> run = (Executable<?>) runnable;
                    exec = r -> run.execute(null);
                }
                runnable = new Request(exec);
            }

            if (runnable instanceof Request) {
                request = (Request) runnable;
                break prepare;
            }

            throw new IllegalArgumentException("Invalid runnable object");
        }
        request.start();

        if (delay == null && time == null) {
            push(request);
        } else if (delay == null) {
            push(new Entry(request, time));
        } else if (time == null) {
            try {
                time = getTime() + delay;
            } catch (Throwable ignored) {
            }
            if (time == null) push(request);
            else push(new Entry(request, time));
        } else {
            time += delay;
            push(new Entry(request, time));
        }
        return request;
    }

    private void push(@NonNull Request request) {
        synchronized (mLock) {
            if (mClosed) return;

            mTasks.addFirst(request);
            mLock.notifyAll();
        }
    }

    private void push(@NonNull Entry entry) {
        synchronized (mLock) {
            if (mClosed) return;

            int index = Collections.binarySearch(
                    mTimedTasks, entry, Entry::compare
            );
            if (index < 0) index = ~index;
            mTimedTasks.add(index, entry);
            mLock.notifyAll();
        }
    }

    @NonNull
    public final List<Request> getAll() {
        return getAll(false);
    }

    @NonNull
    public List<Request> getAll(boolean excludeTimed) {
        List<Request> tmpTasks;
        synchronized (mLock) {
            int size = mTasks.size();
            if (!excludeTimed) {
                size += mTimedTasks.size();
            }
            tmpTasks = new ArrayList<>(size);
            for (Request request : mTasks) {
                if (request == null) continue;
                tmpTasks.add(request);
            }
            if (!excludeTimed) {
                for (Entry entry : mTimedTasks) {
                    if (entry == null) continue;
                    tmpTasks.add(entry.mRequest);
                }
            }
        }
        return tmpTasks;
    }

    public final void cancelAll() {
        cancelAll(false);
    }

    public void cancelAll(boolean excludeTimed) {
        synchronized (mLock) {
            List<Request> tmpTasks;
            try {
                tmpTasks = getAll(excludeTimed);
            } finally {
                removeAll(excludeTimed);
            }

            for (Request request : tmpTasks) {
                try {
                    request.cancel();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public final void removeAll() {
        removeAll(false);
    }

    public void removeAll(boolean excludeTimed) {
        synchronized (mLock) {
            mTasks.clear();
            if (!excludeTimed) {
                mTimedTasks.clear();
            }
        }
    }

    @Nullable
    Request next(long timeout) throws InterruptedException {
        if (mClosed) return null;

        next: {
            if (mBusy) break next;
            synchronized (mLock) {
                if (mBusy) break next;
                mBusy = true;
            }
            if (mClosed) return null;

            try {
                boolean retry = false;
                int size;
                Entry entry = null;
                synchronized (mLock) {
                    while ((size = mTimedTasks.size()) > 0) {
                        entry = mTimedTasks.get(size - 1);
                        if (entry != null
                                && isValid(entry.mRequest)) break;
                        mTimedTasks.remove(size - 1);
                    }
                }
                entry:
                if (size > 0) {
                    long remTime = 0L;
                    try {
                        long t = getTime();
                        remTime = entry.getRemTime(t);
                    } catch (Throwable ignored) {
                    }
                    if (remTime > 0L) {
                        timeout = timeout != 0L
                                  ? Math.min(timeout, remTime)
                                  : remTime;
                        entry = null;
                        break entry;
                    }

                    boolean removed = false;
                    remove:
                    synchronized (mLock) {
                        if (size > mTimedTasks.size()) break remove;
                        Entry tmpEntry = mTimedTasks.get(size - 1);
                        if (entry != tmpEntry) break remove;
                        mTimedTasks.remove(size - 1);
                        removed = true;
                    }

                    try {
                        Request request = entry.mRequest;
                        if (request.ready()) {
                            return request;
                        }
                    } catch (Throwable ignored) {
                        removed = false;
                    }

                    retry = true;
                    if (!removed) entry = null;
                } else entry = null;

                if (mClosed) return null;

                Request request = null;
                synchronized (mLock) {
                    while ((size = mTasks.size()) > 0) {
                        request = mTasks.pollLast();
                        if (request != null
                                && isValid(request)) break;
                    }
                    if (entry != null && !mClosed) {
                        mTasks.addFirst(entry.mRequest);
                    }
                }
                request:
                if (size > 0) {
                    try {
                        if (request.ready()) {
                            return request;
                        }
                    } catch (Throwable ignored) {
                        break request;
                    }

                    retry = true;
                    synchronized (mLock) {
                        if (!mClosed) {
                            mTasks.addFirst(request);
                        }
                    }
                }

                if (!retry) break next;
                if (mClosed) return null;

                long retryTimeout = RETRY_TIMEOUT;
                timeout = timeout != 0L
                          ? Math.min(timeout, retryTimeout)
                          : retryTimeout;
            } finally {
                mBusy = false;
            }
        }

        if (timeout >= 0L && !mClosed) {
            synchronized (mLock) {
                mLock.wait(timeout);
            }
            return next(-1L);
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            mClosed = true;
            mLock.notifyAll();
        }
        removeAll(false);
    }

    protected long getTime() {
        return System.currentTimeMillis();
    }

    protected static boolean isValid(@NonNull Request request) {
        try {
            return request.isWaiting()
                    && request.isStarted();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static class Entry {
        @NonNull
        public final Request mRequest;
        public final long mAtTime;

        private Entry(@NonNull Request request, long atTime) {
            mRequest = request;
            mAtTime = atTime;
        }

        public long getRemTime(long time) {
            return Math.max(mAtTime - time, 0L);
        }

        public static int compare(@NonNull Entry e1, @NonNull Entry e2) {
            long diff = e2.mAtTime - e1.mAtTime;
            diff /= diff < 0L ? -diff : diff;
            return (int) diff;
        }
    }
}
