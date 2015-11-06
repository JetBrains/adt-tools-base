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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.ide.common.util.ReferenceHolder;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A task running a transform.
 */
@ParallelizableTask
public class TransformTask extends StreamBasedTask implements Context {

    private Transform transform;

    public Transform getTransform() {
        return transform;
    }

    @InputFiles
    public Collection<File> getOtherFileInputs() {
        return transform.getSecondaryFileInputs();
    }

    @OutputFiles
    public Collection<File> getOtherFileOutputs() {
        return transform.getSecondaryFileOutputs();
    }

    @OutputDirectories
    public Collection<File> getOtherFolderOutputs() {
        return transform.getSecondaryDirectoryOutputs();
    }

    @Input
    Map<String, Object> getOtherInputs() {
        return transform.getParameterInputs();
    }

    @TaskAction
    void transform(final IncrementalTaskInputs incrementalTaskInputs)
            throws IOException, TransformException, InterruptedException {

        final ReferenceHolder<List<TransformInput>> consumedInputs = ReferenceHolder.empty();
        final ReferenceHolder<List<TransformInput>> referencedInputs = ReferenceHolder.empty();
        final ReferenceHolder<Boolean> isIncremental = ReferenceHolder.empty();

        ThreadRecorder.get().record(ExecutionType.TASK_TRANSFORM_PREPARATION,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {

                        isIncremental.setValue( transform.isIncremental() &&
                                incrementalTaskInputs.isIncremental());

                        Map<File, Status> changedMap = Maps.newHashMap();
                        Set<File> removedFiles = Sets.newHashSet();
                        if (isIncremental.getValue()) {
                            // gather the changed files first.
                            gatherChangedFiles(incrementalTaskInputs, changedMap, removedFiles);

                            // and check against secondary files, which disables
                            // incremental mode.
                            isIncremental.setValue(checkSecondaryFiles(changedMap, removedFiles));
                        }

                        if (isIncremental.getValue()) {
                            // ok create temporary incremental data
                            List<IncrementalTransformInput> incInputs
                                    = createIncrementalInputs(consumedInputStreams);
                            List<IncrementalTransformInput> incReferencedInputs
                                    = createIncrementalInputs(referencedInputStreams);

                            // then compare to changed list and create final Inputs
                            if (isIncremental.setValue(updateIncrementalInputsWithChangedFiles(
                                    incInputs,
                                    incReferencedInputs,
                                    changedMap,
                                    removedFiles))) {
                                consumedInputs.setValue(convertToImmutable(incInputs));
                                referencedInputs.setValue(convertToImmutable(incReferencedInputs));
                            }
                        }

                        // at this point if we do not have incremental mode, got with
                        // default TransformInput with no inc data.
                        if (!isIncremental.getValue()) {
                            consumedInputs.setValue(
                                    computeNonIncTransformInput(consumedInputStreams));
                            referencedInputs.setValue(
                                    computeNonIncTransformInput(referencedInputStreams));
                        }

                        return null;
                    }
                },
                new Recorder.Property("project", getProject().getName()),
                new Recorder.Property("transform", transform.getName()),
                new Recorder.Property("incremental", Boolean.toString(transform.isIncremental())));

