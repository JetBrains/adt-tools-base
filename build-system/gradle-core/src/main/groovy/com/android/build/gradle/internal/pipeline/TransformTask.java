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

import static com.android.build.transform.api.TransformInput.FileStatus.ADDED;
import static com.android.build.transform.api.TransformInput.FileStatus.CHANGED;
import static com.android.build.transform.api.TransformInput.FileStatus.REMOVED;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.NoOpTransform;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
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
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task running a transform.
 */
@ParallelizableTask
public class TransformTask extends StreamBasedTask implements Context {

    private Transform transform;

    public Transform getTransform() {
        return transform;
    }

    @TaskAction
    void transform(IncrementalTaskInputs incrementalTaskInputs)
            throws IOException, TransformException, InterruptedException {
        Map<TransformInput, Object> consumedInputs;
        List<TransformInput> referencedInputs;
        boolean isIncremental = transform.isIncremental() && incrementalTaskInputs.isIncremental();

        if (isIncremental) {
            // map from streams to changed files.
            ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles = ArrayListMultimap.create();

            if (consumedInputStreams.size() + referencedInputStreams.size() > 1) {
                isIncremental = handleMultiStreams(incrementalTaskInputs, streamToChangedFiles);
            } else {
                isIncremental = handleSingleStream(incrementalTaskInputs, streamToChangedFiles);
            }

            if (!isIncremental) {
                streamToChangedFiles = null;
            }

            consumedInputs = createConsumedTransformInputs(streamToChangedFiles);
            referencedInputs = createReferencedTransformInputs(streamToChangedFiles);

        } else {
            consumedInputs = createConsumedTransformInputs(null);
            referencedInputs = createReferencedTransformInputs(null);
        }

        if (transform instanceof AsInputTransform) {
            //noinspection unchecked
            ((AsInputTransform) transform).transform(
                    this,
                    (Map<TransformInput, TransformOutput>) (Map<?,?>) consumedInputs,
                    referencedInputs,
                    isIncremental);

        } else if (transform instanceof CombinedTransform) {
            ((CombinedTransform) transform).transform(
                    this,
                    consumedInputs.keySet(),
                    referencedInputs,
                    Iterables.getOnlyElement(outputStreams).asOutput(),
                    isIncremental);

        } else if (transform instanceof ForkTransform) {
            //noinspection unchecked
            ((ForkTransform) transform).transform(
                    this,
                    (Map<TransformInput, Collection<TransformOutput>>) (Map<?,?>) consumedInputs,
                    referencedInputs,
                    isIncremental);

        } else if (transform instanceof NoOpTransform) {
            ((NoOpTransform) transform).transform(
                    this, consumedInputs.keySet(), referencedInputs, isIncremental);

        } else {
            throw new UnsupportedOperationException("Unknown transform type: " +
                    transform.getClass().getName());
        }
    }

