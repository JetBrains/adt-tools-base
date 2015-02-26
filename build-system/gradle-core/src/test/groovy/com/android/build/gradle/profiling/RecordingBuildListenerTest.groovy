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

package com.android.build.gradle.profiling

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.builder.profile.ExecutionRecord
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.ProcessRecorder
import com.android.builder.profile.ProcessRecorderFactory
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskState
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

import java.util.logging.Logger

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

import static org.junit.Assert.assertNotNull
import static org.mockito.Mockito.when

/**
 * Tests for {@link RecordingBuildListener}
 */
class RecordingBuildListenerTest {

    @Mock
    Task mTask

    @Mock
    TaskState mTaskState

    @Mock
    Project mProject

    private static final class TestRecorder implements Recorder {

        final AtomicLong recordId = new AtomicLong(0);
        final List<ExecutionRecord> records = new CopyOnWriteArrayList<>();

        @Override
        def <T> T record(@NonNull ExecutionType executionType, @NonNull Recorder.Block<T> block,
                Recorder.Property... properties) {
            throw new UnsupportedOperationException("record method was not supposed to be called.")
        }

        @Override
        long allocationRecordId() {
            return recordId.incrementAndGet()
        }

        @Override
        void closeRecord(ExecutionRecord record) {
            records.add(record);
        }
    }

    private static final class TestExecutionRecordWriter implements ProcessRecorder.ExecutionRecordWriter {

        final List<ExecutionRecord> records = new CopyOnWriteArrayList<>();

        @Override
        void write(@NonNull ExecutionRecord executionRecord) throws IOException {
            records.add(executionRecord)
        }

        @Override
        void close() throws IOException {
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mTask.getName()).thenReturn("taskName")
        when(mTask.getProject()).thenReturn(mProject)
        when(mProject.getName()).thenReturn("projectName")
    }

    @Test
    public void "single thread invocation"() {
        def recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder);

        listener.beforeExecute(mTask);
        listener.afterExecute(mTask, mTaskState)
        assertEquals(1, recorder.records.size())
        ExecutionRecord record = recorder.records.get(0)
        assertEquals(1, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "taskName")
        ensurePropertyValue(record.attributes, "project", "projectName")
    }

    @Test
    public void "single thread with multiple spans invocation"() {

        TestExecutionRecordWriter recordWriter = new TestExecutionRecordWriter()
        ProcessRecorderFactory.sINSTANCE = new ProcessRecorderFactory();
        ProcessRecorderFactory.setEnabled(true)
        ProcessRecorderFactory.sINSTANCE.setRecordWriter(
                recordWriter);

        RecordingBuildListener listener = new RecordingBuildListener(ThreadRecorder.get());

        listener.beforeExecute(mTask);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING) {
            Logger.getAnonymousLogger().finest("useless block")
        }
        listener.afterExecute(mTask, mTaskState)

        assertEquals(2, recordWriter.records.size())
        ExecutionRecord record = getRecordForId(recordWriter.records, 1)
        assertEquals(1, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "taskName")
        ensurePropertyValue(record.attributes, "project", "projectName")

        record = getRecordForId(recordWriter.records, 2)
        assertNotNull(record);
        assertEquals(2, record.id)
        assertEquals(1, record.parentId)
        assertEquals(0, record.attributes.size())
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.type)
    }

    @Test
    public void "multiple threads invocation"() {
        def recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder)
        Task secondTask = Mockito.mock(Task.class)
        when(secondTask.getName()).thenReturn("secondTaskName")
        when(secondTask.getProject()).thenReturn(mProject)

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask)

        // first thread finishes
        listener.afterExecute(mTask, mTaskState)

        // and second thread finishes
        listener.afterExecute(secondTask, mTaskState)

        assertEquals(2, recorder.records.size())
        ExecutionRecord record = getRecordForId(recorder.records, 1)
        assertEquals(1, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "taskName")
        ensurePropertyValue(record.attributes, "project", "projectName")

        record = getRecordForId(recorder.records, 2)
        assertEquals(2, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "secondTaskName")
        ensurePropertyValue(record.attributes, "project", "projectName")
    }

    @Test
    public void "multiple threads order invocation"() {
        def recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder)
        Task secondTask = Mockito.mock(Task.class)
        when(secondTask.getName()).thenReturn("secondTaskName")
        when(secondTask.getProject()).thenReturn(mProject)

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask)

        // second thread finishes
        listener.afterExecute(secondTask, mTaskState)

        // and first thread finishes
        listener.afterExecute(mTask, mTaskState)

        assertEquals(2, recorder.records.size())
        ExecutionRecord record = getRecordForId(recorder.records, 1)
        assertEquals(1, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "taskName")
        ensurePropertyValue(record.attributes, "project", "projectName")

        record = getRecordForId(recorder.records, 2)
        assertEquals(2, record.id)
        assertEquals(0, record.parentId)
        assertEquals(2, record.attributes.size())
        ensurePropertyValue(record.attributes, "task", "secondTaskName")
        ensurePropertyValue(record.attributes, "project", "projectName")
    }

    private static void ensurePropertyValue(
            List<Recorder.Property> properties, String name, String value) {

        for (Recorder.Property property : properties) {
            if (property.getName().equals(name)){
                assertEquals(value, property.getValue())
            }
        }
    }

    @Nullable
    private static ExecutionRecord getRecordForId(List<ExecutionRecord> records, long recordId) {
        for (ExecutionRecord record : records) {
            if (record.id == recordId) {
                return record
            }
        }
        return null;
    }
}
