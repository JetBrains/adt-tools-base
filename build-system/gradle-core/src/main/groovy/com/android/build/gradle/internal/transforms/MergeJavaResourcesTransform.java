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

package com.android.build.gradle.internal.transforms;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformInput.FileStatus;
import com.android.build.transform.api.TransformOutput;
import com.android.builder.model.PackagingOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Transform to merge all the Java resources
 */
public class MergeJavaResourcesTransform implements CombinedTransform {

    @NonNull
    private final VariantScope scope;
    @NonNull
    private final PackagingOptions packagingOptions;

    @NonNull
    private final Set<Scope> mergeScopes;

    public MergeJavaResourcesTransform(
            @NonNull VariantScope scope,
            @NonNull PackagingOptions packagingOptions,
            @NonNull Set<Scope> mergeScopes) {
        this.scope = scope;
        this.packagingOptions = packagingOptions;
        this.mergeScopes = Sets.immutableEnumSet(mergeScopes);
    }

    @NonNull
    @Override
    public String getName() {
        return "mergeJavaRes";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_RESOURCES;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_RESOURCES;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return mergeScopes;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.COMBINED;
    }

    @NonNull
    @Override
    public Format getOutputFormat() {
        return Format.SINGLE_FOLDER;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        // TODO the inputs that controls the merge.
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    public static List<FileFilter.SubStream> getExpandedFolders(@NonNull Collection<TransformInput> inputs) {
        ImmutableList.Builder<FileFilter.SubStream> builder = ImmutableList.builder();
        for (TransformInput stream : inputs) {
            switch (stream.getFormat()) {
                case SINGLE_FOLDER:
                    for (File file : stream.getFiles()) {
                        // TODO find name for this stream.
                        builder.add(new FileFilter.SubStream(file, "unnamed"));
                    }
                    break;
                case MULTI_FOLDER:
                    for (File file : stream.getFiles()) {
                        File[] children = file.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                if (child.isDirectory()) {
                                    builder.add(new FileFilter.SubStream(child, child.getName()));
                                }
                            }
                        }
                    }
                    break;
                case SINGLE_JAR:
                    throw new RuntimeException("Merge Java Res Transform does not support SINGLE_JAR stream types as inputs");
                default:
                    throw new RuntimeException("Unsupported ScopedContent.Format value: " + stream.getFormat().name());
            }
        }

        return builder.build();
    }

    @Override
    public void transform(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutput combinedOutput,
            boolean isIncremental) throws IOException, TransformException {

        // all the output will be the same since the transform type is COMBINED.
        checkNotNull(combinedOutput, "Found no output in transform with Type=COMBINED");
        File outFolder = combinedOutput.getOutFile();

        FileFilter packagingOptionsFilter =
                new FileFilter(getExpandedFolders(inputs), packagingOptions);

        // TODO We need to fix this and merge the native jni libs either with a different filter or in this task.
        scope.setPackagingOptionsFilter(packagingOptionsFilter);

        if (!isIncremental) {
            for (TransformInput input : inputs) {
                boolean handleClassFiles = input.getContentTypes().contains(ContentType.CLASSES);

                for (File file : input.getFiles()) {
                    if (file.isFile()) {
                        handleAddedOrChangedFile(packagingOptionsFilter, outFolder, file, handleClassFiles);
                    } else if (file.isDirectory()) {
                        for (File contentFile : Files.fileTreeTraverser().postOrderTraversal(file).toList()) {
                            if (contentFile.isFile()) {
                                handleAddedOrChangedFile(packagingOptionsFilter, outFolder,
                                        contentFile, handleClassFiles);
                            }
                        }
                    }
                }
            }
        } else {
            for (TransformInput stream : inputs) {
                boolean handleClassFiles = stream.getContentTypes().contains(ContentType.CLASSES);

                for (Entry<File, FileStatus> entry : stream.getChangedFiles().entrySet()) {
                    switch (entry.getValue()) {
                        case ADDED:
                        case CHANGED:
                            handleAddedOrChangedFile(packagingOptionsFilter, outFolder, entry.getKey(), handleClassFiles);
                            break;
                        case REMOVED:
                            try {
                                packagingOptionsFilter.handleRemoved(
                                        outFolder, entry.getKey());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                    }
                }
            }
        }
    }

    private static void handleAddedOrChangedFile(
            @NonNull FileFilter packagingOptionsFilter,
            @NonNull File outFolder,
            @NonNull File file,
            boolean handleClassFiles) throws IOException {
        if (handleClassFiles) {
            String fileName = file.getName().toLowerCase(Locale.getDefault());
            if (fileName.endsWith(SdkConstants.DOT_CLASS)) {
                return;
            }
        }

        packagingOptionsFilter.handleChanged(outFolder, file);
    }
}
