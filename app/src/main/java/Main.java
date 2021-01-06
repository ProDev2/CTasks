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

import annotation.NonNull;
import gvoid.concurrent.exec.Executable;
import gvoid.concurrent.exec.Request;
import gvoid.concurrent.exec.loop.Handler;
import gvoid.concurrent.exec.loop.Looper;

public class Main {
    public static void main(String[] args) {
        new Main().setupAndStart(true);
    }

    private Handler handler;

    public void setupAndStart(boolean debug) {
        /*
         * Create a handler to handle all
         * requests (posts)
         */
        handler = new Handler();
        handler.post((Executable<Request>) this::run);

        /*
         * Creates a single looper and
         * attaches the handler to it
         */
        Looper looper = new Looper();
        looper.mHandler = handler;

        /* Start looper on current thread
         * (This will block the current thread until
         * looper is stopped or handler is closed)
         */
        runLooper(looper, debug);
    }

    private static void runLooper(@NonNull Looper looper, boolean debug) {
        try {
            long id = Thread.currentThread().getId();

            looper.start();
            while (looper.isReady()) {
                looper.handle(Looper.NO_TIMEOUT);

                if (debug) System.out.println("!! Looper on thread " + id + " did a loop");
            }
        } catch (InterruptedException ignored) {
        }
    }

    public void destroy() {
        if (handler != null) {
            handler.cancelAll();
            handler.close();
        }
    }

    public void run(@NonNull Request request) {
        /*
         * Stop the handler and all
         * attached loopers after 20 seconds
         */
        handler.postDelayed((Runnable) () -> {
            System.out.println("Called destroy");
            destroy();
        }, 20L * 1000L);


        /*
         * Start some requests
         * (The Handler object can handle ['post']
         * Runnables, Executables and Requests)
         */
        handler.post((Runnable) () -> {
            System.out.println("Out 1");
        });


        handler.postDelayed((Runnable) () -> {
            System.out.println("Out 2");
        }, 1000L);


        handler.postDelayed((Runnable) () -> {
            System.out.println("Out 3");
        }, 10L * 1000L);


        handler.postDelayed((Runnable) () -> {
            System.out.println("Out 4");
        }, 15L * 1000L);
    }
}