    /**
     * In case of single-streams input, just gather all the changed files, and tied them up to the
     * single stream.
     *
     * @param incrementalTaskInputs the task inputs as provided by Gradle
     * @param streamToChangedFiles the map to fill with the file->stream ties.
     * @return whether the build can be incremental
     */
    private boolean handleSingleStream(
            @NonNull final IncrementalTaskInputs incrementalTaskInputs,
            @NonNull final ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles) {
        final AtomicBoolean isIncremental = new AtomicBoolean(true);

        // since there's a single stream, it has to be a consumed input
        final TransformStream stream = Iterables.getOnlyElement(consumedInputStreams);

        final Set<File> secondaryFiles = Sets.newHashSet(transform.getSecondaryFileInputs());

        incrementalTaskInputs.outOfDate(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                // check if we're not already not-incremental, in which case nothing needs to be
                // done.
                if (isIncremental.get()) {
                    if (secondaryFiles.contains(inputFileDetails.getFile())) {
                        isIncremental.set(false);
                    } else {
                        streamToChangedFiles.put(stream, inputFileDetails);
                    }
                }
            }
        });

        // can we abort early due to non-incremental mode detected?
        if (!isIncremental.get()) {
            return false;
        }

        incrementalTaskInputs.removed(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                // check if we're not already not-incremental, in which case nothing needs to be
                // done.
                if (isIncremental.get()) {
                    if (secondaryFiles.contains(inputFileDetails.getFile())) {
                        isIncremental.set(false);
                    } else {
                        streamToChangedFiles.put(stream, inputFileDetails);
                    }
                }
            }
        });

        return isIncremental.get();
    }

    /**
     * In case of multi-streams input, tie each file changed to the stream it belongs to.
     * @param incrementalTaskInputs the task inputs as provided by Gradle
     * @param streamToChangedFiles the map to fill with the file->stream ties.
     * @return whether the build can be incremental
     */
    private boolean handleMultiStreams(
            @NonNull IncrementalTaskInputs incrementalTaskInputs,
            @NonNull final ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles) {
        final AtomicBoolean isIncremental = new AtomicBoolean(true);
        AtomicBoolean hasFolderSourceBool = new AtomicBoolean();
        // map from files to streams. This is from input files to stream to more quickly
        // resolve them.
        final Map<File, TransformStream> sourceToStreamMap = computeSourceToStreamMap(
                hasFolderSourceBool);
        final boolean hasFolderSource = hasFolderSourceBool.get();

        incrementalTaskInputs.outOfDate(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                // check if we're not already not-incremental, in which case nothing needs to be
                // done.
                if (isIncremental.get()) {
                    isIncremental.set(findMatchingStreamforInputFile(
                            inputFileDetails,
                            sourceToStreamMap,
                            hasFolderSource,
                            streamToChangedFiles));
                }
            }
        });

        // can we abort early due to non-incremental mode detected?
        if (!isIncremental.get()) {
            return false;
        }

        incrementalTaskInputs.removed(new org.gradle.api.Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                // check if we're not already not-incremental, in which case nothing needs to be
                // done.
                if (isIncremental.get()) {
                    isIncremental.set(findMatchingStreamforInputFile(
                            inputFileDetails,
                            sourceToStreamMap,
                            hasFolderSource,
                            streamToChangedFiles));
                }
            }
        });

        return isIncremental.get();
    }

    /**
     * Finds the stream owning a given changed input file.
     *
     * In order to create {@link TransformInput} where {@link TransformInput#getChangedFiles()}
     * returns the right thing, we have to search which stream owns a given changed file, as
     * provided by Gradle.
     *
     * This looks for a stream who's source file/folders is or contains the provided file.
     *
     * If any changed file is found to not belong to any stream, this means the changed file
     * is a transform's secondary input file, and the transform will not be incremental anymore.
     * This state is returned through the return value.
     *
     * @param inputFileDetails the changed file data, as provided by Gradle
     * @param sourceToStreamMap a map from each source file/folder to their parent stream.
     * @param hasFolderSource whether any stream has directory inputs.
     * @param streamToChangedFiles the return list multimap of stream->changed files
     */
    private static boolean findMatchingStreamforInputFile(
            @NonNull InputFileDetails inputFileDetails,
            @NonNull Map<File, TransformStream> sourceToStreamMap,
            boolean hasFolderSource,
            @NonNull ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles) {
        File file = inputFileDetails.getFile();

        // check if the file is a direct source.
        TransformStream stream = sourceToStreamMap.get(file);
        if (stream != null) {
            streamToChangedFiles.put(stream, inputFileDetails);
        } else if (hasFolderSource) {
            // if the file is not a direct input and we have folder, search
            // if the file belong to one of the input file.
            String path = file.getPath();
            // if we have source folder, check which stream owns this file
            TransformStream match = null;
            for (Entry<File, TransformStream> entry : sourceToStreamMap.entrySet()) {
                if (path.startsWith(entry.getKey().getPath())) {
                    match = entry.getValue();
                    break;
                }
            }

            if (match != null) {
                streamToChangedFiles.put(match, inputFileDetails);
            } else {
                // looks like this input is coming from a secondary file.
                // we need to stop resolving inputs and do a non incremental run
                return false;
            }
        } else {
            // looks like this input is coming from a secondary file.
            // we need to stop resolving inputs and do a non incremental run
            return false;
        }

        return true;
    }

    /**
     * Computes a map from source files/folders to streams.
     *
     * Since each stream can contain multiple files or folders as inputs, we gather a list
     * of all possible input files/folders and what stream they belong too.
     *
     * We also returns a boolean, as the <var>hasFolderSourceBool</var> if any input files
     * is a directory.
     *
     * @param hasFolderSourceBool a return boolean value indicating that one input is a directory.
     * @return a map of files to streams.
     */
    @NonNull
    private Map<File, TransformStream> computeSourceToStreamMap(
            @NonNull AtomicBoolean hasFolderSourceBool) {
        boolean hasFolder = false;
        Map<File, TransformStream> map = Maps.newHashMap();
        for (TransformStream stream : consumedInputStreams) {
            for (File file : stream.getFiles().get()) {
                if (map.containsKey(file)) {
                    throw new RuntimeException("Multiple streams with input: " + file);
                }

                if (!hasFolder && file.isDirectory()) {
                    hasFolder = true;
                }

                map.put(file, stream);
            }
        }

        for (TransformStream stream : referencedInputStreams) {
            for (File file : stream.getFiles().get()) {
                if (map.containsKey(file)) {
                    throw new RuntimeException("Multiple streams with input: " + file);
                }

                if (!hasFolder && file.isDirectory()) {
                    hasFolder = true;
                }

                map.put(file, stream);
            }
        }

        hasFolderSourceBool.set(hasFolder);

        return map;
    }

    /**
     * Creates the TransformInput from the TransformStream instances.
     *
     * This only applies to the consumed inputs.
     *
     * @param streamToChangedFiles optional map of stream to changed files.
     * @return the list of transform inputs.
     */
    @NonNull
    private Map<TransformInput, Object> createConsumedTransformInputs(
            @Nullable ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles) {
        boolean inputOutput = transform instanceof AsInputTransform;
        boolean multiOutput = transform instanceof ForkTransform;
        inputOutput |= multiOutput;

        Map<TransformInput, Object> results = Maps.newHashMap();

        for (TransformStream input : consumedInputStreams) {
            if (input.getFormat() == Format.MULTI_FOLDER) {
                // in this case, we'll create a TransformInput for each sub-stream.
                if (multiOutput) {
                    results.putAll(handleMultiFoldersWithMultiOutputs(input,
                            streamToChangedFiles == null ? null : streamToChangedFiles.get(input)));
                } else if (inputOutput) {
                    results.putAll(handleMultiFoldersWithSingleOutput(input,
                            streamToChangedFiles == null ? null : streamToChangedFiles.get(input)));
                } else {
                    results.putAll(handleMultiFoldersWithNoOutputs(input,
                            streamToChangedFiles == null ? null : streamToChangedFiles.get(input)));
                }

            } else {
                TransformInputImpl.Builder inputBuilder = TransformInputImpl.builder()
                        .from(input);

                if (streamToChangedFiles != null) {
                    List<InputFileDetails> changedFiles = streamToChangedFiles.get(input);
                    if (changedFiles != null) {
                        for (InputFileDetails details : changedFiles) {
                            inputBuilder.addChangedFile(
                                    details.getFile(),
                                    details.isAdded() ? ADDED
                                            : (details.isModified() ? CHANGED : REMOVED));
                        }
                    }
                }

                if (multiOutput) {
                    results.put(inputBuilder.build(), findOutputsFor(input));
                } else if (inputOutput) {
                    results.put(inputBuilder.build(), findOutputFor(input));
                } else {
                    results.put(inputBuilder.build(), null);
                }
            }
        }

        // can't use ImmutableMap due to possible null values.
        // Since the backing map is a local var in this method whose reference
        // is lost after the method return, there's no issue with modifications to the
        // underlying map happening.
        return Collections.unmodifiableMap(results);
    }

    private static Map<? extends TransformInput,?> handleMultiFoldersWithNoOutputs(
            @NonNull TransformStream input,
            @Nullable List<InputFileDetails> inputFileDetails) {
        return handleMultiFolders(input, inputFileDetails, new OutputCreator() {
            @Override
            public Object create(@NonNull String subStreamName) {
                return null;
            }
        });
    }

    private Map<? extends TransformInput,?> handleMultiFoldersWithMultiOutputs(
            @NonNull TransformStream input,
            @Nullable List<InputFileDetails> inputFileDetails) {

        final Collection<TransformStream> matchingOutputStreams = Lists.newArrayListWithExpectedSize(
                transform.getOutputTypes().size());
        for (TransformStream outputStream : outputStreams) {
            if (input == outputStream.getParentStream()) {
                matchingOutputStreams.add(outputStream);
            }
        }

        Preconditions.checkState(!outputStreams.isEmpty(),
                "No matching output for ForkTransform with input: " + input);

        return handleMultiFolders(input, inputFileDetails, new OutputCreator() {
            @Override
            public Object create(@NonNull String subStreamName) {
                List<TransformOutput> outputs = Lists.newArrayListWithCapacity(
                        outputStreams.size());
                for (TransformStream outputStream : matchingOutputStreams) {
                    outputs.add(outputStream.asSubStreamOutput(subStreamName));
                }

                return outputs;
            }
        });
    }

    /**
     * Handles a TransformStream of type {@link Format#MULTI_FOLDER} for a transform of type
     * {@link AsInputTransform}.
     *
     * This looks at all the available sub-streams and create a pair of TransformInput/Output
     * for the stream (With format {@link Format#SINGLE_FOLDER}), and its own changed files.
     *
     * @param input the stream
     * @param inputFileDetails the changed files for this stream.
     * @return a map of input, output.
     */
    private Map<TransformInput, Object> handleMultiFoldersWithSingleOutput(
            @NonNull TransformStream input,
            @Nullable List<InputFileDetails> inputFileDetails) {

        // get the true Stream for the output.
        final TransformStream outputStream = getMatchingOutputStream(input);
        Preconditions.checkNotNull(outputStream,
                "No matching output for AsInputTransform with input: " + input);

        return handleMultiFolders(input, inputFileDetails, new OutputCreator() {
            @Override
            public Object create(@NonNull String subStreamName) {
                return outputStream.asSubStreamOutput(subStreamName);
            }
        });
    }


    /**
     * Returns the matching {@link TransformStream} for an input {@link TransformStream}
     */
    @Nullable
    private TransformStream getMatchingOutputStream(@NonNull TransformStream input) {
        for (TransformStream output : outputStreams) {
            if (input == output.getParentStream()) {
                return output;
            }
        }
        return null;
    }

    interface OutputCreator {
        Object create(@NonNull String subStreamName);
    }

    @NonNull
    private static Map<TransformInput, Object> handleMultiFolders(
            @NonNull TransformStream input,
            @Nullable List<InputFileDetails> inputFileDetails,
            @NonNull OutputCreator creator) {
        Map<TransformInput, Object> results = Maps.newHashMap();

        // first gather all the substream folders.
        List<File> subStreams = Lists.newArrayList();

        for (File substreamRoot : input.getFiles().get()) {
            File[] files = substreamRoot.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isDirectory();
                }
            });

            if (files != null) {
                subStreams.addAll(Arrays.asList(files));
            }
        }

        // now look at the changed files, and map it to the subStreams.
        ListMultimap<File, InputFileDetails> substreamMap = null;
        if (inputFileDetails != null) {
            substreamMap = ArrayListMultimap.create(subStreams.size(), inputFileDetails.size());

            for (InputFileDetails detail : inputFileDetails) {

                File subStream = getFileForPath(subStreams, detail.getFile().getPath());
                if (subStream != null) {
                    substreamMap.put(subStream, detail);
                    continue;
                }

                // if we are here, we did not find a matching subStream for this file.
                if (!detail.isRemoved()) {
                    throw new RuntimeException(
                            "Non-removed InputFileDetails not matched to any subStream: "
                                    + detail.getFile());
                }

                // since the file is removed we must imagine that we have a removed sub-stream.
                // So we create the sub-stream to hold it.
                // Find the root folder.
                File root = Iterables.getOnlyElement(input.getFiles().get());
                // get the name of the removed subStream.
                String name = getSubStreamRoot(root, detail.getFile());

                // create the subStream, add the entry in the map, and the subStream to the list.
                File removedSubStream = new File(root, name);
                substreamMap.put(removedSubStream, detail);
                subStreams.add(removedSubStream);
            }
        }

        // now create the TransformInput/Output
        // reuse the builder to reuse the sets of content types/scopes.
        TransformInputImpl.Builder inputBuilder = TransformInputImpl.builder()
                .setContentTypes(input.getContentTypes())
                .setScopes(input.getScopes())
                .setFormat(Format.SINGLE_FOLDER);

        for (File subStream : subStreams) {
            inputBuilder.setFiles(subStream).resetChangedFiles();

            // add incremental data if any
            if (substreamMap != null) {
                List<InputFileDetails> changedFiles = substreamMap.get(subStream);
                if (changedFiles != null) {
                    for (InputFileDetails details : changedFiles) {
                        inputBuilder.addChangedFile(
                                details.getFile(),
                                details.isAdded() ? ADDED
                                        : (details.isModified() ? CHANGED : REMOVED));
                    }
                }
            }

            results.put(inputBuilder.build(), creator.create(subStream.getName()));
        }

        return results;
    }

    /**
     * Returns a {@link File} from a list of files which path starts with the provided path.
     * @param files the list of files to look into.
     * @param path the path of the searched file
     * @return the file which path starts with the provided path or null if not found.
     */
    @Nullable
    private static File getFileForPath(List<File> files, String path) {
        for (File file : files) {
            if (path.startsWith(file.getPath())) {
                return file;
            }
        }
        return null;
    }

    private static String getSubStreamRoot(@NonNull File root, @NonNull File subStreamFile) {
        String relativePath = FileUtils.relativePossiblyNonExistingPath(subStreamFile, root);
        // get the first element.
        int pos = relativePath.indexOf(File.separator);
        if (pos != -1) {
            return relativePath.substring(0, pos);
        }

        return relativePath;
    }

    /**
     * Creates the TransformInput from the TransformStream instances.
     *
     * This only applies to the referenced inputs.
     *
     * @param streamToChangedFiles optional map of stream to changed files.
     * @return the list of transform inputs.
     */
    @NonNull
    private List<TransformInput> createReferencedTransformInputs(
            @Nullable ListMultimap<TransformStream, InputFileDetails> streamToChangedFiles) {

        List<TransformInput> results = Lists.newArrayListWithCapacity(referencedInputStreams.size());

        for (TransformStream input : referencedInputStreams) {
            TransformInputImpl.Builder builder = TransformInputImpl.builder().from(input);
            if (streamToChangedFiles != null) {
                List<InputFileDetails> changedFiles = streamToChangedFiles.get(input);
                if (changedFiles != null) {
                    for (InputFileDetails details : changedFiles) {
                        builder.addChangedFile(
                                details.getFile(),
                                details.isAdded() ? ADDED : (details.isModified() ? CHANGED : REMOVED));
                    }
                }
            }

            results.add(builder.build());
        }

        return ImmutableList.copyOf(results);
    }

    @NonNull
    private TransformOutput findOutputFor(@NonNull TransformStream input) {
        for (TransformStream output : outputStreams) {
            if (input == output.getParentStream()) {
                return output.asOutput();
            }
        }

        throw new RuntimeException("No matching output for AS_INPUT transform with input: " + input);
    }

    @NonNull
    private Collection<TransformOutput> findOutputsFor(@NonNull TransformStream inputStream) {
        Collection<TransformOutput> outputs = Lists.newArrayListWithExpectedSize(
                transform.getOutputTypes().size());
        for (TransformStream outputStream : outputStreams) {
            if (inputStream == outputStream.getParentStream()) {
                outputs.add(outputStream.asOutput());
            }
        }

        return outputs;
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
        return transform.getSecondaryFolderOutputs();
    }

    @Input
    Map<String, Object> getOtherInputs() {
        return transform.getParameterInputs();
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
        @NonNull
        private Collection<TransformStream> outputStreams;
        @Nullable
        private final ConfigActionCallback<T> configActionCallback;

        ConfigAction(
                @NonNull String variantName,
                @NonNull String taskName,
                @NonNull T transform,
                @NonNull Collection<TransformStream> consumedInputStreams,
                @NonNull Collection<TransformStream> referencedInputStreams,
                @NonNull Collection<TransformStream> outputStreams,
                @Nullable ConfigActionCallback<T> configActionCallback) {
            this.variantName = variantName;
            this.taskName = taskName;
            this.transform = transform;
            this.consumedInputStreams = consumedInputStreams;
            this.referencedInputStreams = referencedInputStreams;
            this.outputStreams = outputStreams;
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
        public void execute(TransformTask task) {
            task.transform = transform;
            task.consumedInputStreams = consumedInputStreams;
            task.referencedInputStreams = referencedInputStreams;
            task.outputStreams = outputStreams;
            task.setVariantName(variantName);

            if (configActionCallback != null) {
                configActionCallback.callback(transform, task);
            }
        }
    }
}
