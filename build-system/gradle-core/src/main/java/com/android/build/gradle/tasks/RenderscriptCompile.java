/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NdkTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Task to compile Renderscript files. Supports incremental update.
 */
@ParallelizableTask
public class RenderscriptCompile extends NdkTask {

    // ----- PUBLIC TASK API -----

    private File sourceOutputDir;

    private File resOutputDir;

    private File objOutputDir;

    private File libOutputDir;


    // ----- PRIVATE TASK API -----

    private List<File> sourceDirs;

    private List<File> importDirs;

    private Integer targetApi;

    private boolean supportMode;

    private int optimLevel;

    private boolean debugBuild;

    private boolean ndkMode;

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @OutputDirectory
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @OutputDirectory
    public File getResOutputDir() {
        return resOutputDir;
    }

    public void setResOutputDir(File resOutputDir) {
        this.resOutputDir = resOutputDir;
    }

    @OutputDirectory
    public File getObjOutputDir() {
        return objOutputDir;
    }

    public void setObjOutputDir(File objOutputDir) {
        this.objOutputDir = objOutputDir;
    }

    @OutputDirectory
    public File getLibOutputDir() {
        return libOutputDir;
    }

    public void setLibOutputDir(File libOutputDir) {
        this.libOutputDir = libOutputDir;
    }

    @InputFiles
    public List<File> getSourceDirs() {
        return sourceDirs;
    }

    public void setSourceDirs(List<File> sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    @InputFiles
    public List<File> getImportDirs() {
        return importDirs;
    }

    public void setImportDirs(List<File> importDirs) {
        this.importDirs = importDirs;
    }

    @Input
    public Integer getTargetApi() {
        return targetApi;
    }

    public void setTargetApi(Integer targetApi) {
        this.targetApi = targetApi;
    }

    @Input
    public boolean isSupportMode() {
        return supportMode;
    }

    public void setSupportMode(boolean supportMode) {
        this.supportMode = supportMode;
    }

    @Input
    public int getOptimLevel() {
        return optimLevel;
    }

    public void setOptimLevel(int optimLevel) {
        this.optimLevel = optimLevel;
    }

    @Input
    public boolean isDebugBuild() {
        return debugBuild;
    }

    public void setDebugBuild(boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    @Input
    public boolean isNdkMode() {
        return ndkMode;
    }

    public void setNdkMode(boolean ndkMode) {
        this.ndkMode = ndkMode;
    }

    @TaskAction
    void taskAction()
            throws IOException, InterruptedException, ProcessException, LoggedErrorException {
        // this is full run (always), clean the previous outputs
        File sourceDestDir = getSourceOutputDir();
        FileUtils.emptyFolder(sourceDestDir);

        File resDestDir = getResOutputDir();
        FileUtils.emptyFolder(resDestDir);

        File objDestDir = getObjOutputDir();
        FileUtils.emptyFolder(objDestDir);

        File libDestDir = getLibOutputDir();
        FileUtils.emptyFolder(libDestDir);

        // get the import folders. If the .rsh files are not directly under the import folders,
        // we need to get the leaf folders, as this is what llvm-rs-cc expects.
        List<File> importFolders = AndroidBuilder.getLeafFolders("rsh",
                getImportDirs(), getSourceDirs());

        getBuilder().compileAllRenderscriptFiles(
                getSourceDirs(),
                importFolders,
                sourceDestDir,
                resDestDir,
                objDestDir,
                libDestDir,
                getTargetApi(),
                isDebugBuild(),
                getOptimLevel(),
                isNdkMode(),
                isSupportMode(),
                getNdkConfig() == null ? null : getNdkConfig().getAbiFilters(),
                new LoggedProcessOutputHandler(getILogger()));
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<RenderscriptCompile> {

        @NonNull
        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("compile", "Renderscript");
        }

        @NonNull
        @Override
        public Class<RenderscriptCompile> getType() {
            return RenderscriptCompile.class;
        }

        @Override
        public void execute(@NonNull RenderscriptCompile renderscriptTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            variantData.renderscriptCompileTask = renderscriptTask;
            boolean ndkMode = config.getRenderscriptNdkModeEnabled();
            renderscriptTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            renderscriptTask.setVariantName(config.getFullName());

            ConventionMappingHelper.map(renderscriptTask, "targetApi",
                    (Callable<Integer>) config::getRenderscriptTarget);

            renderscriptTask.supportMode = config.getRenderscriptSupportModeEnabled();
            renderscriptTask.ndkMode = ndkMode;
            renderscriptTask.debugBuild = config.getBuildType().isRenderscriptDebuggable();
            renderscriptTask.optimLevel = config.getBuildType().getRenderscriptOptimLevel();

            ConventionMappingHelper.map(renderscriptTask, "sourceDirs",
                    (Callable<List<File>>) config::getRenderscriptSourceList);
            ConventionMappingHelper.map(renderscriptTask, "importDirs",
                    (Callable<List<File>>) config::getRenderscriptImports);

            renderscriptTask.setSourceOutputDir(scope.getRenderscriptSourceOutputDir());
            renderscriptTask.setResOutputDir(scope.getRenderscriptResOutputDir());
            renderscriptTask.setObjOutputDir(scope.getRenderscriptObjOutputDir());
            renderscriptTask.setLibOutputDir(scope.getRenderscriptLibOutputDir());

            ConventionMappingHelper.map(renderscriptTask, "ndkConfig",
                    (Callable<CoreNdkOptions>) config::getNdkConfig);
        }
    }
}
