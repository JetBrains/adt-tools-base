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

import static com.android.build.transform.api.ScopedContent.ContentType.CLASSES;
import static com.android.build.transform.api.ScopedContent.ContentType.DEX;
import static com.android.build.transform.api.ScopedContent.ContentType.RESOURCES;
import static com.android.utils.StringHelper.capitalize;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.Transform.Type;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages the transforms for a variant.
 *
 * Th actual execution is handled by Gradle through the tasks.
 * Instead it's a mean to more easily configure a series of transforms that consume each other's
 * inputs when several of these transform are optional.
 */
public class TransformManager {

    private static final boolean DEBUG = false;

    private static final String FD_TRANSFORMS = "transforms";

    public static final Set<Scope> EMPTY_SCOPES = Sets
            .immutableEnumSet(EnumSet.noneOf(Scope.class));

    public static final Set<ContentType> CONTENT_CLASS = Sets.immutableEnumSet(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = Sets.immutableEnumSet(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = Sets.immutableEnumSet(RESOURCES);
    public static final Set<ContentType> CONTENT_DEX = Sets.immutableEnumSet(DEX);

    /**
     *  Format that are disallowed as Transforms outputs.
     *  This is due to the current technical difficulties of having a Transform that outputs
     *  a unknown list of File (at creation/wiring up time.)
     */
    static final Set<Format> DISALLOWED_OUTPUT_FORMATS = EnumSet.of(
            Format.MIXED_FOLDERS_AND_JARS,
            Format.MULTI_JAR);

    @NonNull
    private final AndroidTaskRegistry taskRegistry;

    /**
     * These are the streams that are available for new Transforms to consume.
     *
     * Once a new transform is added, the streams that it consumes are removed from this list,
     * and the streams it produces are put instead.
     *
     * When all the transforms have been added, the remaining streams should be consumed by
     * standard Tasks somehow.
     *
     * @see #getStreams(StreamFilter)
     */
    @NonNull
    private final List<TransformStream> streams = Lists.newArrayList();

    private final List<Transform> transforms = Lists.newArrayList();

    public TransformManager(@NonNull AndroidTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @NonNull
    public AndroidTaskRegistry getTaskRegistry() {
        return taskRegistry;
    }

    public void addStream(@NonNull TransformStream stream) {
        streams.add(stream);
    }

    public void addStreams(@NonNull TransformStream... streams) {
        this.streams.addAll(Arrays.asList(streams));
    }

    public void addStreams(@NonNull Collection<TransformStream> streams) {
        this.streams.addAll(streams);
    }

    public <T extends Transform> AndroidTask<TransformTask> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            @NonNull T transform) {
        return addTransform(taskFactory, variantScope, transform, null /*callback*/);
    }

    /**
     * Adds a Transform.
     *
     * This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * This also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param variantScope the current variant
     * @param transform the transform to add
     * @param callback a callback that is run when the task is actually configured
     * @param <T> the type of the transform
     * @return the AndroidTask for the given transform task.
     */
    public <T extends Transform> AndroidTask<TransformTask> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            @NonNull T transform,
            @Nullable TransformTask.ConfigActionCallback<T> callback) {
        if (DISALLOWED_OUTPUT_FORMATS.contains(transform.getOutputFormat())) {
            throw new RuntimeException(
                    "Cannot add a Transform with OutputFormat: :" + transform.getOutputFormat());
        }

        List<TransformStream> inputStreams = grabStreams(transform);
        if (inputStreams.isEmpty()) {
            // didn't find any match. Means there is a broken order somewhere in the streams.
            throw new RuntimeException(String.format(
                    "Unable to add Transform '%s' on variant '%s': requested streams not available: %s",
                    transform.getName(), variantScope.getVariantConfiguration().getFullName(),
                    transform.getScopes()));
        }


        String taskName = variantScope.getTaskName(getTaskNamePrefix(transform));

        // create new Stream to match the output of the transform.
        Collection<TransformStream> outputStreams = computeOutputStreams(
                transform, inputStreams, taskName,
                variantScope.getVariantConfiguration().getDirName(),
                variantScope.getGlobalScope().getBuildDir());
        streams.addAll(outputStreams);

        // TODO: we probably need a map from transform to tasks
        transforms.add(transform);

        // create the task...
        List<TransformStream> referencedStreams = grabReferencedStreams(transform);

        if (DEBUG) {
            System.out.println(
                    "ADDED TRANSFORM(" + variantScope.getVariantConfiguration().getFullName()
                            + "):");
            System.out.println("\tName: " + transform.getName());
            System.out.println("\tTask: " + taskName);
            for (TransformStream sd : inputStreams) {
                System.out.println("\tInputStream: " + sd);
            }
            for (TransformStream sd : referencedStreams) {
                System.out.println("\tRef'edStream: " + sd);
            }
            for (TransformStream sd : outputStreams) {
                System.out.println("\tOutputStream: " + sd);
            }
        }

        AndroidTask<TransformTask> task = taskRegistry.create(
                taskFactory,
                new TransformTask.ConfigAction<T>(
                        variantScope.getVariantConfiguration().getFullName(),
                        taskName,
                        transform,
                        inputStreams,
                        referencedStreams,
                        outputStreams,
                        callback));

        for (TransformStream s : inputStreams) {
            task.dependsOn(taskFactory, s.getDependencies());
        }
        for (TransformStream s : referencedStreams) {
            task.dependsOn(taskFactory, s.getDependencies());
        }

        return task;
    }

    @NonNull
    public List<TransformStream> getStreams() {
        return streams;
    }

    @NonNull
    public ImmutableList<TransformStream> getStreamsByContent(
            @NonNull ContentType contentType) {
        return getStreamsByContent(EnumSet.of(contentType));
    }

    @NonNull
    public ImmutableList<TransformStream> getStreamsByContent(@NonNull ContentType type1,
            @NonNull ContentType... otherTypes) {
        return getStreamsByContent(EnumSet.of(type1, otherTypes));
    }

    @NonNull
    public ImmutableList<TransformStream> getStreamsByContent(
            @NonNull Set<ContentType> contentTypes) {
        ImmutableList.Builder<TransformStream> streamsByType = ImmutableList.builder();
        for (TransformStream s : streams) {
            if (contentTypes.containsAll(s.getContentTypes())) {
                streamsByType.add(s);
            }
        }

        return streamsByType.build();
    }

    public interface StreamFilter {
        boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes);
    }

