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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.repository.Revision;
import com.android.repository.Revision.PreviewComparison;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Transform for converting classes to Jack library using Jill.
 */
public class JillTransform extends Transform {

    private final AndroidBuilder androidBuilder;

    private final DexOptions dexOptions;

    private final boolean isJackInProcessFlag;

    private final boolean forPackagedLibs;

    public JillTransform(
            AndroidBuilder androidBuilder,
            DexOptions dexOptions,
            boolean isJackInProcessFlag,
            boolean forPackagedLibs) {
        this.androidBuilder = androidBuilder;
        this.dexOptions = dexOptions;
        this.isJackInProcessFlag = isJackInProcessFlag;
        this.forPackagedLibs = forPackagedLibs;
    }

    @NonNull
    @Override
    public String getName() {
        return forPackagedLibs ? "jillPackagedLibs" : "jillRuntime";
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.<String, Object>of(
                "buildToolsVersion",
                androidBuilder.getTargetInfo().getBuildTools().getRevision().toString());
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return forPackagedLibs
                ? TransformManager.SCOPE_FULL_PROJECT
                :  Collections.singleton(QualifiedContent.Scope.PROVIDED_ONLY);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());
        checkNotNull(androidBuilder.getTargetInfo());
        try {
            Revision revision = androidBuilder.getTargetInfo().getBuildTools().getRevision();
            if (revision.compareTo(JackTransform.JACK_MIN_REV, PreviewComparison.IGNORE) < 0) {
                throw new RuntimeException(
                        "Jack requires Build Tools " + JackTransform.JACK_MIN_REV + " or later");
            }

            final Set<String> hashs = Sets.newHashSet();
            final WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
            final Map<File, File> inputFileDetails = mapAllInputs(transformInvocation);

            final AndroidBuilder builder = androidBuilder;

            for (Map.Entry<File, File> entry : inputFileDetails.entrySet()) {
                Callable<Void> action =
                        new JillCallable(entry.getKey(), entry.getValue(), hashs, builder);
                executor.execute(action);

            }
            executor.waitForTasksWithQuickFail(false);
        } catch (LoggedErrorException e) {
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    private Map<File, File> mapAllInputs(TransformInvocation transformInvocation)
            throws IOException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());
        Map<File, File> inputFiles = Maps.newHashMap();
        // if we are not in incremental mode, then outOfDate will contain
        // all th files, but first we need to delete the previous output
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            checkState(input.getDirectoryInputs().isEmpty(),
                    "Found unexpected directory input for Jill transform.");

            for (JarInput jarInput : input.getJarInputs()) {
                final File outJar = outputProvider.getContentLocation(
                        getJackFileName(jarInput.getFile()),
                        getOutputTypes(),
                        getScopes(),
                        Format.JAR);
                if (jarInput.getStatus() != Status.REMOVED) {
                    inputFiles.put(jarInput.getFile(), outJar);
                    FileUtils.mkdirs(outJar.getParentFile());
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    outJar.delete();
                }
            }
        }

        // Jill boot classpath as well as runtime libraries.
        if (!forPackagedLibs) {
            for (File file : androidBuilder.getBootClasspath(true)) {

                final File outJar = outputProvider.getContentLocation(
                        getJackFileName(file),
                        getOutputTypes(),
                        getScopes(),
                        Format.JAR);
                inputFiles.put(file, outJar);
                FileUtils.mkdirs(outJar.getParentFile());
            }
        }

        return inputFiles;
    }

    private final class JillCallable implements Callable<Void> {

        @NonNull
        private final File fileToProcess;

        @NonNull
        private final Set<String> hashs;

        @NonNull
        private final com.android.builder.core.DexOptions options = dexOptions;

        @NonNull
        private final File outputFile;

        @NonNull
        private final AndroidBuilder builder;

        private JillCallable(
                @NonNull File inputFile,
                @NonNull File outputFile,
                @NonNull Set<String> hashs,
                @NonNull AndroidBuilder builder) {
            this.hashs = hashs;
            this.fileToProcess = inputFile;
            this.outputFile = outputFile;
            this.builder = builder;
        }

        @Override
        public Void call() throws Exception {
            // TODO remove once we can properly add a library as a dependency of its test.
            String hash = FileUtils.sha1(fileToProcess);

            synchronized (hashs) {
                if (hashs.contains(hash)) {
                    return null;
                }

                hashs.add(hash);
            }

            builder.convertLibraryToJack(
                    fileToProcess,
                    outputFile,
                    options,
                    new LoggedProcessOutputHandler(builder.getLogger()),
                    isJackInProcessFlag);

            return null;
        }

        @NonNull
        public final File getOutputFile() {
            return outputFile;
        }
    }

    /**
     * Returns a unique File for the converted library, even if there are 2 libraries with the same
     * file names (but different paths)
     *
     * @param inputFile the library
     */
    @NonNull
    public static String getJackFileName(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE);

        return name + "-" + hashCode.toString();
    }

    public boolean isForRuntimeLibs() {
        return !forPackagedLibs;
    }
}
