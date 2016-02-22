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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

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
 *     <li>Jack format libraries, produced by the for packaged libs {@link JillTransform}
 *         (optionally pre-dexed)</li>
 *     <li>Jack format classpath, produced by the for runtime libs {@link JillTransform}
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
    public static final Revision JACK_MIN_REV = new Revision(24, 0, 0);

    private AndroidBuilder androidBuilder;

    private boolean isDebugLog;

    private Collection<File> proguardFiles;
    private Collection<File> jarJarRuleFiles;

    private File ecjOptionsFile;
    private File outputJackFileForTestCompilation;
    private File javaResourcesFolder;

    private File mappingFile;

    private boolean multiDexEnabled;

    private int minSdkVersion;

    private String javaMaxHeapSize;

    private File incrementalDir;
    private boolean jackInProcess;

    private String sourceCompatibility;

    private final List<ConfigurableFileTree> sources = Lists.newArrayList();

    @NonNull
    @Override
    public String getName() {
        return "jack";
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
        if (proguardFiles != null) {
            for (File file : proguardFiles) {
                builder.add(new SecondaryFile(file, false));
            }
        }
        for (File file : jarJarRuleFiles) {
            builder.add(new SecondaryFile(file, false));
        }
        checkNotNull(androidBuilder.getTargetInfo());
        builder.add(new SecondaryFile(
                new File(androidBuilder.getTargetInfo().getBuildTools().getPath(
                        BuildToolInfo.PathId.JACK)),
                false));
        for (File file : getSourceFiles()) {
            builder.add(new SecondaryFile(file, true));
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        builder.add(outputJackFileForTestCompilation);
        if (mappingFile != null) {
            builder.add(mappingFile);
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("javaResourcesFolder", javaResourcesFolder);
        params.put("isDebugLog", isDebugLog);
        params.put("multiDexEnabled", multiDexEnabled);
        params.put("minSdkVersion", minSdkVersion);
        params.put("javaMaxHeapSize", javaMaxHeapSize);
        params.put("sourceCompatibility", sourceCompatibility);
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
        } catch (ProcessException e) {
            throw new TransformException(e);
        } catch (ConfigNotSupportedException e) {
            throw new TransformException(e);
        } catch (ClassNotFoundException e) {
            throw new TransformException(e);
        } catch (CompilationException e) {
            throw new TransformException(e);
        } catch (ConfigurationException e) {
            throw new TransformException(e);
        } catch (UnrecoverableException e) {
            throw new TransformException(e);
        }
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ProcessException, IOException, ConfigNotSupportedException,
            ClassNotFoundException, CompilationException, ConfigurationException,
            UnrecoverableException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);
        final File outDirectory = outputProvider.getContentLocation("main",
                getOutputTypes(),
                getScopes(),
                Format.DIRECTORY);

        FileUtils.mkdirs(outDirectory);
        FileUtils.mkdirs(outputJackFileForTestCompilation.getParentFile());

        if (jackInProcess) {
            androidBuilder.convertByteCodeUsingJackApis(
                    outDirectory,
                    outputJackFileForTestCompilation,
                    getAllInputFiles(transformInvocation.getReferencedInputs()),
                    getAllInputFiles(transformInvocation.getInputs()),
                    getSourceFiles(),
                    proguardFiles,
                    mappingFile,
                    jarJarRuleFiles,
                    incrementalDir,
                    javaResourcesFolder,
                    sourceCompatibility,
                    multiDexEnabled,
                    minSdkVersion);
        } else {
            // no incremental support through command line so far.
            androidBuilder.convertByteCodeWithJack(
                    outDirectory,
                    outputJackFileForTestCompilation,
                    computeClasspath(getAllInputFiles(transformInvocation.getReferencedInputs())),
                    getAllInputFiles(transformInvocation.getInputs()),
                    computeEcjOptionFile(),
                    proguardFiles,
                    mappingFile,
                    jarJarRuleFiles,
                    sourceCompatibility,
                    multiDexEnabled,
                    minSdkVersion,
                    isDebugLog,
                    javaMaxHeapSize,
                    new LoggedProcessOutputHandler(androidBuilder.getLogger()));
        }
    }

    private static List<File> getAllInputFiles(Collection<TransformInput> transformInputs) {
        List<File> inputFiles = Lists.newArrayList();
        for (TransformInput input : transformInputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                inputFiles.add(directoryInput.getFile());
            }
            for (JarInput jarInput : input.getJarInputs()) {
                inputFiles.add(jarInput.getFile());
            }
        }
        return inputFiles;
    }

    @Nullable
    private File computeEcjOptionFile() throws IOException {
        if (getSourceFiles().isEmpty()) {
            return null;
        }

        FileUtils.mkdirs(ecjOptionsFile.getParentFile());

        StringBuilder sb = new StringBuilder();

        for (File sourceFile : getSourceFiles()) {
            sb.append('\"').append(sourceFile.getAbsolutePath()).append('\"').append("\n");
        }

        Files.write(sb.toString(), ecjOptionsFile, Charsets.UTF_8);

        return ecjOptionsFile;
    }

    private static String computeClasspath(Iterable<File> files) {
        return Joiner.on(':').join(
                Iterables.transform(files, FileUtils.GET_ABSOLUTE_PATH));
    }

    private Collection<File> getSourceFiles() {
        List<File> sourceFiles = Lists.newArrayList();
        for (ConfigurableFileTree fileTree : sources) {
            sourceFiles.addAll(fileTree.getFiles());
        }
        return sourceFiles;
    }

    public File getMappingFile() {
        return mappingFile;
    }

    public JackTransform(
            final VariantScope scope,
            final boolean isDebugLog,
            final boolean compileJavaSources) {

        this.isDebugLog = isDebugLog;
        GlobalScope globalScope = scope.getGlobalScope();

        androidBuilder = globalScope.getAndroidBuilder();
        javaMaxHeapSize = globalScope.getExtension().getDexOptions().getJavaMaxHeapSize();

        Project project = globalScope.getProject();
        if (compileJavaSources) {
            sources.addAll(scope.getVariantData().getJavaSources());
            if (scope.getVariantData().getExtraGeneratedSourceFolders() != null) {
                for (File extraSourceFolder : scope.getVariantData()
                        .getExtraGeneratedSourceFolders()) {
                    sources.add(project.fileTree(extraSourceFolder));
                }
            }
        }
        final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();
        multiDexEnabled = config.isMultiDexEnabled();
        minSdkVersion = config.getMinSdkVersion().getApiLevel();
        incrementalDir = scope.getIncrementalDir(getName());
        jackInProcess = config.getJackOptions().isJackInProcess();

        outputJackFileForTestCompilation = scope.getJackClassesZip();
        ecjOptionsFile = scope.getJackEcjOptionsFile();

        javaResourcesFolder = scope.getJavaResourcesDestinationDir();

        if (config.isMinifyEnabled()) {
            // since all the output use the same resources, we can use the first output
            // to query for a proguard file.
            File sdkDir = scope.getGlobalScope().getSdkHandler().getAndCheckSdkFolder();
            checkNotNull(sdkDir);
            File defaultProguardFile = FileUtils.join(sdkDir,
                    SdkConstants.FD_TOOLS,
                    SdkConstants.FD_PROGUARD,
                    TaskManager.DEFAULT_PROGUARD_CONFIG_FILE);

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
            this.proguardFiles = proguardFiles;
            mappingFile = new File(scope.getProguardOutputFolder(), "mapping.txt");
        }

        jarJarRuleFiles = Lists.newArrayListWithCapacity(
                config.getJarJarRuleFiles().size());
        for (File file: config.getJarJarRuleFiles()) {
            jarJarRuleFiles.add(project.file(file));
        }

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();
        AbstractCompilesUtil.setDefaultJavaVersion(
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                true /*jackEnabled*/);
        sourceCompatibility = compileOptions.getSourceCompatibility().toString();
    }
}
