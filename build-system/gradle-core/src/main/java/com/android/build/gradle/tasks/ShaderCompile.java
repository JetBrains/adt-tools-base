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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Task to compile Shaders
 */
@ParallelizableTask
public class ShaderCompile extends BaseTask {

    private static final PatternSet PATTERN_SET = new PatternSet()
            .include("**/*." + ShaderProcessor.EXT_VERT)
            .include("**/*." + ShaderProcessor.EXT_TESC)
            .include("**/*." + ShaderProcessor.EXT_TESE)
            .include("**/*." + ShaderProcessor.EXT_GEOM)
            .include("**/*." + ShaderProcessor.EXT_FRAG)
            .include("**/*." + ShaderProcessor.EXT_COMP);

    // ----- PUBLIC TASK API -----

    // ----- PRIVATE TASK API -----
    private File outputDir;

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    private File sourceDir;

    @NonNull
    private List<String> defaultArgs = ImmutableList.of();
    private Map<String, List<String>> scopedArgs = ImmutableMap.of();

    private File ndkLocation;

    @InputFiles
    FileTree getSourceFiles() {
        FileTree src = null;
        File sourceDir = getSourceDir();
        if (sourceDir.isDirectory()) {
            src = getProject().files(sourceDir).getAsFileTree().matching(PATTERN_SET);
        }
        return src == null ? getProject().files().getAsFileTree() : src;
    }

    @TaskAction
    protected void compileShaders() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        try {
            getBuilder().compileAllShaderFiles(
                    getSourceDir(),
                    getOutputDir(),
                    defaultArgs,
                    scopedArgs,
                    ndkLocation,
                    new LoggedProcessOutputHandler(getILogger()));
        } catch (Exception e) {
            throw  new RuntimeException(e);
        }
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File sourceOutputDir) {
        this.outputDir = sourceOutputDir;
    }

    public File getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(File sourceDir) {
        this.sourceDir = sourceDir;
    }

    @NonNull
    @Input
    public List<String> getDefaultArgs() {
        return defaultArgs;
    }

    public void setDefaultArgs(@NonNull List<String> defaultArgs) {
        this.defaultArgs = ImmutableList.copyOf(defaultArgs);
    }

    @NonNull
    @Input
    public Map<String, List<String>> getScopedArgs() {
        return scopedArgs;
    }

    public void setScopedArgs(@NonNull Map<String, List<String>> scopedArgs) {
        this.scopedArgs = ImmutableMap.copyOf(scopedArgs);
    }

    public static class ConfigAction implements TaskConfigAction<ShaderCompile> {

        @NonNull
        VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("compile", "Shaders");
        }

        @Override
        @NonNull
        public Class<ShaderCompile> getType() {
            return ShaderCompile.class;
        }

        @Override
        public void execute(@NonNull ShaderCompile compileTask) {
            final GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();

            scope.getVariantData().shaderCompileTask = compileTask;

            compileTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            compileTask.setVariantName(variantConfiguration.getFullName());

            compileTask.ndkLocation = scope.getGlobalScope().getNdkHandler().getNdkDirectory();

            compileTask.setSourceDir(scope.getMergeShadersOutputDir());
            compileTask.setOutputDir(scope.getShadersOutputDir());
            compileTask.setDefaultArgs(variantConfiguration.getDefautGlslcArgs());
            compileTask.setScopedArgs(variantConfiguration.getScopedGlslcArgs());
        }
    }
}
