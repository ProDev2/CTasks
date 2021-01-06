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

@SuppressWarnings("unused")
public final class States {
    public static final int STATE_NONE = 0x0;
    public static final int STATE_STARTED = 0x1 << 25;
    public static final int STATE_READY = STATE_STARTED | 0x1 << 24;
    public static final int STATE_DONE = STATE_STARTED | 0x1 << 31;
    public static final int STATE_RUNNING = STATE_READY | 0x1 << 26;
    public static final int STATE_CANCELED = STATE_DONE | 0x1 << 27;
    public static final int STATE_SUCCESS = STATE_DONE | 0x1 << 28;
    public static final int STATE_FAILED = STATE_DONE | 0x1 << 29;
    public static final int STATE_POST_FAILED = STATE_DONE | 0x1 << 30;

    public static boolean isStarted(int state) {
        return (state & STATE_READY) != 0;
    }

    public static boolean isReady(int state) {
        return (state & STATE_READY) == STATE_READY;
    }

    public static boolean isWaiting(int state) {
        return (state
                & (STATE_RUNNING | STATE_DONE)
                & ~STATE_READY
        ) == 0;
    }

    public static boolean isRunning(int state) {
        return (state & STATE_RUNNING & ~STATE_READY) != 0;
    }

    public static boolean isStillRunning(int state) {
        return (state
                & (STATE_RUNNING | STATE_DONE)
                & ~STATE_READY
        ) == (STATE_RUNNING & ~STATE_READY);
    }

    public static boolean isDone(int state) {
        return (state & STATE_DONE) == STATE_DONE;
    }

    public static boolean isCanceled(int state) {
        return (state & STATE_CANCELED) == STATE_CANCELED;
    }

    public static boolean isSuccess(int state) {
        return (state & STATE_SUCCESS & ~STATE_DONE) != 0;
    }

    public static boolean isFailed(int state) {
        return (state & STATE_FAILED & ~STATE_DONE) != 0;
    }

    public static boolean isFailedPost(int state) {
        return (state & STATE_POST_FAILED & ~STATE_DONE) != 0;
    }
}
