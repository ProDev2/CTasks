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
import gvoid.concurrent.task.TaskSpawner;
import gvoid.concurrent.task.async.AsyncTaskSpawner;

public class MainAdvanced {
    public static void main(String[] args) {
        // No debug this time ;)
        new MainAdvanced().setupAndStart(false);
    }

    private Handler handler;
    private TaskSpawner spawner;

    public void setupAndStart(boolean debug) {
        /*
         * Create a handler to handle all
         * requests (posts)
         */
        handler = new Handler();
        handler.post((Executable<Request>) this::run);

        /*
         * Create a Task spawner who's tasks
         * are executed using an ExecutorService.
         * Once a task is done it will bring the result
         * back to the 'main thread' by adding (posting)
         * a request to the attached handler object
         */
        spawner = AsyncTaskSpawner.create(
                handler, // The handler to run on after the task is done
                2, // The core amount of threads
                3, // The queue size (Once it is full, more threads will be created
                10L * 1000L // The keep alive time of all threads
        );

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

        if (spawner != null) {
            spawner.close();
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
        handler.postDelayed((Runnable) () -> {
            System.out.println("Out 2");
        }, 13L * 1000L);


        handler.postDelayed((Runnable) () -> {
            System.out.println("Out 3");
        }, 18L * 1000L);


        spawner.execute().mExec = task -> {
            // Now running on ExecutorService

            // Do some stuff
            long threadId = Thread.currentThread().getId();
            for (int i = 0; i < 5 && !task.isCanceled(); i++) {
                Thread.sleep(2000);

                System.out.println(i + " > Running on ExecutorService's thread: " + threadId);
            }

            // Publish back to 'main thread'
            return () -> {
                // Now running on 'main thread' (handler) in this case

                long id = Thread.currentThread().getId();
                System.out.println("Publish result back on main thread: " + id);
            };
        };
    }
}
