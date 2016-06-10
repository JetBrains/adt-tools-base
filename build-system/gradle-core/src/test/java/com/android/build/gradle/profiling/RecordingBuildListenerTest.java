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

package com.android.build.gradle.profiling;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.profile.RecordingBuildListener;
import com.android.builder.profile.AsyncRecorder;
import com.android.builder.profile.NameAnonymizer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;
import com.android.builder.profile.ProcessRecorder;
import com.android.builder.profile.ProcessRecorderFactory;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.ILogger;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RecordingBuildListener}
 */
public class RecordingBuildListenerTest {

    @Mock
    Task mTask;

    @Mock
    Task mSecondTask;

    @Mock
    TaskState mTaskState;

    @Mock
    Project mProject;

    @Mock
    ILogger logger;

    private static final class TestRecorder implements Recorder {

        final AtomicLong recordId = new AtomicLong(0);
        final List<AndroidStudioStats.GradleBuildProfileSpan> records =
                new CopyOnWriteArrayList<>();

        @Override
        public <T> T record(@NonNull ExecutionType executionType, @NonNull String project,
                String variant, @NonNull Block<T> block) {
            throw new UnsupportedOperationException("record method was not supposed to be called.");
        }

        @Nullable
        @Override
        public <T> T record(
                @NonNull ExecutionType executionType,
                @Nullable AndroidStudioStats.GradleTransformExecution transform,
                @NonNull String project, @Nullable String variant, @NonNull Block<T> block) {
            throw new UnsupportedOperationException("record method was not supposed to be called");
        }

        @Override
        public long allocationRecordId() {
            return recordId.incrementAndGet();
        }

        @Override
        public void closeRecord(@NonNull String project, @Nullable String variant,
                @NonNull AndroidStudioStats.GradleBuildProfileSpan.Builder executionRecord) {
            if (project.equals(":projectName")) {
                executionRecord.setProject(1);
            }
            if ("variantName".equals(variant)) {
                executionRecord.setVariant(1);
            }

            records.add(executionRecord.build());
        }
    }

    private static final class TestExecutionRecordWriter implements ProcessRecorder.ExecutionRecordWriter {

        final List<AndroidStudioStats.GradleBuildProfileSpan> records =
                new CopyOnWriteArrayList<>();
        final Map<Long, Map<String, String>> attributes = Maps.newConcurrentMap();

        @Override
        public void write(@NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
                @NonNull Map<String, String> executionAttributes) throws IOException {
            records.add(executionRecord);
            attributes.put(executionRecord.getId(), executionAttributes);
        }

        List<AndroidStudioStats.GradleBuildProfileSpan> getRecords() {
            return records;
        }

        Map<String, String> getAttributes(long id) {
            return attributes.get(id);
        }

        @Override
        public void close() throws IOException {
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mTask.getName()).thenReturn("taskName");
        when(mSecondTask.getName()).thenReturn("task2Name");
    }

    @Test
    public void singleThreadInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener("projectName", recorder);