    @NonNull
    public ImmutableList<TransformStream> getStreams(@NonNull StreamFilter streamFilter) {
        ImmutableList.Builder<TransformStream> streamsByType = ImmutableList.builder();
        for (TransformStream s : streams) {
            if (streamFilter.accept(s.getContentTypes(), s.getScopes())) {
                streamsByType.add(s);
            }
        }

        return streamsByType.build();
    }

    public List<TransformStream> getStreamsByContentAndScope(
            @NonNull ContentType contentType,
            @NonNull Set<Scope> allowedScopes) {
        ImmutableList.Builder<TransformStream> streamsByType = ImmutableList.builder();
        for (TransformStream s : streams) {
            if (s.getContentTypes().equals(EnumSet.of(contentType)) &&
                    allowedScopes.containsAll(s.getScopes())) {
                streamsByType.add(s);
            }
        }

        return streamsByType.build();
    }

    /**
     * Returns a single file that is the output of the pipeline for the scope/content indicated
     * via the {@link StreamFilter}.
     *
     * An optional {@link Format} indicate what the format of the output should be like.
     *
     * This will throw an exception if there is more than one matching Stream or if
     * the stream has more than one files.
     *
     * @param streamFilter the filter
     * @param requiredFormat an optional format
     * @return the file or null if there's no match (no stream or wrong format)
     */
    @Nullable
    public File getSinglePipelineOutput(
            @NonNull StreamFilter streamFilter,
            @Nullable Format requiredFormat) {

        ImmutableList<TransformStream> streams = getStreams(streamFilter);
        if (streams.isEmpty()) {
            return null;
        }

        // get the single stream.
        TransformStream transformStream = Iterables.getOnlyElement(streams);

        if (requiredFormat != null &&
                requiredFormat != transformStream.getFormat()) {
            return null;
        }

        return Iterables.getOnlyElement(transformStream.getFiles().get());
    }

    @NonNull
    private static String getTaskNamePrefix(@NonNull Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");

        Iterator<ContentType> iterator = transform.getInputTypes().iterator();
        // there's always at least one
        sb.append(capitalize(iterator.next().name().toLowerCase(Locale.getDefault())));
        while (iterator.hasNext()) {
            sb.append("And").append(capitalize(
                    iterator.next().name().toLowerCase(Locale.getDefault())));
        }

        sb.append("With").append(capitalize(transform.getName())).append("For");

        return sb.toString();
    }

    private static final class StreamKey {
        @NonNull
        private final Set<ContentType> types;
        @NonNull
        private final Set<Scope> scopes;

        StreamKey(@NonNull TransformStream stream) {
            this.types = stream.getContentTypes();
            this.scopes = stream.getScopes();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StreamKey that = (StreamKey) o;
            return Objects.equal(types, that.types) &&
                    Objects.equal(scopes, that.scopes);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(types, scopes);
        }
    }