        ThreadRecorder.get().record(ExecutionType.TASK_TRANSFORM,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        transform.transform(
                                TransformTask.this,
                                consumedInputs.getValue(),
                                referencedInputs.getValue(),
                                outputStream != null ? outputStream.asOutput() : null,
                                isIncremental.getValue());
                        return null;
                    }
                },
                new Recorder.Property("project", getProject().getName()),
                new Recorder.Property("transform", transform.getName()),
                new Recorder.Property("incremental", Boolean.toString(transform.isIncremental())));
    }

    /**
     * Returns a list of non incremental TransformInput.
     * @param streams the streams.
     * @return a list of non-incremental TransformInput matching the content of the streams.
     */
    @NonNull
    private static List<TransformInput> computeNonIncTransformInput(
            @NonNull Collection<TransformStream> streams) {
        List<TransformInput> inputs = Lists.newArrayListWithCapacity(streams.size());
        for (TransformStream stream : streams) {
            inputs.add(stream.asNonIncrementalInput());
        }

        return inputs;
    }

    /**
     * Returns a list of IncrementalTransformInput for all the inputs.
     */
    @NonNull
    private static List<IncrementalTransformInput> createIncrementalInputs(
            @NonNull Collection<TransformStream> streams) {
        List<IncrementalTransformInput> list = Lists.newArrayListWithCapacity(streams.size());

        for (TransformStream stream : streams) {
            list.add(stream.asIncrementalInput());
        }

        return list;
    }

    private static void gatherChangedFiles(
            @NonNull IncrementalTaskInputs incrementalTaskInputs,
            @NonNull final Map<File, Status> changedFileMap,
            @NonNull final Set<File> removedFiles) {
        incrementalTaskInputs.outOfDate(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                if (inputFileDetails.isAdded()) {
                    changedFileMap.put(inputFileDetails.getFile(), Status.ADDED);
                } else if (inputFileDetails.isModified()) {
                    changedFileMap.put(inputFileDetails.getFile(), Status.CHANGED);
                }
            }
        });

        incrementalTaskInputs.removed(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                removedFiles.add(inputFileDetails.getFile());
            }
        });
    }

    private boolean checkSecondaryFiles(
            @NonNull Map<File, Status> changedMap,
            @NonNull Set<File> removedFiles) {
        for (File file : transform.getSecondaryFileInputs()) {
            if (changedMap.containsKey(file) || removedFiles.contains(file)) {
                return false;
            }
        }

        return true;
    }

    private static boolean updateIncrementalInputsWithChangedFiles(
            @NonNull List<IncrementalTransformInput> consumedInputs,
            @NonNull List<IncrementalTransformInput> referencedInputs,
            @NonNull Map<File, Status> changedFilesMap,
            @NonNull Set<File> removedFiles) {

        // we're going to concat both list multiple times, and the Iterators API ultimately put
        // all the iterators to concat in a list. So let's reuse a list.
        List<Iterator<IncrementalTransformInput>> iterators = Lists.newArrayListWithCapacity(2);

        Splitter splitter = Splitter.on(File.separatorChar);

        // start with the removed files as they carry the risk of removing incremental mode.
        // If we detect such a case, we stop immediately.
        for (File removedFile : removedFiles) {
            List<String> removedFileSegments = Lists.newArrayList(
                    splitter.split(removedFile.getAbsolutePath()));

            Iterator<IncrementalTransformInput> iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);

            boolean found = false;
            while (iterator.hasNext()) {
                IncrementalTransformInput next = iterator.next();
                if (next.checkRemovedJarFile(removedFile, removedFileSegments) ||
                        next.checkRemovedFolderFile(removedFile, removedFileSegments)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                // this deleted file breaks incremental because we cannot figure out where it's
                // coming from and what types/scopes is associated with it.
                return false;
            }
        }

        // now handle the added/changed files.

        for (Map.Entry<File, Status> entry : changedFilesMap.entrySet()) {
            File changedFile = entry.getKey();
            Status changedStatus = entry.getValue();

            // first go through the jars first as it's a faster check.
            Iterator<IncrementalTransformInput> iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);
            boolean found = false;
            while (iterator.hasNext()) {
                if (iterator.next().checkForJar(changedFile, changedStatus)) {
                    // we can skip to the next changed file.
                    found = true;
                    break;
                }
            }

            if (found) {
                continue;
            }

            // now go through the folders. First get a segment list for the path.
            iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);
            List<String> changedSegments = Lists.newArrayList(
                    splitter.split(changedFile.getAbsolutePath()));

            while (iterator.hasNext()) {
                if (iterator.next().checkForFolder(changedFile, changedSegments, changedStatus)) {
                    // we can skip to the next changed file.
                    break;
                }
            }
        }

        return true;
    }

    @NonNull
    private static Iterator<IncrementalTransformInput> getConcatIterator(
            @NonNull List<IncrementalTransformInput> consumedInputs,
            @NonNull List<IncrementalTransformInput> referencedInputs,
            List<Iterator<IncrementalTransformInput>> iterators) {
        iterators.clear();
        iterators.add(consumedInputs.iterator());
        iterators.add(referencedInputs.iterator());
        return Iterators.concat(iterators.iterator());
    }

    @NonNull
    private static List<TransformInput> convertToImmutable(
            @NonNull List<IncrementalTransformInput> inputs) {
        List<TransformInput> immutableInputs = Lists.newArrayListWithCapacity(inputs.size());
        for (IncrementalTransformInput input : inputs) {
            immutableInputs.add(input.asImmutable());
        }

        return immutableInputs;
    }

    public  interface  ConfigActionCallback<T extends Transform> {
        void callback(@NonNull T transform, @NonNull TransformTask task);
    }

    public static class ConfigAction<T extends Transform> implements TaskConfigAction<TransformTask> {

        @NonNull
        private final String variantName;
        @NonNull
        private final String taskName;
        @NonNull
        private final T transform;
        @NonNull
        private Collection<TransformStream> consumedInputStreams;
        @NonNull
        private Collection<TransformStream> referencedInputStreams;
        @Nullable
        private IntermediateStream outputStream;
        @Nullable
        private final ConfigActionCallback<T> configActionCallback;

        ConfigAction(
                @NonNull String variantName,
                @NonNull String taskName,
                @NonNull T transform,
                @NonNull Collection<TransformStream> consumedInputStreams,
                @NonNull Collection<TransformStream> referencedInputStreams,
                @Nullable IntermediateStream outputStream,
                @Nullable ConfigActionCallback<T> configActionCallback) {
            this.variantName = variantName;
            this.taskName = taskName;
            this.transform = transform;
            this.consumedInputStreams = consumedInputStreams;
            this.referencedInputStreams = referencedInputStreams;
            this.outputStream = outputStream;
            this.configActionCallback = configActionCallback;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<TransformTask> getType() {
            return TransformTask.class;
        }

        @Override
        public void execute(@NonNull TransformTask task) {
            task.transform = transform;
            task.consumedInputStreams = consumedInputStreams;
            task.referencedInputStreams = referencedInputStreams;
            task.outputStream = outputStream;
            task.setVariantName(variantName);

            if (configActionCallback != null) {
                configActionCallback.callback(transform, task);
            }
        }
    }
}