        listener.beforeExecute(mTask);
        listener.afterExecute(mTask, mTaskState);
        assertEquals(1, recorder.records.size());
        AndroidStudioStats.GradleBuildProfileSpan record = recorder.records.get(0);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        //assertEquals(2, record.attributes.size());
        //ensurePropertyValue(record.attributes, "task", "taskName");
        //ensurePropertyValue(record.attributes, "project", "projectName");
    }

    @Test
    public void singleThreadWithMultipleSpansInvocation() throws InterruptedException {

        TestExecutionRecordWriter recordWriter = new TestExecutionRecordWriter();
        ProcessRecorderFactory.initializeForTests(recordWriter);

        RecordingBuildListener listener =
                new RecordingBuildListener(":projectName", ThreadRecorder.get());

        listener.beforeExecute(mTask);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        Logger.getAnonymousLogger().finest("useless block");
                        return null;
                    }
                }); {
        }
        listener.afterExecute(mTask, mTaskState);
        ProcessRecorderFactory.shutdown();

        assertEquals(4, recordWriter.getRecords().size());
        AndroidStudioStats.GradleBuildProfileSpan record = getRecordForId(recordWriter.getRecords(), 2);
        Map<String, String> attributes = recordWriter.getAttributes(2);
        assertEquals(0, record.getParentId());
        assertEquals(1, attributes.size());
        ensurePropertyValue(attributes, "project", ":projectName");

        record = getRecordForId(recordWriter.getRecords(), 3);
        assertNotNull(record);
        assertEquals(2, record.getParentId());
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
    }

    @Test
    public void simulateTasksUnorderedLifecycleEventsDelivery() throws InterruptedException {

        TestExecutionRecordWriter recordWriter = new TestExecutionRecordWriter();
        ProcessRecorderFactory.initializeForTests(recordWriter);

        RecordingBuildListener listener =
                new RecordingBuildListener(":projectName", AsyncRecorder.get());

        listener.beforeExecute(mTask);
        listener.beforeExecute(mSecondTask);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Object>() {
                    @Override
                    public Object call() throws Exception {
                        logger.verbose("useless block");
                        return null;
                    }
                });
        listener.afterExecute(mTask, mTaskState);
        listener.afterExecute(mSecondTask, mTaskState);

        ProcessRecorderFactory.shutdown();

        assertEquals(5, recordWriter.getRecords().size());
        AndroidStudioStats.GradleBuildProfileSpan record =
                getRecordForId(recordWriter.getRecords(), 2);
        assertEquals(1, record.getProject());

        record = getRecordForId(recordWriter.getRecords(), 3);
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());

        record = getRecordForId(recordWriter.getRecords(), 4);
        assertNotNull(record);
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
    }

    @Test
    public void testInitialAndFinalRecords() throws InterruptedException {

        TestExecutionRecordWriter recordWriter = new TestExecutionRecordWriter();
        ProcessRecorderFactory.initializeForTests(recordWriter);

        ProcessRecorderFactory.shutdown();

        assertEquals(2, recordWriter.getRecords().size());
        for (AndroidStudioStats.GradleBuildProfileSpan  record : recordWriter.getRecords()) {
            System.out.println(record);
        }
        AndroidStudioStats.GradleBuildProfileSpan record = getRecordForId(recordWriter.getRecords(), 1);
        Map<String, String> attributes = recordWriter.getAttributes(1);
        assertEquals(0, record.getParentId());
        assertEquals(ExecutionType.INITIAL_METADATA, record.getType());
        assertEquals(6, attributes.size());
        ensurePropertyValue(attributes, "os_name", System.getProperty("os.name"));

        record = getRecordForId(recordWriter.getRecords(), 2);
        attributes = recordWriter.getAttributes(2);
        assertNotNull(record);
        assertEquals(0, record.getParentId());
        assertEquals(3, attributes.size());
        assertEquals(ExecutionType.FINAL_METADATA, record.getType());
    }

    @Test
    public void multipleThreadsInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(":projectName", recorder);
        Task secondTask = Mockito.mock(Task.class);
        when(secondTask.getName()).thenReturn("secondTaskName");
        when(secondTask.getProject()).thenReturn(mProject);

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask);

        // first thread finishes
        listener.afterExecute(mTask, mTaskState);

        // and second thread finishes
        listener.afterExecute(secondTask, mTaskState);

        assertEquals(2, recorder.records.size());
        AndroidStudioStats.GradleBuildProfileSpan record = getRecordForId(recorder.records, 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());

        record = getRecordForId(recorder.records, 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
    }

    @Test
    public void multipleThreadsOrderInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(":projectName", recorder);
        Task secondTask = Mockito.mock(Task.class);
        when(secondTask.getName()).thenReturn("secondTaskName");
        when(secondTask.getProject()).thenReturn(mProject);

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask);

        // second thread finishes
        listener.afterExecute(secondTask, mTaskState);

        // and first thread finishes
        listener.afterExecute(mTask, mTaskState);

        assertEquals(2, recorder.records.size());
        AndroidStudioStats.GradleBuildProfileSpan  record = getRecordForId(recorder.records, 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());

        record = getRecordForId(recorder.records, 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
    }

    private static void ensurePropertyValue(
            Map<String, String> properties, String name, String value) {

        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (property.getKey().equals(name)){
                assertEquals(value, property.getValue());
            }
        }
    }

    @Nullable
    private static AndroidStudioStats.GradleBuildProfileSpan getRecordForId(
            @NonNull List<AndroidStudioStats.GradleBuildProfileSpan> records,
            long recordId) {
        for (AndroidStudioStats.GradleBuildProfileSpan record : records) {
            if (record.getId() == recordId) {
                return record;
            }
        }
        return null;
    }
}
