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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.Task;
import com.android.builder.tasks.WorkQueue;
import com.android.utils.ILogger;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.Writer;

/**
 * Records all the {@link ExecutionRecord} for a process, in order it was received and sends then
 * synchronously to a {@link JsonRecordWriter}.
 */
public class ProcessRecorder {

    @NonNull
    static ProcessRecorder get() {
        return ProcessRecorderFactory.INSTANCE.get();
    }

    public interface ExecutionRecordWriter {
        void write(@NonNull ExecutionRecord executionRecord);
    }

    static class JsonRecordWriter implements ExecutionRecordWriter {

        @NonNull
        private final Gson gson;
        @NonNull
        private final Writer writer;

        JsonRecordWriter(@NonNull Writer writer) {
            this.gson = new Gson();
            this.writer = writer;
        }

        @Override
        public void write(@NonNull ExecutionRecord executionRecord) {
            String json = gson.toJson(executionRecord);
            try {
                writer.append(json);
                writer.append("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class WorkQueueContext implements QueueThreadContext<ExecutionRecordWriter> {
        @Override
        public void creation(@NonNull Thread t) throws IOException {
        }

        @Override
        public void runTask(@NonNull Job<ExecutionRecordWriter> job) throws Exception {
            job.runTask(singletonJobContext);
        }

        @Override
        public void destruction(@NonNull Thread t) throws IOException, InterruptedException {
        }

        @Override
        public void shutdown() {
        }
    }

    @NonNull
    private final JobContext<ExecutionRecordWriter> singletonJobContext;
    @NonNull
    private final WorkQueue<ExecutionRecordWriter> workQueue;

    ProcessRecorder(@NonNull ExecutionRecordWriter outWriter, @NonNull ILogger iLogger) {
        this.singletonJobContext = new JobContext<ExecutionRecordWriter>(outWriter);
        workQueue = new WorkQueue<ExecutionRecordWriter>(
                iLogger, new WorkQueueContext(), "execRecordWriter", 1, 0);
    }

    void writeRecord(@NonNull final ExecutionRecord executionRecord) {

        try {
            workQueue.push(new Job<ExecutionRecordWriter>("recordWriter", new Task<ExecutionRecordWriter>() {
                @Override
                public void run(@NonNull Job<ExecutionRecordWriter> job,
                        @NonNull JobContext<ExecutionRecordWriter> context) throws IOException {
                    context.getPayload().write(executionRecord);
                    job.finished();
                }
            }));
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Done with the recording processing, finish processing the outstanding {@link ExecutionRecord}
     * publication and shutdowns the processing queue.
     *
     * @throws InterruptedException
     */
    public void finish() throws InterruptedException {
        workQueue.shutdown();
    }

}
