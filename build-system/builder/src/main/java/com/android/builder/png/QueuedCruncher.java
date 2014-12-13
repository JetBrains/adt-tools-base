/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.Task;
import com.android.builder.tasks.WorkQueue;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.PngCruncher;
import com.android.utils.ILogger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * implementation of {@link com.android.ide.common.internal.PngCruncher} that queues request and
 * use a pool or aapt server processes to serve those.
 */
public class QueuedCruncher implements PngCruncher {

    // use an enum to ensure singleton.
    public enum Builder {
        INSTANCE;

        private final Map<String, QueuedCruncher> sInstances =
                new ConcurrentHashMap<String, QueuedCruncher>();
        private final Object sLock = new Object();

        /**
         * Creates a new {@link com.android.builder.png.QueuedCruncher} or return an existing one
         * based on the underlying AAPT executable location.
         * @param aaptLocation the APPT executable location.
         * @param logger the logger to use
         * @return a new of existing instance of the {@link com.android.builder.png.QueuedCruncher}
         */
        public QueuedCruncher newCruncher(
                @NonNull String aaptLocation,
                @NonNull ILogger logger) {
            synchronized (sLock) {
                logger.info("QueuedCruncher is using %1$s", aaptLocation);
                if (!sInstances.containsKey(aaptLocation)) {
                    QueuedCruncher queuedCruncher = new QueuedCruncher(aaptLocation, logger);
                    sInstances.put(aaptLocation, queuedCruncher);
                }
                return sInstances.get(aaptLocation);
            }
        }
    }


    @NonNull private final String mAaptLocation;
    @NonNull private final ILogger mLogger;
    // Queue responsible for handling all passed jobs with a pool of worker threads.
    @NonNull private final WorkQueue<AaptProcess> mCrunchingRequests;
    // list of outstanding jobs.
    @NonNull private final ConcurrentLinkedQueue<Job<AaptProcess>> mOutstandingJobs =
            new ConcurrentLinkedQueue<Job<AaptProcess>>();


    private QueuedCruncher(
            @NonNull String aaptLocation,
            @NonNull ILogger iLogger) {
        mAaptLocation = aaptLocation;
        mLogger = iLogger;
        QueueThreadContext<AaptProcess> queueThreadContext = new QueueThreadContext<AaptProcess>() {

            // move this to a TLS.
            @NonNull private final Map<String, AaptProcess> mAaptProcesses = new HashMap<String, AaptProcess>();

            @Override
            public void creation(Thread t) throws IOException {
                try {
                    mLogger.verbose("Thread(%1$s): create aapt slave",
                            Thread.currentThread().getName());
                    AaptProcess aaptProcess = new AaptProcess.Builder(mAaptLocation, mLogger).start();
                    assert aaptProcess != null;
                    aaptProcess.waitForReady();
                    mAaptProcesses.put(t.getName(), aaptProcess);
                } catch (InterruptedException e) {
                    mLogger.error(e, "Cannot start slave process");
                    e.printStackTrace();
                }
            }

            @Override
            public void runTask(Job<AaptProcess> job) throws Exception {
                job.runTask(
                        new JobContext<AaptProcess>(mAaptProcesses.get(Thread.currentThread().getName())));
            }

            @Override
            public void destruction(Thread t) throws IOException, InterruptedException {

                AaptProcess aaptProcess = mAaptProcesses.get(Thread.currentThread().getName());
                if (aaptProcess != null) {
                    mLogger.verbose("Thread(%1$s): notify aapt slave shutdown",
                            Thread.currentThread().getName());
                    aaptProcess.shutdown();
                    mAaptProcesses.remove(t.getName());
                    mLogger.verbose("Thread(%1$s): after shutdown queue_size=%2$d",
                            Thread.currentThread().getName(), mAaptProcesses.size());
                }
            }

            @Override
            public void shutdown() {
                if (!mAaptProcesses.isEmpty()) {
                    mLogger.warning("Process list not empty");
                    for (Map.Entry<String, AaptProcess> aaptProcessEntry : mAaptProcesses
                            .entrySet()) {
                        mLogger.warning("Thread(%1$s): queue not cleaned", aaptProcessEntry.getKey());
                        try {
                            aaptProcessEntry.getValue().shutdown();
                        } catch (Exception e) {
                            mLogger.error(e, "while shutting down" + aaptProcessEntry.getKey());
                        }
                    }
                }
                mAaptProcesses.clear();
            }
        };
        mCrunchingRequests = new WorkQueue<AaptProcess>(mLogger, queueThreadContext, "png-cruncher", 5, 2);
    }

    @Override
    public void crunchPng(@NonNull final File from, @NonNull final File to)
            throws InterruptedException, LoggedErrorException, IOException {
        final Job<AaptProcess> aaptProcessJob = new Job<AaptProcess>(
                "Cruncher " + from.getName(),
                new Task<AaptProcess>() {
            @Override
            public void run(Job<AaptProcess> job, JobContext<AaptProcess> context) throws IOException {
                mLogger.verbose("Thread(%1$s): begin executing job %2$s",
                        Thread.currentThread().getName(), job.getJobTitle());
                context.getPayload().crunch(from, to, job);
                mLogger.verbose("Thread(%1$s): done executing job %2$s",
                        Thread.currentThread().getName(), job.getJobTitle());
            }
        });
        mOutstandingJobs.add(aaptProcessJob);
        mCrunchingRequests.push(aaptProcessJob);
    }

    public void waitForAll() throws InterruptedException {
        mLogger.verbose("Thread(%1$s): begin waitForAll", Thread.currentThread().getName());
        Job<AaptProcess> aaptProcessJob = mOutstandingJobs.poll();
        while (aaptProcessJob != null) {
            mLogger.verbose("Thread(%1$s) : wait for {%2$s)", Thread.currentThread().getName(),
                    aaptProcessJob.toString());
            if (!aaptProcessJob.await()) {
                throw new RuntimeException(
                        "Crunching " + aaptProcessJob.getJobTitle() + " failed, see logs");
            }
            aaptProcessJob = mOutstandingJobs.poll();
        }
        mLogger.verbose("Thread(%1$s): end waitForAll", Thread.currentThread().getName());
    }

    @Override
    public void end() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            waitForAll();
            mOutstandingJobs.clear();
            mLogger.verbose("Job finished in %1$d", System.currentTimeMillis() - startTime);
        } finally {
            // even if we have failures, we need to shutdown property the sub processes.
            mCrunchingRequests.shutdown();
            mLogger.verbose("Shutdown finished in %1$d", System.currentTimeMillis() - startTime);
        }
    }
}
