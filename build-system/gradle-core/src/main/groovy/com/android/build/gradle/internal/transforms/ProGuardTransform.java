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

import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.tasks.SimpleWorkQueue;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import proguard.ClassPath;

/**
 * ProGuard support as a transform
 */
public class ProGuardTransform extends BaseProguardAction implements CombinedTransform {

    private final VariantScope variantScope;
    private final boolean asJar;

    private final boolean isLibrary;
    private final boolean isTest;

    private final File proguardOut;

    private final File printMapping;
    private final File dump;
    private final File printSeeds;
    private final File printUsage;
    private final ImmutableList<File> secondaryFileOutputs;

    private File testedMappingFile = null;
    private org.gradle.api.artifacts.Configuration testMappingConfiguration = null;

    public ProGuardTransform(
            @NonNull VariantScope variantScope,
            boolean asJar) {
        this.variantScope = variantScope;
        this.asJar = asJar;
        isLibrary = variantScope.getVariantData() instanceof LibraryVariantData;
        isTest = variantScope.getTestedVariantData() != null;

        GlobalScope globalScope = variantScope.getGlobalScope();
        proguardOut = new File(Joiner.on(File.separatorChar).join(
                String.valueOf(globalScope.getBuildDir()),
                FD_OUTPUTS,
                "mapping",
                variantScope.getVariantConfiguration().getDirName()));

        printMapping = new File(proguardOut, "mapping.txt");
        dump = new File(proguardOut, "dump.txt");
        printSeeds = new File(proguardOut, "seeds.txt");
        printUsage = new File(proguardOut, "usage.txt");
        secondaryFileOutputs = ImmutableList.of(printMapping, dump, printSeeds, printUsage);
    }

    @Nullable
    public File getMappingFile() {
        return printMapping;
    }

    public void applyTestedMapping(@Nullable File testedMappingFile) {
        this.testedMappingFile = testedMappingFile;
    }

    public void applyTestedMapping(
            @Nullable org.gradle.api.artifacts.Configuration testMappingConfiguration) {
        this.testMappingConfiguration = testMappingConfiguration;
    }

    @NonNull
    @Override
    public String getName() {
        return "proguard";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        if (isLibrary) {
            return Sets.immutableEnumSet(Scope.PROJECT, Scope.PROJECT_LOCAL_DEPS);
        }

        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        Set<Scope> set = Sets.newLinkedHashSetWithExpectedSize(5);
        if (isLibrary) {
            set.add(Scope.SUB_PROJECTS);
            set.add(Scope.SUB_PROJECTS_LOCAL_DEPS);
            set.add(Scope.EXTERNAL_LIBRARIES);
        }

        if (isTest) {
            set.add(Scope.TESTED_CODE);
        }

        set.add(Scope.PROVIDED_ONLY);

        return Sets.immutableEnumSet(set);
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.COMBINED;
    }

    @NonNull
    @Override
    public Format getOutputFormat() {
        if (asJar) {
            return Format.JAR;
        }

        return Format.SINGLE_FOLDER;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        final List<File> files = Lists.newArrayList();

        // the mapping file.
        File testedMappingFile = computeMappingFile();
        if (testedMappingFile != null) {
            files.add(testedMappingFile);
        }

        // the config files
        files.addAll(getAllConfigurationFiles());

        return files;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return secondaryFileOutputs;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(
            @NonNull final Collection<TransformInput> inputs,
            @NonNull final Collection<TransformInput> referencedInputs,
            @NonNull final TransformOutput combinedOutput,
            boolean isIncremental) throws TransformException {
        // only run one minification at a time (across projects)
        final Job<Void> job = new Job<Void>(getName(),
                new com.android.builder.tasks.Task<Void>() {
                    @Override
                    public void run(@NonNull Job<Void> job,
                            @NonNull JobContext<Void> context) throws IOException {
                        doMinification(inputs, referencedInputs, combinedOutput);
                    }
                });
        try {
            SimpleWorkQueue.push(job);

            // wait for the task completion.
            if (!job.awaitRethrowExceptions()) {
                throw new RuntimeException("Job failed, see logs for details");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void doMinification(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutput combinedOutput) throws IOException {
        // all the output will be the same since the transform type is COMBINED.
        checkNotNull(combinedOutput, "Found no output in transform with Type=COMBINED");
        File outFile = combinedOutput.getOutFile();

        try {
            GlobalScope globalScope = variantScope.getGlobalScope();

            // set the mapping file if there is one.
            File testedMappingFile = computeMappingFile();
            if (testedMappingFile != null) {
                applyMapping(testedMappingFile);
            }

            // --- InJars / LibraryJars ---
            addInputsToConfiguration(inputs, false);
            addInputsToConfiguration(referencedInputs, true);

            // libraryJars: the runtime jars, with all optional libraries.
            for (File runtimeJar : globalScope.getAndroidBuilder().getBootClasspath(true)) {
                libraryJar(runtimeJar);
            }

            // --- Out files ---
            outJar(outFile);

            if (asJar) {
                mkdirs(outFile.getParentFile());
            } else {
                mkdirs(outFile);
            }

            // proguard doesn't verify that the seed/mapping/usage folders exist and will fail
            // if they don't so create them.
            mkdirs(proguardOut);

            for (File configFile : getAllConfigurationFiles()) {
                applyConfigurationFile(configFile);
            }

            configuration.printMapping = printMapping;
            configuration.dump = dump;
            configuration.printSeeds = printSeeds;
            configuration.printUsage = printUsage;

            forceprocessing();
            runProguard();

        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException(e);
        }
    }

    private void addInputsToConfiguration(
            @NonNull Collection<TransformInput> streamList, boolean referencedOnly) {
        ClassPath classPath;
        List<String> baseFilter;

        if (referencedOnly) {
            classPath = configuration.libraryJars;
            baseFilter = JAR_FILTER;
        } else {
            classPath = configuration.programJars;
            baseFilter = null;
        }

        for (TransformInput transformInput : streamList) {
            List<String> filter = baseFilter;
            if (!transformInput.getContentTypes().contains(ContentType.CLASSES)) {
                ImmutableList.Builder<String> builder = ImmutableList.builder();
                if (filter != null) {
                    builder.addAll(filter);
                }
                builder.add("!**/*.class");
                filter = builder.build();
            }

            switch (transformInput.getFormat()) {
                case SINGLE_FOLDER:
                case JAR:
                    for (File file : transformInput.getFiles()) {
                        inputJar(classPath, file, filter);
                    }
                    break;
                case MULTI_FOLDER:
                    for (File file : transformInput.getFiles()) {
                        File[] subStreams = file.listFiles();
                        if (subStreams != null) {
                            for (File subStream : subStreams) {
                                inputJar(classPath, subStream, filter);
                            }
                        }
                    }
                    break;
                default:
                    throw new RuntimeException("Unsupported ScopedContent.Format value: " + transformInput.getFormat().name());
            }
        }
    }

    @Nullable
    private File computeMappingFile() {
        if (testedMappingFile != null && testedMappingFile.isFile()) {
            return testedMappingFile;
        } else if (testMappingConfiguration != null && testMappingConfiguration.getSingleFile().isFile()) {
            return testMappingConfiguration.getSingleFile();
        }

        return null;
    }
}
