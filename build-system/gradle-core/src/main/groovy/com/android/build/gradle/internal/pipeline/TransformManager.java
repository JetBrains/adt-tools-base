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
import com.android.build.gradle.internal.scope.BaseScope;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.NoOpTransform;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.Transform.Type;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
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

    public static final Set<Scope> EMPTY_SCOPES = ImmutableSet.of();

    public static final Set<ContentType> CONTENT_CLASS = Sets.immutableEnumSet(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = Sets.immutableEnumSet(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = Sets.immutableEnumSet(RESOURCES);
    public static final Set<ContentType> CONTENT_DEX = Sets.immutableEnumSet(DEX);
    public static final Set<Scope> SCOPE_FULL_PROJECT = Sets.immutableEnumSet(
            Scope.PROJECT,
            Scope.PROJECT_LOCAL_DEPS,
            Scope.SUB_PROJECTS,
            Scope.SUB_PROJECTS_LOCAL_DEPS,
            Scope.EXTERNAL_LIBRARIES);
    public static final Set<Scope> SCOPE_FULL_LIBRARY = Sets.immutableEnumSet(
            Scope.PROJECT,
            Scope.PROJECT_LOCAL_DEPS);

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
            @NonNull BaseScope variantScope,
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
     * @param scope the current scope
     * @param transform the transform to add
     * @param callback a callback that is run when the task is actually configured
     * @param <T> the type of the transform
     * @return the AndroidTask for the given transform task.
     */
    public <T extends Transform> AndroidTask<TransformTask> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull BaseScope scope,
            @NonNull T transform,
            @Nullable TransformTask.ConfigActionCallback<T> callback) {
        validateTransform(transform);

        List<TransformStream> inputStreams = Lists.newArrayList();
        List<TransformStream> outputStreams = Lists.newArrayList();
        String taskName = scope.getTaskName(getTaskNamePrefix(transform));

        // find input streams, and compute output streams for the transform.
        findTransformStreams(
                transform,
                scope,
                inputStreams,
                outputStreams,
                taskName,
                scope.getGlobalScope().getBuildDir());

        if (inputStreams.isEmpty()) {
            // didn't find any match. Means there is a broken order somewhere in the streams.
            throw new RuntimeException(String.format(
                    "Unable to add Transform '%s' on variant '%s': requested streams not available: %s/%s",
                    transform.getName(), scope.getVariantConfiguration().getFullName(),
                    transform.getScopes(), transform.getInputTypes()));
        }

        // add referenced-only streams
        List<TransformStream> referencedStreams = grabReferencedStreams(transform);

        if (DEBUG) {
            System.out.println(
                    "ADDED TRANSFORM(" + scope.getVariantConfiguration().getFullName()
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

        transforms.add(transform);

        // create the task...
        AndroidTask<TransformTask> task = taskRegistry.create(
                taskFactory,
                new TransformTask.ConfigAction<T>(
                        scope.getVariantConfiguration().getFullName(),
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

    private static <T extends Transform> void validateTransform(T transform) {
        switch (transform.getTransformType()) {
            case AS_INPUT:
                if (!(transform instanceof AsInputTransform)) {
                    throw new RuntimeException(
                            "Transform with Type AS_INPUT must be implementation of AsInputTransform");
                }
                break;
            case COMBINED:
                if (!(transform instanceof CombinedTransform)) {
                    throw new RuntimeException(
                            "Transform with Type COMBINED must be implementation of CombinedTransform");
                }
                break;
            case FORK_INPUT:
                if (!(transform instanceof ForkTransform)) {
                    throw new RuntimeException(
                            "Transform with Type FORK_INPUT must be implementation of ForkTransform");
                }
                if (transform.getInputTypes().size() > 1) {
                    throw new RuntimeException(String.format(
                            "FORK_INPUT mode only works since a single input type. Transform '%s' declared with %s",
                            transform.getName(), transform.getInputTypes()));
                }
                break;
            case NO_OP:
                if (!(transform instanceof NoOpTransform)) {
                    throw new RuntimeException(
                            "Transform with Type NO_OP must be implementation of NoOpTransform");
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported transform type: " + transform.getTransformType());
        }
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

    @NonNull
    public TransformStream getSingleStream(@NonNull StreamFilter streamFilter) {
        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());
        for (TransformStream s : streams) {
            if (streamFilter.accept(s.getContentTypes(), s.getScopes())) {
                streamMatches.add(s);
            }
        }

        return Iterables.getOnlyElement(streamMatches);
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
    public Map<File, Format> getPipelineOutput(
            @NonNull StreamFilter streamFilter,
            @Nullable Format requiredFormat) {
        ImmutableList<TransformStream> streams = getStreams(streamFilter);
        if (streams.isEmpty()) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<File, Format> builder = ImmutableMap.builder();
        for (TransformStream stream : streams) {
            Format format = stream.getFormat();
            if (requiredFormat == null || requiredFormat == format) {
                for (File file : stream.getFiles().get()) {
                    builder.put(file, format);
                }
            }
        }

        return builder.build();
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

    /**
     * Finds the stream the transform consumes, and return them.
     *
     * This also removes them from the instance list. They will be replaced with the output
     * stream(s) from the transform.
     *
     * @param transform the transform.
     * @param scope the scope the transform is applied to.
     * @param inputStreams the out list of input streams for the transform.
     * @param outputStreams the out list of output streams for the transform.
     * @param taskName the name of the task that will run the transform
     * @param buildDir the build dir of the project.
     */
    private void findTransformStreams(
            @NonNull Transform transform,
            @NonNull BaseScope scope,
            @NonNull List<TransformStream> inputStreams,
            @NonNull List<TransformStream> outputStreams,
            @NonNull String taskName,
            @NonNull File buildDir) {

        Set<ContentType> requestedTypes = transform.getInputTypes();
        Set<Scope> requestedScopes = transform.getScopes();
        EnumSet<ContentType> foundTypes = EnumSet.noneOf(ContentType.class);

        String transformName = transform.getName();
        Type type = transform.getTransformType();
        Format outputFormat = transform.getOutputFormat();

        // map to handle multi stream sharing the same type/scope
        Map<StreamKey, Integer> dupCounter = Maps
                .newHashMapWithExpectedSize(inputStreams.size());

        boolean consumesInputs = type != Type.NO_OP;

        Collection<String> variantDirSegments = null;
        if (consumesInputs) {
            variantDirSegments = scope.getDirectorySegments();
        }

        // list to hold the list of unused streams in the manager after everything is done.
        // they'll be put back in the streams collection, along with the new outputs.
        List<TransformStream> oldStreams = Lists.newArrayListWithExpectedSize(streams.size());

        for (TransformStream stream : streams) {

            // The stream must contain only the required scopes, not any other.
            // If the stream contains more types, it's not a problem since the transform
            // can disambiguate (unlike on scopes)
            Set<ContentType> contentTypes = stream.getContentTypes();
            if (requestedScopes.containsAll(stream.getScopes()) &&
                    hasMatchIn(requestedTypes, contentTypes)) {
                inputStreams.add(stream);

                foundTypes.addAll(contentTypes);
                if (consumesInputs) {
                    // if the stream has more types than the requested types, create a copy
                    // ot if with the remaining streams, so that other transforms can still
                    // consume the other types in this scope.
                    // However don't do this if the transform type is no_op since it doesn't
                    // consume its streams anyway.
                    if (!requestedTypes.equals(contentTypes)) {
                        EnumSet<ContentType> diff = EnumSet.copyOf(contentTypes);
                        diff.removeAll(requestedTypes);
                        if (!diff.isEmpty()) {
                            // create a copy of the stream with only the remaining type.
                            oldStreams.add(TransformStream.builder()
                                    .copyWithRestrictedTypes(stream, diff)
                                    .build());

                        }
                    }
                } else {
                    // if stream is not consumed, put it back in the list.
                    oldStreams.add(stream);
                }

                // now handle the output.
                switch (type) {
                    case AS_INPUT:
                        outputStreams.add(createMatchingOutput(
                                stream,
                                transform.getOutputTypes(),
                                outputFormat,
                                transformName,
                                taskName,
                                variantDirSegments,
                                buildDir,
                                dupCounter));
                        break;
                    case FORK_INPUT:
                        // for this case, loop on all the output types, and create a matching
                        // stream for each
                        for (ContentType outputType : transform.getOutputTypes()) {
                            outputStreams.add(createMatchingOutput(
                                    stream,
                                    EnumSet.of(outputType),
                                    outputFormat,
                                    transformName,
                                    taskName,
                                    variantDirSegments,
                                    buildDir,
                                    dupCounter));
                        }
                        break;
                    // Now all the types that don't have per-input outputs.
                    case NO_OP:
                    case COMBINED:
                        // empty on purpose
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Unsupported transform type: " + type);
                }

            } else {
                oldStreams.add(stream);
            }
        }

        // special combined output.
        if (type == Type.COMBINED) {
            // create single combined output stream for all types and scopes
            Set<ContentType> types = transform.getOutputTypes();
            Set<Scope> scopes = transform.getScopes();

            File destinationFile = FileUtils.join(buildDir, StringHelper.toStrings(
                    AndroidProject.FD_INTERMEDIATES,
                    FD_TRANSFORMS,
                    combineTypesForName(types),
                    combineScopesForName(scopes),
                    transform.getName(),
                    variantDirSegments));

            if (transform.getOutputFormat() == Format.JAR) {
                destinationFile = new File(destinationFile, SdkConstants.FN_CLASSES_JAR);
            }

            outputStreams.add(TransformStream.builder()
                    .addContentTypes(types)
                    .addScopes(scopes)
                    .setFormat(transform.getOutputFormat())
                    .setDependency(taskName)
                    .setFiles(destinationFile)
                    .build());
        }

        // update the list of available streams.
        streams.clear();
        streams.addAll(oldStreams);
        streams.addAll(outputStreams);
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

    private static TransformStream createMatchingOutput(
            @NonNull TransformStream input,
            @NonNull Set<ContentType> types,
            @NonNull Format format,
            @NonNull String transformName,
            @NonNull String taskName,
            @NonNull Collection<String> variantDirSegments,
            @NonNull File buildDir,
            @NonNull Map<StreamKey, Integer> dupCounter) {
        // because we need a matching output for each input, but we can be in a case
        // where we have 2 similar inputs (same types/scopes), we want to append an
        // integer somewhere in the path. So we record each inputs to detect dups.
        StreamKey key = new StreamKey(input);
        String deDupedName = transformName;
        Integer count = dupCounter.get(key);
        if (count == null) {
            dupCounter.put(key, 1);
        } else {
            deDupedName = deDupedName + "-" + count;
            dupCounter.put(key, count + 1);
        }

        // copy with new location.
        return TransformStream.builder()
                .copyWithRestrictedTypes(input, types)
                .setFiles(FileUtils.join(buildDir, StringHelper.toStrings(
                        AndroidProject.FD_INTERMEDIATES,
                        FD_TRANSFORMS,
                        combineTypesForName(types),
                        combineScopesForName(input.getScopes()),
                        deDupedName,
                        variantDirSegments)))
                .setDependency(taskName)
                .setParentStream(input)
                .setFormat(format)
                .build();
    }

    private static String combineTypesForName(@NonNull Set<ContentType> types) {
        return Joiner.on("_and_").join(types);
    }

    private static String combineScopesForName(@NonNull Set<Scope> types) {
        if (SCOPE_FULL_PROJECT.equals(types)) {
            return "FULL_PROJECT";
        }

        return Joiner.on("_and_").join(types);
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
