/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.common.runner;

import static com.google.common.base.Preconditions.checkNotNull;

import org.junit.runners.model.RunnerScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Version of {@link org.junit.runners.Parameterized} that executes methods in parallel.
 */
public class ParallelParameterized extends FilterableParameterized {

    private static class ThreadPoolScheduler implements RunnerScheduler {
        private ExecutorService executor;

        public ThreadPoolScheduler() {
            String threads = System.getProperty("junit.parallel.threads");
            checkNotNull(threads, "You have to specify the junit.parallel.threads property");
            executor = Executors.newFixedThreadPool(Integer.parseInt(threads));
        }

        @Override
        public void finished() {
            executor.shutdown();
            try {
                // Use the same timeout as Jenkins.
                executor.awaitTermination(60, TimeUnit.MINUTES);
            }
            catch (InterruptedException exc) {
                throw new RuntimeException(exc);
            }
        }

        @Override
        public void schedule(Runnable childStatement) {
            executor.submit(childStatement);
        }
    }

    public ParallelParameterized(Class klass) throws Throwable {
        super(klass);
        setScheduler(new ThreadPoolScheduler());
    }
}

