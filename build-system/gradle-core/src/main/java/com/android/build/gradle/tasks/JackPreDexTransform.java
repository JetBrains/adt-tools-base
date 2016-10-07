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

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.ide.common.process.ProcessException;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.UnrecoverableException;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Predex Java libraries and convert to class to jayce format using Jack.
 */
public class JackPreDexTransform extends Transform {

    private AndroidBuilder androidBuilder;
    private String javaMaxHeapSize;
    private boolean forPackagedLibs;
    @NonNull
    private CoreJackOptions coreJackOptions;

    public JackPreDexTransform(
            @NonNull AndroidBuilder androidBuilder,
            @Nullable String javaMaxHeapSize,
            @NonNull CoreJackOptions coreJackOptions,
            boolean forPackagedLibs) {
        this.androidBuilder = androidBuilder;
        this.javaMaxHeapSize = javaMaxHeapSize;
        this.coreJackOptions = coreJackOptions;
        this.forPackagedLibs = forPackagedLibs;
    }

    @NonNull
    @Override
    public String getName() {
        return forPackagedLibs ? "preJackPackagedLibraries" : "preJackRuntimeLibraries";
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

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "buildToolsRev",
                androidBuilder.getTargetInfo().getBuildTools().getRevision().toString());
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            runJack(transformInvocation);
        } catch (ProcessException
                | ConfigurationException
                | UnrecoverableException
                | ConfigNotSupportedException
                | CompilationException
                | ClassNotFoundException e) {
            throw new TransformException(e);
        }
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ConfigNotSupportedException, CompilationException, ProcessException,
            UnrecoverableException, ConfigurationException, ClassNotFoundException, IOException,
            InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);

        for (File file : TransformInputUtil.getDirectories(transformInvocation.getInputs())) {
            JackProcessOptions options = new JackProcessOptions();
            options.setImportFiles(ImmutableList.of(file));
            File outDirectory = outputProvider.getContentLocation(
                    file.getName(),
                    getOutputTypes(),
                    getScopes(),
                    Format.DIRECTORY);
            options.setDexOutputDirectory(outDirectory);
            options.setJavaMaxHeapSize(javaMaxHeapSize);
            options.setAdditionalParameters(coreJackOptions.getAdditionalParameters());

            JackConversionCache.getCache().convertLibrary(
                    androidBuilder,
                    file,
                    outDirectory,
                    options,
                    coreJackOptions.isJackInProcess());
        }

        Iterable<File> jarInputs = forPackagedLibs
                ? TransformInputUtil.getJarFiles(transformInvocation.getInputs())
                : Iterables.concat(
                        TransformInputUtil.getJarFiles(transformInvocation.getInputs()),
                        androidBuilder.getBootClasspath(true));
        for (File file : jarInputs) {
            JackProcessOptions options = new JackProcessOptions();
            options.setImportFiles(ImmutableList.of(file));
            File outFile = outputProvider.getContentLocation(
                    getJackFileName(file),
                    getOutputTypes(),
                    getScopes(),
                    Format.JAR);
            options.setOutputFile(outFile);
            options.setJavaMaxHeapSize(javaMaxHeapSize);
            options.setAdditionalParameters(coreJackOptions.getAdditionalParameters());

            JackConversionCache.getCache().convertLibrary(
                    androidBuilder,
                    file,
                    outFile,
                    options,
                    coreJackOptions.isJackInProcess());
        }
    }

    /**
     * Returns a unique file name for the converted library, even if there are 2 libraries with the
     * same file names (but different paths)
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
