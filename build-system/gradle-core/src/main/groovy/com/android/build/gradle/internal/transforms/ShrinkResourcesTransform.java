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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.core.AaptPackageProcessBuilder;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Implementation of Resource Shrinking as a transform.
 *
 * Since this transform only reads the data from the stream but does not output anything
 * back into the stream, it is a no-op transform, asking only for referenced scopes, and not
 * "consumed" scopes.
 */
public class ShrinkResourcesTransform extends Transform {

    /** Whether we've already warned about how to turn off shrinking. Used to avoid
     * repeating the same multi-line message for every repeated abi split. */
    private static boolean ourWarned;

    /**
     * Associated variant data that the strip task will be run against. Used to locate
     * not only locations the task needs (e.g. for resources and generated R classes)
     * but also to obtain the resource merging task, since we will run it a second time
     * here to generate a new .ap_ file with fewer resources
     */
    @NonNull
    private final BaseVariantOutputData variantOutputData;
    @NonNull
    private final File uncompressedResources;
    @NonNull
    private final File compressedResources;

    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final Logger logger;

    @NonNull
    private final ImmutableList<File> secondaryInputs;

    private final File sourceDir;
    private final File resourceDir;
    private final File mergedManifest;
    private final File mappingFile;

    public ShrinkResourcesTransform(
            @NonNull BaseVariantOutputData variantOutputData,
            @NonNull File uncompressedResources,
            @NonNull File compressedResources,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull Logger logger) {
        this.variantOutputData = variantOutputData;
        this.uncompressedResources = uncompressedResources;
        this.compressedResources = compressedResources;
        this.androidBuilder = androidBuilder;
        this.logger = logger;

        BaseVariantData<?> variantData = variantOutputData.variantData;
        ProcessAndroidResources processResourcesTask = variantData.generateRClassTask;
        sourceDir = processResourcesTask.getSourceOutputDir();
        resourceDir = variantData.getScope().getFinalResourcesDir();
        mergedManifest = variantOutputData.manifestProcessorTask.getManifestOutputFile();
        mappingFile = variantData.getMappingFile();

        if (mappingFile != null) {
            secondaryInputs = ImmutableList.of(
                    uncompressedResources,
                    sourceDir,
                    resourceDir,
                    mergedManifest,
                    mappingFile);
        } else {
            secondaryInputs = ImmutableList.of(
                    uncompressedResources,
                    sourceDir,
                    resourceDir,
                    mergedManifest);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return "shrinkRes";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return secondaryInputs;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(compressedResources);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {

        // there should be only one input since this transform is always applied after
        // proguard.
        TransformInput input = Iterables.getOnlyElement(referencedInputs);
        File minifiedOutJar = Iterables.getOnlyElement(input.getJarInputs()).getFile();

        BaseVariantData<?> variantData = variantOutputData.variantData;
        ProcessAndroidResources processResourcesTask = variantData.generateRClassTask;
        try {

            // Analyze resources and usages and strip out unused
            ResourceUsageAnalyzer analyzer = new ResourceUsageAnalyzer(
                    sourceDir,
                    minifiedOutJar,
                    mergedManifest,
                    mappingFile,
                    resourceDir);
            analyzer.setVerbose(logger.isEnabled(LogLevel.INFO));
            analyzer.setDebug(logger.isEnabled(LogLevel.DEBUG));
            analyzer.analyze();

            if (ResourceUsageAnalyzer.TWO_PASS_AAPT) {
                // This is currently not working; we need support from aapt to be able
                // to assign a stable set of resources that it should use.
                File destination = new File(resourceDir.getParentFile(), resourceDir.getName() + "-stripped");
                analyzer.removeUnused(destination);

                File sourceOutputs = new File(sourceDir.getParentFile(),
                        sourceDir.getName() + "-stripped");
                FileUtils.mkdirs(sourceOutputs);

                // We don't need to emit R files again, but we can do this here such that
                // we can *verify* that the R classes generated in the second aapt pass
                // matches those we saw the first time around.
                //String sourceOutputPath = sourceOutputs?.getAbsolutePath();
                String sourceOutputPath = null;

                // Repackage the resources:
                AaptPackageProcessBuilder aaptPackageCommandBuilder =
                        new AaptPackageProcessBuilder(
                                mergedManifest,
                                processResourcesTask.getAaptOptions())
                                .setAssetsFolder(processResourcesTask.getAssetsDir())
                                .setResFolder(destination)
                                .setLibraries(processResourcesTask.getLibraries())
                                .setPackageForR(processResourcesTask.getPackageForR())
                                .setSourceOutputDir(sourceOutputPath)
                                .setResPackageOutput(compressedResources.getAbsolutePath())
                                .setType(processResourcesTask.getType())
                                .setDebuggable(processResourcesTask.getDebuggable())
                                .setResourceConfigs(processResourcesTask.getResourceConfigs())
                                .setSplits(processResourcesTask.getSplits());

                androidBuilder.processResources(
                        aaptPackageCommandBuilder,
                        processResourcesTask.getEnforceUniquePackageName(),
                        new LoggedProcessOutputHandler(androidBuilder.getLogger())
                );
            } else {
                // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                analyzer.rewriteResourceZip(uncompressedResources, compressedResources);
            }

            // Dump some stats
            int unused = analyzer.getUnusedResourceCount();
            if (unused > 0) {
                StringBuilder sb = new StringBuilder(200);
                sb.append("Removed unused resources");

                // This is a bit misleading until we can strip out all resource types:
                //int total = analyzer.getTotalResourceCount()
                //sb.append("(" + unused + "/" + total + ")")

                long before = uncompressedResources.length();
                long after = compressedResources.length();
                long percent = (int) ((before - after) * 100 / before);
                sb.append(": Binary resource data reduced from ").
                        append(toKbString(before)).
                        append("KB to ").
                        append(toKbString(after)).
                        append("KB: Removed ").append(percent).append("%");
                if (!ourWarned) {
                    ourWarned = true;
                    sb.append(
                            "\nNote: If necessary, you can disable resource shrinking by adding\n" +
                                    "android {\n" +
                                    "    buildTypes {\n" +
                                    "        " + variantData.getVariantConfiguration().getBuildType().getName() + " {\n" +
                                    "            shrinkResources false\n" +
                                    "        }\n" +
                                    "    }\n" +
                                    "}");
                }

                System.out.println(sb.toString());
            }

        } catch (Exception e) {
            System.out.println("Failed to shrink resources: " + e.toString() + "; ignoring");
            logger.quiet("Failed to shrink resources: ignoring", e);
        }
    }

    private static String toKbString(long size) {
        return Integer.toString((int)size/1024);
    }
}
