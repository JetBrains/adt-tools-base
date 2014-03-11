/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.DefaultBuildType;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.model.SourceProvider;

import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A Build variant and all its public data. This is the base class for items common to apps,
 * test apps, and libraries
 */
public interface BaseVariant {

    /**
     * Returns the name of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getName();

    /**
     * Returns a description for the build variant.
     */
    @NonNull
    String getDescription();

    /**
     * Returns a subfolder name for the variant. Guaranteed to be unique.
     *
     * This is usually a mix of build type and flavor(s) (if applicable).
     * For instance this could be:
     * "debug"
     * "debug/myflavor"
     * "release/Flavor1Flavor2"
     */
    @NonNull
    String getDirName();

    /**
     * Returns the base name for the output of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getBaseName();

    /**
     * Returns the flavor name of the variant. This is a concatenation of all the
     * applied flavors
     * @return the name of the flavors, or an empty string if there is not flavors.
     */
    @NonNull
    String getFlavorName();

    /**
     * Returns the {@link com.android.builder.DefaultBuildType} for this build variant.
     */
    @NonNull
    DefaultBuildType getBuildType();

    /**
     * Returns a {@link com.android.builder.DefaultProductFlavor} that represents the merging
     * of the default config and the flavors of this build variant.
     */
    @NonNull
    DefaultProductFlavor getMergedFlavor();

    /**
     * Returns a list of sorted SourceProvider in order of ascending order, meaning, the earlier
     * items are meant to be overridden by later items.
     *
     * @return a list of source provider
     */
    @NonNull
    List<SourceProvider> getSourceSets();

    /**
     * Returns the output file for this build variants. Depending on the configuration, this could
     * be an apk (regular and test project) or a bundled library (library project).
     *
     * If it's an apk, it could be signed, or not; zip-aligned, or not.
     */
    @NonNull
    File getOutputFile();

    void setOutputFile(@NonNull File outputFile);

    /**
     * Returns the pre-build anchor task
     */
    @NonNull
    Task getPreBuild();

    /**
     * Returns the check manifest task.
     */
    @NonNull
    Task getCheckManifest();

    /**
     * Returns the Manifest processing task.
     */
    @NonNull
    ProcessManifest getProcessManifest();

    /**
     * Returns the AIDL compilation task.
     */
    @NonNull
    AidlCompile getAidlCompile();

    /**
     * Returns the Renderscript compilation task.
     */
    @NonNull
    RenderscriptCompile getRenderscriptCompile();

    /**
     * Returns the resource merging task.
     */
    @Nullable
    MergeResources getMergeResources();

    /**
     * Returns the asset merging task.
     */
    @Nullable
    MergeAssets getMergeAssets();

    /**
     * Returns the Android Resources processing task.
     */
    @NonNull
    ProcessAndroidResources getProcessResources();

    /**
     * Returns the BuildConfig generation task.
     */
    @Nullable
    GenerateBuildConfig getGenerateBuildConfig();

    /**
     * Returns the Java Compilation task.
     */
    @NonNull
    JavaCompile getJavaCompile();

    @NonNull
    NdkCompile getNdkCompile();

    /**
     * Returns the Java resource processing task.
     */
    @NonNull
    Copy getProcessJavaResources();

    /**
     * Returns the assemble task.
     */
    @Nullable
    Task getAssemble();

    /**
     * Adds new Java source folders to the model.
     *
     * These source folders will not be used for the default build
     * system, but will be passed along the default Java source folders
     * to whoever queries the model.
     *
     * @param sourceFolders the source folders where the generated source code is.
     */
    void addJavaSourceFoldersToModel(@NonNull File... sourceFolders);

    /**
     * Adds new Java source folders to the model.
     *
     * These source folders will not be used for the default build
     * system, but will be passed along the default Java source folders
     * to whoever queries the model.
     *
     * @param sourceFolders the source folders where the generated source code is.
     */
    void addJavaSourceFoldersToModel(@NonNull Collection<File> sourceFolders);

    /**
     * Adds to the variant a task that generates Java source code.
     *
     * This will make the compileJava task depend on this task and add the
     * new source folders as compilation inputs.
     *
     * The new source folders are also added to the model.
     *
     * @param task the task
     * @param sourceFolders the source folders where the generated source code is.
     */
    void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders);

    /**
     * Adds to the variant a task that generates Java source code.
     *
     * This will make the compileJava task depend on this task and add the
     * new source folders as compilation inputs.
     *
     * The new source folders are also added to the model.
     *
     * @param task the task
     * @param sourceFolders the source folders where the generated source code is.
     */
    void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> sourceFolders);
}
