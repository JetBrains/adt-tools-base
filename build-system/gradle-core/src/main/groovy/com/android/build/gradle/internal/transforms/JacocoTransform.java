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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Jacoco Transform
 */
public class JacocoTransform extends Transform {

    @NonNull
    private final Supplier<Collection<File>> jacocoClasspath;

    public JacocoTransform(@NonNull  final ConfigurationContainer configurations) {
        this.jacocoClasspath = Suppliers.memoize(new Supplier<Collection<File>>() {
            @Override
            public Collection<File> get() {
                return configurations.getByName(JacocoPlugin.AGENT_CONFIGURATION_NAME).getFiles();
            }
        });
    }

    @NonNull
    @Override
    public String getName() {
        return "jacoco";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        // only run on the project classes
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return jacocoClasspath.get();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {

        checkNotNull(outputProvider, "Missing output object for transform " + getName());
        File outputDir = outputProvider.getContentLocation("main", getOutputTypes(), getScopes(),
                Format.DIRECTORY);
        FileUtils.mkdirs(outputDir);

        TransformInput input = Iterables.getOnlyElement(inputs);
        // we don't want jar inputs.
        Preconditions.checkState(input.getJarInputs().isEmpty());
        DirectoryInput directoryInput = Iterables.getOnlyElement(input.getDirectoryInputs());
        File inputDir = directoryInput.getFile();

        Instrumenter instrumenter = new Instrumenter(new OfflineInstrumentationAccessGenerator());
        if (isIncremental) {
            instrumentFilesIncremental(instrumenter, inputDir, outputDir, directoryInput.getChangedFiles());
        } else {
            instrumentFilesFullRun(instrumenter, inputDir, outputDir);
        }
    }

    private static void instrumentFilesIncremental(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputDir,
            @NonNull File outputDir,
            @NonNull Map<File, Status> changedFiles) throws IOException {
        for (Map.Entry<File, Status> changedInput : changedFiles.entrySet()) {
            File inputFile = changedInput.getKey();
            if (!inputFile.getName().endsWith(SdkConstants.DOT_CLASS)) {
                continue;
            }

            File outputFile = new File(outputDir,
                    FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
            switch (changedInput.getValue()) {
                case REMOVED:
                    FileUtils.delete(outputFile);
                    break;
                case ADDED:
                    // fall through
                case CHANGED:
                    instrumentFile(instrumenter, inputFile, outputFile);
            }
        }
    }

    private static void instrumentFilesFullRun(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputDir,
            @NonNull File outputDir) throws IOException {
        FileUtils.emptyFolder(outputDir);
        Iterable<File> files = FileUtils.getAllFiles(inputDir);
        for (File inputFile : files) {
            if (!inputFile.getName().endsWith(SdkConstants.DOT_CLASS)) {
                continue;
            }

            File outputFile = new File(outputDir, FileUtils.relativePath(inputFile, inputDir));
            instrumentFile(instrumenter, inputFile, outputFile);
        }
    }

    private static void instrumentFile(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputFile,
            @NonNull File outputFile) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = Files.asByteSource(inputFile).openBufferedStream();
            Files.createParentDirs(outputFile);
            byte[] instrumented = instrumenter.instrument(
                    inputStream,
                    inputFile.toString());
            Files.write(instrumented, outputFile);
        } finally {
            Closeables.closeQuietly(inputStream);
        }
    }

}
