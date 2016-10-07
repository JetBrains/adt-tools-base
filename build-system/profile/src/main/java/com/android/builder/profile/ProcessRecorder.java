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

import static com.android.builder.profile.MemoryStats.getCurrentProperties;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.stream.JsonWriter;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records all the {@link AndroidStudioStats.GradleBuildProfileSpan}s for a process,
 * in order it was received and sends then synchronously to a {@link JsonRecordWriter}.
 */
public class ProcessRecorder {

    private final AndroidStudioStats.GradleBuildMemorySample mStartMemoryStats;

    private final NameAnonymizer mNameAnonymizer;

    private final AndroidStudioStats.GradleBuildProfile.Builder mBuild;

    private final LoadingCache<String, Project> mProjects;

    @Nullable
    private final ExecutionRecordWriter mExecutionRecordWriter;

    @Nullable
    private String benchmarkName;
    @Nullable
    private String benchmarkMode;

    private static final AtomicLong lastRecordId = new AtomicLong(1);

    static long allocateRecordId() {
        return lastRecordId.incrementAndGet();
    }

    @VisibleForTesting
    static void resetForTests() {
        lastRecordId.set(1);
    }

    @NonNull
    static ProcessRecorder get() {
        return ProcessRecorderFactory.sINSTANCE.get();
    }

    /**
     * Abstraction for a {@link  AndroidStudioStats.GradleBuildProfileSpan } writer.
     */
    public interface ExecutionRecordWriter {
        void write(
                @NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
                @NonNull Map<String, String> attributes) throws IOException;
        void close() throws IOException;
    }

    ProcessRecorder(
            @Nullable ExecutionRecordWriter outWriter,
            @NonNull ILogger iLogger) {
        mExecutionRecordWriter = outWriter;
        mNameAnonymizer = new NameAnonymizer();
        mBuild = AndroidStudioStats.GradleBuildProfile.newBuilder();
        mStartMemoryStats = createAndRecordMemorySample();
        mProjects = CacheBuilder.newBuilder().build(new ProjectCacheLoader(mNameAnonymizer));
    }

