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

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.JackPreDexTransform;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.JackProcessOptions;
import com.android.ide.common.process.ProcessException;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.sdklib.BuildToolInfo;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transform for compiling using Jack.
 *
 * <p>Transform inputs:</p>
 * <ul>
 *     <li>Jack format libraries, produced by the for packaged libs {@link JackPreDexTransform}</li>
 *     <li>Jack format classpath, produced by the for runtime libs {@link JackPreDexTransform}
 *         i.e. normally the android.jar</li>
 * </ul>
 * <p>Secondary inputs:</p>
 * <ul>
 *     <li>Java source files to be compiled by Jack.</li>
 *     <li>Proguard config files</li>
 *     <li>JarJar config files</li>
 *     <li>The Jack binary</li>
 * </ul>
 * <p>Transform outputs:</p>
 * <ul>
 *     <li>Dex files for the whole app, including its dependencies</li>
 * </ul>
 * <p>Secondary outputs:</p>
 * <ul>
 *     <li>Proguard mapping file (for decoding stacktraces and test compilation)</li>
 *     <li>Jack file for test compilation</li>
 * </ul>
 */
public class JackTransform extends Transform {
    private Project project;

    private AndroidBuilder androidBuilder;

    private JackProcessOptions options;

    private boolean jackInProcess;

    private final List<ConfigurableFileTree> sourceFileTrees = Lists.newArrayList();

