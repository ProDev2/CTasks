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

import gvoid.concurrent.exec.Executable;
import gvoid.concurrent.exec.Request;
import gvoid.concurrent.exec.loop.Handler;
import gvoid.concurrent.exec.loop.Looper;

public class Test2 {
    public static void main(String[] args) {
        Handler handler = new Handler();
        handler.postDelayed((Executable<Request>) request -> {
            if (!request.isCanceled()) {
                System.out.println("\n\n");
                System.out.println("Called shutdown");
                handler.close();
            }
        }, 25000L);

        Looper.DEFAULT_TIMEOUT = Looper.NO_TIMEOUT;

        Looper looper1 = new Looper(handler);
        Looper.startOnThread(looper1);

        Looper looper2 = new Looper(handler);
        Looper.startOnThread(looper2);

        Looper looper3 = new Looper(handler);
        Looper.startOnThread(looper3);

        Executable<Request> executable = request -> {
            long id = Thread.currentThread().getId();

            System.out.println("\n");
            for (int i = 0; i < 10 && !request.isCanceled(); i++) {
                System.out.println(id + " => " + i);
                Thread.sleep(300L);
            }
        };

        handler.post(executable);

        handler.post(new Request(executable));

        handler.postDelayed((Runnable) () -> {
            System.out.println("Ok");
        }, 4000L);


        Request request = handler.postDelayed(executable, 5000L);
        try {
            Thread.sleep(6530L);
        } catch (Throwable tr) {
            tr.printStackTrace();
        }
        request.cancel();

        try {
            Thread.sleep(2000L);
        } catch (Throwable tr) {
            tr.printStackTrace();
        }

        handler.post(executable);
        handler.post(executable);
        handler.post(executable);
        handler.post(executable);
    }
}