    void writeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull final AndroidStudioStats.GradleBuildProfileSpan.Builder executionRecord) {

        executionRecord.setProject(mNameAnonymizer.anonymizeProjectName(project));
        executionRecord.setVariant(mNameAnonymizer.anonymizeVariant(project, variant));

        mBuild.addSpan(executionRecord.build());
    }

    /**
     * Done with the recording processing, finish processing the outstanding
     * {@link AndroidStudioStats.GradleBuildProfileSpan}
     * publication and shutdowns the processing queue.
     *
     * @throws InterruptedException
     */
    void finish() throws InterruptedException {
        AndroidStudioStats.GradleBuildMemorySample memoryStats =
                createAndRecordMemorySample();
        mBuild.setBuildTime(
                memoryStats.getTimestamp() - mStartMemoryStats.getTimestamp());
        mBuild.setGcCount(
                memoryStats.getGcCount() - mStartMemoryStats.getGcCount());
        mBuild.setGcTime(
                memoryStats.getGcTimeMs() - mStartMemoryStats.getGcTimeMs());

        for (Project project : mProjects.asMap().values()) {
            for (AndroidStudioStats.GradleBuildVariant.Builder variant :
                    project.variants.values()) {
                project.properties.addVariant(variant);
            }
            if (project.properties != null) {
                mBuild.addProject(project.properties);
            }
        }

        AndroidStudioStats.AndroidStudioEvent.Builder studioStats =
                AndroidStudioStats.AndroidStudioEvent.newBuilder();
        studioStats.setCategory(
                AndroidStudioStats.AndroidStudioEvent.EventCategory.GRADLE);
        studioStats.setKind(
                AndroidStudioStats.AndroidStudioEvent.EventKind.GRADLE_BUILD_PROFILE);
        studioStats.setGradleBuildProfile(mBuild.build());

        UsageTracker.getInstance().log(studioStats);

        if (mExecutionRecordWriter != null) {
            try {
                writeDebugRecords(mExecutionRecordWriter);
            } catch (IOException e) {
                System.err.println(Throwables.getStackTraceAsString(e));
            }
        }
    }

    private void writeDebugRecords(@NonNull ExecutionRecordWriter writer) throws IOException {

        // Initial metadata
        ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
        properties.put("build_id", UUID.randomUUID().toString());
        properties.put("os_name", mBuild.getOsName());
        properties.put("os_version", mBuild.getOsVersion());
        properties.put("java_version", mBuild.getJavaVersion());
        properties.put("java_vm_version", mBuild.getJavaVmVersion());
        properties.put("max_memory", Long.toString(mBuild.getMaxMemory()));
        if (benchmarkName != null) {
            properties.put("benchmark_name", benchmarkName);
        }
        if (benchmarkMode != null) {
            properties.put("benchmark_mode", benchmarkMode);
        }
        writer.write(
                AndroidStudioStats.GradleBuildProfileSpan.newBuilder()
                        .setId(1)
                        .setStartTimeInMs(mStartMemoryStats.getTimestamp())
                        .setType(
                                AndroidStudioStats.GradleBuildProfileSpan.ExecutionType.INITIAL_METADATA)
                        .build(),
                properties.build());

        //De-anonymize project names
        Map<Long, Pair<String, Map<Long,String>>> originalNames =
                mNameAnonymizer.createDeanonymizer();

        // Spans
        for (AndroidStudioStats.GradleBuildProfileSpan span : mBuild.getSpanList()) {
            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
            if (span.getProject() != 0) {
                Pair<String, Map<Long,String>> project = originalNames.get(span.getProject());
                if (project != null) {
                    builder.put("project", project.getFirst());
                    if (span.getVariant() != 0) {
                        String variant = project.getSecond().get(span.getVariant());
                        if (variant != null) {
                            builder.put("variant", variant);
                        }
                    }
                }
            }

            writer.write(span, builder.build());
        }

        // Final metadata
        writer.write(
                AndroidStudioStats.GradleBuildProfileSpan.newBuilder()
                        .setId(ThreadRecorder.get().allocationRecordId())
                        .setStartTimeInMs(mStartMemoryStats.getTimestamp())
                        .setType(
                                AndroidStudioStats.GradleBuildProfileSpan.ExecutionType.FINAL_METADATA)
                        .build(),
                ImmutableMap.of(
                        "build_time", Long.toString(mBuild.getBuildTime()),
                        "gc_count", Long.toString(mBuild.getGcCount()),
                        "gc_time", Long.toString(mBuild.getGcTime())));

    }

    /**
     * Properties and statistics global to this build invocation.
     */
    @NonNull
    public static AndroidStudioStats.GradleBuildProfile.Builder getGlobalProperties() {
        return get().getProperties();
    }

    @NonNull
    AndroidStudioStats.GradleBuildProfile.Builder getProperties() {
        return mBuild;
    }

    @NonNull
    public static AndroidStudioStats.GradleBuildProject.Builder getProject(
            @NonNull String projectPath) {
        return get().mProjects.getUnchecked(projectPath).properties;
    }

    public static AndroidStudioStats.GradleBuildVariant.Builder addVariant(
            @NonNull String projectPath,
            @NonNull String variantName) {
        AndroidStudioStats.GradleBuildVariant.Builder properties =
                AndroidStudioStats.GradleBuildVariant.newBuilder();
        get().addVariant(projectPath, variantName, properties);
        return properties;
    }

    private void addVariant(@NonNull String projectPath, @NonNull String variantName,
            @NonNull AndroidStudioStats.GradleBuildVariant.Builder properties) {
        Project project = mProjects.getUnchecked(projectPath);
        properties.setId(mNameAnonymizer.anonymizeVariant(projectPath, variantName));
        project.variants.put(variantName, properties);
    }

    public static void setBenchmark(
            @NonNull String benchmarkName,
            @NonNull String benchmarkMode) {
        ProcessRecorder recorder = get();
        recorder.benchmarkName = benchmarkName;
        recorder.benchmarkMode = benchmarkMode;
    }

    private AndroidStudioStats.GradleBuildMemorySample createAndRecordMemorySample() {
        AndroidStudioStats.GradleBuildMemorySample stats = getCurrentProperties();
        if (stats != null) {
            mBuild.addMemorySample(stats);
        }
        return stats;
    }

    public static void recordMemorySample() {
        get().createAndRecordMemorySample();
    }

    private static class ProjectCacheLoader extends CacheLoader<String, Project> {

        @NonNull
        private final NameAnonymizer mNameAnonymizer;

        ProjectCacheLoader(@NonNull NameAnonymizer nameAnonymizer) {
            mNameAnonymizer = nameAnonymizer;
        }

        @Override
        public Project load(@NonNull String name) throws Exception {
            return new Project(mNameAnonymizer.anonymizeProjectName(name));
        }
    }

    private static class Project {
        Project(long id) {
            properties = AndroidStudioStats.GradleBuildProject.newBuilder();
            properties.setId(id);
        }

        final Map<String, AndroidStudioStats.GradleBuildVariant.Builder> variants =
                Maps.newConcurrentMap();
        final AndroidStudioStats.GradleBuildProject.Builder properties;
    }

    /**
     * Implementation of {@link ExecutionRecordWriter} that persist in json format.
     */
    static class JsonRecordWriter implements ExecutionRecordWriter {

        @NonNull
        private final Writer writer;

        @NonNull
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public JsonRecordWriter(@NonNull Writer writer) {
            this.writer = writer;
        }

        @Override
        public void write(
                @NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
                @NonNull Map<String, String> attributes)
                throws IOException {

            if (closed.get()) {
                return;
            }
            // We want to keep the underlying stream open.
            //noinspection IOResourceOpenedButNotSafelyClosed
            JsonWriter mJsonWriter = new JsonWriter(writer);
            mJsonWriter.beginObject();
            {
                mJsonWriter.name("id").value(executionRecord.getId());
                mJsonWriter.name("parentId").value(executionRecord.getParentId());
                mJsonWriter.name("startTimeInMs").value(executionRecord.getStartTimeInMs());
                mJsonWriter.name("durationInMs").value(executionRecord.getDurationInMs());
                String type = executionRecord.getType().toString();
                if (executionRecord.hasTask()) {
                    type = type + "_" + executionRecord.getTask().getType().toString();
                } else if (executionRecord.hasTransform()) {
                    type = type + "_" + executionRecord.getTransform().getType().toString();
                }
                mJsonWriter.name("type").value(type);
                mJsonWriter.name("attributes");
                mJsonWriter.beginArray();
                {
                    for (Map.Entry<String, String> entry: attributes.entrySet()) {
                        mJsonWriter.beginObject();
                        {
                            mJsonWriter.name("name").value(entry.getKey());
                            mJsonWriter.name("value").value(entry.getValue());
                        }
                        mJsonWriter.endObject();
                    }
                }
                mJsonWriter.endArray();
            }
            mJsonWriter.endObject();
            mJsonWriter.flush();

            writer.append("\n");
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (closed.get()) {
                    return;
                }
                closed.set(true);
            }
            writer.flush();
            writer.close();
        }
    }
}