    @NonNull
    private static Collection<TransformStream> computeOutputStreams(
            @NonNull Transform transform,
            @NonNull List<TransformStream> inputStreams,
            @NonNull String taskName,
            @NonNull String variantDirName,
            @NonNull File buildDir) {
        switch (transform.getTransformType()) {
            case AS_INPUT: {
                // for each input, create a matching output.
                // but only create a single output for any combination of contentType/scope.
                Map<StreamKey, Integer> dupCounter = Maps
                        .newHashMapWithExpectedSize(inputStreams.size());
                List<TransformStream> outputStreams = Lists.newArrayListWithExpectedSize(
                        inputStreams.size());
                String name = transform.getName();

                for (TransformStream input : inputStreams) {
                    // because we need a matching output for each input, but we can be in a case
                    // where we have 2 similar inputs (same types/scopes), we want to append an
                    // integer somewhere in the path. So we record each inputs to detect dups.
                    StreamKey key = new StreamKey(input);
                    String deDupedName = name;
                    Integer count = dupCounter.get(key);
                    if (count == null) {
                        dupCounter.put(key, 1);
                    } else {
                        deDupedName = deDupedName + "-" + count;
                        dupCounter.put(key, count + 1);
                    }

                    // copy with new location.
                    outputStreams.add(TransformStream.builder()
                            .copyWithRestrictedTypes(input, transform.getOutputTypes())
                            .setFiles(FileUtils.join(buildDir,
                                    AndroidProject.FD_INTERMEDIATES,
                                    FD_TRANSFORMS,
                                    combineNames(transform.getOutputTypes()),
                                    combineNames(input.getScopes()),
                                    deDupedName,
                                    variantDirName))
                            .setDependency(taskName)
                            .setParentStream(input)
                            .setFormat(transform.getOutputFormat())
                            .build());
                }

                return outputStreams;
            }
            case COMBINED: {
                // create single combined output stream for all types and scopes
                Set<ContentType> types = transform.getOutputTypes();
                Set<Scope> scopes = transform.getScopes();

                File destinationFile = FileUtils.join(buildDir,
                        AndroidProject.FD_INTERMEDIATES,
                        FD_TRANSFORMS,
                        combineNames(types),
                        combineNames(scopes),
                        transform.getName(),
                        variantDirName);

                if (transform.getOutputFormat() == Format.SINGLE_JAR) {
                    destinationFile = new File(destinationFile, SdkConstants.FN_CLASSES_JAR);
                }

                return Collections.singletonList(TransformStream.builder()
                        .addContentTypes(types)
                        .addScopes(scopes)
                        .setFormat(transform.getOutputFormat())
                        .setDependency(taskName)
                        .setFiles(destinationFile)
                        .build());
            }
            case NO_OP:
                // put the input streams back into the pipeline.
                return inputStreams;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported transform type: " + transform.getTransformType());
        }
    }

    private static String combineNames(Set types) {
        return Joiner.on("_and_").join(types);
    }

    /**
     * Finds the stream the transform consumes, and return them.
     *
     * This also removes them from the instance list. They will be replaced with the output
     * stream(s) from the transform.
     *
     * @param transform the transform.
     * @return the input streams for the transform.
     */
    @NonNull
    private List<TransformStream> grabStreams(@NonNull Transform transform) {
        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());

        Set<ContentType> requestedTypes = transform.getInputTypes();
        Set<Scope> requestedScopes = transform.getScopes();
        EnumSet<ContentType> foundTypes = EnumSet.noneOf(ContentType.class);
        boolean consumesInputs = transform.getTransformType() != Type.NO_OP;
        int i = 0;
        while (i < streams.size()) {
            TransformStream stream = streams.get(i);

            // The stream must contain only the required scopes, not any other.
            // If the stream contains more types, it's not a problem since the transform
            // can disambiguate (unlike on scopes)
            Set<ContentType> contentTypes = stream.getContentTypes();
            if (requestedScopes.containsAll(stream.getScopes()) &&
                    hasMatchIn(requestedTypes, contentTypes)) {
                streamMatches.add(stream);

                foundTypes.addAll(contentTypes);
                // if the stream has more types than the requested types, create a copy
                // ot if with the remaining streams, so that other transforms can still
                // consume the other types in this scope.
                // However don't do this if the transform type is no_op since it doesn't
                // consume its streams anyway.
                TransformStream replacementStream = null;
                if (consumesInputs && !requestedTypes.equals(contentTypes)) {
                    EnumSet<ContentType> diff = EnumSet.copyOf(contentTypes);
                    diff.removeAll(requestedTypes);
                    if (!diff.isEmpty()) {
                        // create a copy of the stream with only the remaining type.
                        replacementStream = TransformStream.builder()
                                .copyWithRestrictedTypes(stream, diff)
                                .build();

                    }
                }

                streams.remove(i);
                if (replacementStream != null) {
                    streams.add(i, replacementStream);
                    i++;
                }
            } else {
                i++;
            }
        }

        // TODO ensure that we found all the types we were looking for (same for the scopes!)
        // check we've found all the types we were looking for
        if (!foundTypes.containsAll(requestedTypes)) {

        }

        return streamMatches;
    }

    private static boolean hasMatchIn(
            @NonNull Set<ContentType> requestedTypes,
            @NonNull Set<ContentType> types) {
        for (ContentType type : requestedTypes) {
            if (types.contains(type)) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    private List<TransformStream> grabReferencedStreams(@NonNull Transform transform) {
        Set<Scope> requestedScopes = transform.getReferencedScopes();
        if (requestedScopes.isEmpty()) {
            return ImmutableList.of();
        }

        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());

        Set<ContentType> requestedTypes = transform.getInputTypes();
        for (TransformStream stream : streams) {
            if (requestedScopes.containsAll(stream.getScopes()) &&
                    requestedTypes.containsAll(stream.getContentTypes())) {
                streamMatches.add(stream);
            }
        }

        return streamMatches;
    }
}
