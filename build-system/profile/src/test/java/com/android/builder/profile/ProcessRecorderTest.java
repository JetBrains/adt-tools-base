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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for the {@link ProcessRecorder} class
 */
public class ProcessRecorderTest {

    @Before
    public void setUp() {
        // reset for each test.
        ProcessRecorderFactory.setEnabled(true);
        ProcessRecorderFactory.sINSTANCE = new ProcessRecorderFactory();
    }

    @After
    public void shutdown() throws InterruptedException {
        ProcessRecorderFactory.shutdown();
    }

    @Test
    public void testBasicRecord() throws InterruptedException {
        StringWriter stringWriter = new StringWriter();
        ProcessRecorder.JsonRecordWriter jsonRecordWriter =
                new ProcessRecorder.JsonRecordWriter(stringWriter);
        ProcessRecorderFactory.initializeForTests(jsonRecordWriter);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return 10;
                    }
                });
        ProcessRecorder.get().finish();
        String jsonText = stringWriter.toString();
        assertNotNull(jsonText);
        Assert.assertFalse(jsonText.isEmpty());
        assertTrue(jsonText.contains("id"));
        assertTrue(jsonText.contains("parentId"));
        assertTrue(jsonText.contains("startTimeInMs"));
        assertTrue(jsonText.contains("\"type\":\"SOME_RANDOM_PROCESSING\""));
    }

    @Test
    public void testRecordWithAttributes() throws InterruptedException {
        StringWriter stringWriter = new StringWriter();
        ProcessRecorder.JsonRecordWriter jsonRecordWriter =
                new ProcessRecorder.JsonRecordWriter(stringWriter);
        ProcessRecorderFactory.initializeForTests(jsonRecordWriter);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", "foo", new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return 10;
                    }
                });
        ProcessRecorder.get().finish();
        String jsonText = stringWriter.toString();
        assertTrue(jsonText.contains("{\"name\":\"variant\",\"value\":\"foo\"}]"));
    }

    @Test
    public void testRecordsOrder() throws InterruptedException {
        final List<AndroidStudioStats.GradleBuildProfileSpan> records = new ArrayList<>();
        ProcessRecorder.ExecutionRecordWriter recorderWriter =
                new ProcessRecorder.ExecutionRecordWriter() {
                    @Override
                    public void write(
                            @NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
                            @NonNull Map<String, String> properties)
                            throws IOException {
                        records.add(executionRecord);
                    }

                    @Override
                    public void close() throws IOException {
                    }
                };

        ProcessRecorderFactory.initializeForTests(recorderWriter);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                "projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                                "projectName", null, new Recorder.Block<Integer>() {
                                    @Override
                                    public Integer call() throws Exception {
                                        return 10;
                                    }
                                });
                    }
                });

        ProcessRecorder.get().finish();
        sortExecutionRecords(records);
        // delete the metadata records.
        assertEquals(ExecutionType.INITIAL_METADATA, records.remove(0).getType());
        assertEquals(ExecutionType.FINAL_METADATA,
                records.remove(records.size() - 1).getType());
        assertEquals(2, records.size());
        assertTrue(records.get(1).getParentId() == records.get(0).getId());
    }

    @Test
    public void testMultipleSpans() throws InterruptedException {
        final List<AndroidStudioStats.GradleBuildProfileSpan> records = new ArrayList<>();
        ProcessRecorder.ExecutionRecordWriter recorderWriter =
                new ProcessRecorder.ExecutionRecordWriter() {
                    @Override
                    public void write(
                            @NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
                            @NonNull Map<String, String> attributes) throws IOException {
                        records.add(executionRecord);
                    }

                    @Override
                    public void close() throws IOException {
                    }
                };
        ProcessRecorderFactory.initializeForTests(recorderWriter);

        Integer value = ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                                ":projectName", null, new Recorder.Block<Integer>() {
                                    @Override
                                    public Integer call() throws Exception {
                                        Integer first = ThreadRecorder.get().record(
                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                ":projectName", null, new Recorder.Block<Integer>() {
                                                    @Override
                                                    public Integer call() throws Exception {
                                                        return 1;
                                                    }
                                                });
                                        Integer second = ThreadRecorder.get().record(
                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                ":projectName", null, new Recorder.Block<Integer>() {
                                                    @Override
                                                    public Integer call() throws Exception {
                                                        return 3;
                                                    }
                                                });
                                        Integer third = ThreadRecorder.get().record(
                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                ":projectName", null, new Recorder.Block<Integer>() {
                                                    @Override
                                                    public Integer call() throws Exception {
                                                        Integer value = ThreadRecorder.get().record(
                                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                                ":projectName", null,
                                                                new Recorder.Block<Integer>() {
                                                                    @Override
                                                                    public Integer call()
                                                                            throws Exception {
                                                                        return 7;
                                                                    }
                                                                });
                                                        assertNotNull(value);
                                                        return 5 + value;
                                                    }
                                                });
                                        assertNotNull(first);
                                        assertNotNull(second);
                                        assertNotNull(third);
                                        return first + second + third;
                                    }
                                });
                    }
                });

        assertNotNull(value);
        assertEquals(16, value.intValue());
        ProcessRecorder.get().finish();
        // delete the metadata records.
        assertEquals(ExecutionType.INITIAL_METADATA, records.remove(0).getType());
        assertEquals(ExecutionType.FINAL_METADATA, records.remove(records.size() - 1).getType());
        assertEquals(6, records.size());
        // re-order by event id.
        sortExecutionRecords(records);
        assertEquals(records.get(0).getId(), records.get(1).getParentId());
        assertEquals(records.get(1).getId(), records.get(2).getParentId());
        assertEquals(records.get(1).getId(), records.get(3).getParentId());
        assertEquals(records.get(1).getId(), records.get(4).getParentId());
        assertEquals(records.get(4).getId(), records.get(5).getParentId());

        assertTrue(
                records.get(1).getDurationInMs() >=
                        records.get(2).getDurationInMs()
                                + records.get(3).getDurationInMs()
                                + records.get(4).getDurationInMs());

        assertTrue(records.get(4).getDurationInMs() >= records.get(5).getDurationInMs());
    }

    private static void sortExecutionRecords(
            @NonNull List<AndroidStudioStats.GradleBuildProfileSpan> records) {
        Collections.sort(records, (o1, o2) -> new Long(o1.getId()).compareTo(o2.getId()));
    }
}