    @NonNull
    @Override
    public String getName() {
        return "jack";
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
        builder.addAll(
                Iterables.transform(options.getProguardFiles(), SecondaryFile::nonIncremental));
        builder.addAll(
                Iterables.transform(options.getJarJarRuleFiles(), SecondaryFile::nonIncremental));
        builder.addAll(
                Iterables.transform(
                        options.getAnnotationProcessorClassPath(), SecondaryFile::nonIncremental));
        builder.addAll(Iterables.transform(getSourceFiles(), SecondaryFile::incremental));

        builder.add(
                SecondaryFile.nonIncremental(
                        new File(androidBuilder
                                .getTargetInfo()
                                .getBuildTools()
                                .getPath(BuildToolInfo.PathId.JACK))));

        return builder.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        if (options.getOutputFile() != null) {
            builder.add(options.getOutputFile());
        }
        if (options.getMappingFile() != null) {
            builder.add(options.getMappingFile());
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("javaResourcesFolder", options.getResourceDirectories());
        params.put("isDebugLog", options.isDebugLog());
        params.put("multiDexEnabled", options.isMultiDex());
        params.put("minSdkVersion", options.getMinSdkVersion());
        params.put("javaMaxHeapSize", options.getJavaMaxHeapSize());
        params.put("sourceCompatibility", options.getSourceCompatibility());
        params.put("buildToolsRev",
                androidBuilder.getTargetInfo().getBuildTools().getRevision().toString());
        return params;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROVIDED_ONLY,
                QualifiedContent.Scope.TESTED_CODE);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            runJack(transformInvocation);
        } catch (ProcessException | ConfigNotSupportedException | CompilationException
                | ClassNotFoundException | UnrecoverableException | ConfigurationException e) {
            throw new TransformException(e);
        }
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ProcessException, IOException, ConfigNotSupportedException,
            ClassNotFoundException, CompilationException, ConfigurationException,
            UnrecoverableException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);
        final File outDirectory = outputProvider.getContentLocation(
                "main",
                getOutputTypes(),
                getScopes(),
                Format.DIRECTORY);

        options.setDexOutputDirectory(outDirectory);
        options.setClasspaths(
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs()));
        options.setImportFiles(TransformInputUtil.getAllFiles(transformInvocation.getInputs()));
        options.setInputFiles(getSourceFiles());

        androidBuilder.convertByteCodeUsingJack(options, jackInProcess);
    }

    private Collection<File> getSourceFiles() {
        Collection<File> sourceFiles = Lists.newArrayList();
        for (ConfigurableFileTree fileTree : sourceFileTrees) {
            sourceFiles.addAll(fileTree.getFiles());
        }
        return sourceFiles;
    }

    public File getMappingFile() {
        return options.getMappingFile();
    }

    public void addSource(File sourceFolder) {
        sourceFileTrees.add(project.fileTree(sourceFolder));
    }

    public JackTransform(
            final VariantScope scope,
            final boolean isDebugLog,
            final boolean compileJavaSources) {

        options = new JackProcessOptions();

        options.setDebugLog(isDebugLog);
        GlobalScope globalScope = scope.getGlobalScope();

        androidBuilder = globalScope.getAndroidBuilder();

        project = globalScope.getProject();
        if (compileJavaSources) {
            sourceFileTrees.addAll(scope.getVariantData().getJavaSources());
        }
        final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();
        options.setJavaMaxHeapSize(globalScope.getExtension().getDexOptions().getJavaMaxHeapSize());
        options.setJumboMode(globalScope.getExtension().getDexOptions().getJumboMode());
        boolean isDebuggable = scope.getVariantConfiguration().getBuildType().isDebuggable();
        options.setDebuggable(isDebuggable);
        options.setDexOptimize(
                Objects.firstNonNull(
                        globalScope.getExtension().getDexOptions().getOptimize(), !isDebuggable));
        options.setMultiDex(config.isMultiDexEnabled());
        options.setMinSdkVersion(config.getMinSdkVersion().getApiLevel());
        if (!Boolean.FALSE.equals(
                globalScope.getExtension().getCompileOptions().getIncremental())) {
            options.setIncrementalDir(scope.getIncrementalDir(getName()));
        }
        options.setOutputFile(scope.getJackClassesZip());
        options.setResourceDirectories(ImmutableList.of(scope.getJavaResourcesDestinationDir()));

        CoreAnnotationProcessorOptions annotationProcessorOptions =
                config.getJavaCompileOptions().getAnnotationProcessorOptions();
        checkNotNull(annotationProcessorOptions.getIncludeCompileClasspath());
        options.setAnnotationProcessorClassPath(
                Lists.newArrayList(
                        scope.getVariantData().getVariantDependency()
                                .resolveAndGetAnnotationProcessorClassPath(
                                        annotationProcessorOptions.getIncludeCompileClasspath(),
                                        androidBuilder.getErrorReporter())));
        options.setAnnotationProcessorNames(annotationProcessorOptions.getClassNames());
        options.setAnnotationProcessorOptions(annotationProcessorOptions.getArguments());
        options.setAnnotationProcessorOutputDirectory(scope.getAnnotationProcessorOutputDir());
        options.setEcjOptionFile(scope.getJackEcjOptionsFile());
        options.setAdditionalParameters(config.getJackOptions().getAdditionalParameters());

        jackInProcess = config.getJackOptions().isJackInProcess();

        if (config.getBuildType().isTestCoverageEnabled()) {
            options.setCoverageMetadataFile(scope.getJackCoverageMetadataFile());
        }

        if (config.isMinifyEnabled()) {
            // since all the output use the same resources, we can use the first output
            // to query for a proguard file.
            File sdkDir = scope.getGlobalScope().getSdkHandler().getAndCheckSdkFolder();
            checkNotNull(sdkDir);
            File defaultProguardFile = ProguardFiles.getDefaultProguardFile(
                    TaskManager.DEFAULT_PROGUARD_CONFIG_FILE, project);

            Set<File> proguardFiles = config.getProguardFiles(true /*includeLibs*/,
                    ImmutableList.of(defaultProguardFile));
            File proguardResFile = scope.getProcessAndroidResourcesProguardOutputFile();
            proguardFiles.add(proguardResFile);
            // for tested app, we only care about their aapt config since the base
            // configs are the same files anyway.
            if (scope.getTestedVariantData() != null) {
                proguardResFile = scope.getTestedVariantData().getScope()
                        .getProcessAndroidResourcesProguardOutputFile();
                proguardFiles.add(proguardResFile);
            }
            options.setProguardFiles(proguardFiles);
            options.setMappingFile(new File(scope.getProguardOutputFolder(), "mapping.txt"));
        }

        ImmutableList.Builder<File> jarJarRuleFiles = ImmutableList.builder();
        for (File file : config.getJarJarRuleFiles()) {
            jarJarRuleFiles.add(project.file(file));
        }
        options.setJarJarRuleFiles(jarJarRuleFiles.build());

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();
        AbstractCompilesUtil.setDefaultJavaVersion(
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                true /*jackEnabled*/);
        options.setSourceCompatibility(compileOptions.getSourceCompatibility().toString());
    }
}
