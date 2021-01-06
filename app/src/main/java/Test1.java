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

import gvoid.concurrent.task.TaskStack;
import gvoid.concurrent.task.executor.ExecutorTaskStack;

public class Test1 {
    public static void main(String[] args) {
        TaskStack stack = ExecutorTaskStack.create(
                2,
                0,
                10L * 1000L
        );

        for (int x = 0; x < 10; x++) {

            stack.execute().mExec = task -> {
                long id = Thread.currentThread().getId();

                for (int i = 0; i < 6; i++) {
                    System.out.println(id + " > " + i);

                    try {
                        Thread.sleep(500L);
                    } catch (Throwable tr) {
                        tr.printStackTrace();
                    }
                }
                return () -> System.out.println("Done");
            };

        }
    }
}
