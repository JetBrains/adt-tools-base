/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transform to remove debug symbols from native libraries.
 */
public class StripDebugSymbolTransform extends Transform {
    @NonNull
    private final Project project;

    @NonNull
    private final Map<Abi, File> stripExecutables = Maps.newHashMap();

    @NonNull
    private final Set<PathMatcher> excludeMatchers;

    public StripDebugSymbolTransform(
            @NonNull Project project,
            @NonNull NdkHandler ndkHandler,
            @NonNull Set<String> excludePattern) {

        this.excludeMatchers = excludePattern.stream()
                .map(StripDebugSymbolTransform::compileGlob)
                .collect(ImmutableCollectors.toImmutableSet());
        checkArgument(ndkHandler.isConfigured());

        for(Abi abi : ndkHandler.getSupportedAbis()) {
            stripExecutables.put(abi, ndkHandler.getStripExecutable(abi));
        }
        this.project = project;
    }

    @NonNull
    @Override
    public String getName() {
        return "stripDebugSymbol";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.NATIVE_LIBS);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return stripExecutables.values().stream()
                .map(f -> new SecondaryFile(f, false))
                .collect(Collectors.toList());
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        boolean isIncremental = transformInvocation.isIncremental();

        if (!isIncremental) {
            outputProvider.deleteAll();
        }
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File folder = directoryInput.getFile();
                File output = outputProvider.getContentLocation(
                        directoryInput.getName(),
                        getInputTypes(),
                        directoryInput.getScopes(),
                        Format.DIRECTORY);
                if (isIncremental) {
                    for (Map.Entry<File, Status> fileStatus
                            : directoryInput.getChangedFiles().entrySet()) {
                        File input = fileStatus.getKey();
                        if (input.isDirectory()) {
                            continue;
                        }
                        String abiName = input.getParentFile().getName();
                        Abi abi = Abi.getByName(abiName);
                        String path = FileUtils.relativePossiblyNonExistingPath(input, folder);

                        File strippedLib = new File(
                                output,
                                FileUtils.relativePossiblyNonExistingPath(input, folder));
                        switch(fileStatus.getValue()) {
                            case ADDED:
                            case CHANGED:
                                if (excludeMatchers.stream().anyMatch(m -> m.matches(Paths.get(path)))) {
                                    FileUtils.mkdirs(strippedLib.getParentFile());
                                    FileUtils.copyFile(input, strippedLib);
                                } else {
                                    stripFile(input, strippedLib, abi);
                                }
                                break;
                            case REMOVED:
                                FileUtils.deleteIfExists(strippedLib);
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    for (File input : FileUtils.getAllFiles(folder)) {
                        if (input.isDirectory()) {
                            continue;
                        }
                        String abiName = input.getParentFile().getName();
                        Abi abi = Abi.getByName(abiName);
                        String path = FileUtils.relativePath(input, folder);
                        File strippedLib = new File(output, path);

                        if (excludeMatchers.stream().anyMatch(m -> m.matches(Paths.get(path)))) {
                            FileUtils.mkdirs(strippedLib.getParentFile());
                            FileUtils.copyFile(input, strippedLib);
                        } else {
                            stripFile(input, strippedLib, abi);
                        }
                    }
                }
            }

            for (JarInput jarInput : transformInput.getJarInputs()) {
                File outFile = outputProvider.getContentLocation(
                        jarInput.getName(),
                        getInputTypes(),
                        jarInput.getScopes(),
                        Format.JAR);
                if (!isIncremental
                        || jarInput.getStatus() == Status.ADDED
                        || jarInput.getStatus() == Status.CHANGED) {
                    // Just copy the jar files.  Native libraries in a jar files are not built by
                    // the plugin.  We expect the libraries to be stripped as we won't be able to
                    // debug the libraries unless we extract them anyway.
                    FileUtils.mkdirs(outFile.getParentFile());
                    FileUtils.copyFile(jarInput.getFile(), outFile);
                } else if (jarInput.getStatus() == Status.REMOVED) {
                    FileUtils.deleteIfExists(outFile);
                }
            }
        }
    }

    private void stripFile(@NonNull File input, @NonNull File output, @Nullable Abi abi)
            throws IOException {
        FileUtils.mkdirs(output.getParentFile());
        if (abi == null) {
            FileUtils.copyFile(input, output);
            return;
        }

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(stripExecutables.get(abi));
        builder.addArgs("--strip-unneeded");
        builder.addArgs("-o");
        builder.addArgs(output.toString());
        builder.addArgs(input.toString());
        ILogger logger = new LoggerWrapper(project.getLogger());
        ProcessResult result = new GradleProcessExecutor(project).execute(
                builder.createProcess(),
                new LoggedProcessOutputHandler(logger));
        if (result.getExitValue() != 0) {
            logger.warning("Unable to strip library '%s', packaging it as is.",
                    input.getAbsolutePath());
            FileUtils.copyFile(input, output);
        }
    }


    @NonNull
    private static PathMatcher compileGlob(@NonNull String pattern) {
        FileSystem fs = FileSystems.getDefault();

        if (!pattern.startsWith("/") && !pattern.startsWith("*")) {
            pattern = "/" + pattern;
        }

        return fs.getPathMatcher("glob:" + pattern);
    }
}
