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

package com.android.build.gradle.internal.tasks.databinding;

import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;

import java.io.File;

/**
 * This task creates a class which includes the build environment information, which is needed for
 * the annotation processor.
 */
public class DataBindingExportBuildInfoTask extends DefaultTask {
    private LayoutXmlProcessor xmlProcessor;
    private File sdkDir;
    private File xmlOutFolder;
    private File exportClassListTo;
    private boolean printMachineReadableErrors;

    @TaskAction
    public void exportInfo() {
        xmlProcessor.writeInfoClass(getSdkDir(), getXmlOutFolder(), getExportClassListTo(),
                getLogger().isDebugEnabled(), isPrintMachineReadableErrors());
        Scope.assertNoError();
    }

    public LayoutXmlProcessor getXmlProcessor() {
        return xmlProcessor;
    }

    public void setXmlProcessor(LayoutXmlProcessor xmlProcessor) {
        this.xmlProcessor = xmlProcessor;
    }

    public File getSdkDir() {
        return sdkDir;
    }

    public void setSdkDir(File sdkDir) {
        this.sdkDir = sdkDir;
    }

    public File getXmlOutFolder() {
        return xmlOutFolder;
    }

    public void setXmlOutFolder(File xmlOutFolder) {
        this.xmlOutFolder = xmlOutFolder;
    }

    public File getExportClassListTo() {
        return exportClassListTo;
    }

    public void setExportClassListTo(File exportClassListTo) {
        this.exportClassListTo = exportClassListTo;
    }

    public boolean isPrintMachineReadableErrors() {
        return printMachineReadableErrors;
    }

    public void setPrintMachineReadableErrors(boolean printMachineReadableErrors) {
        this.printMachineReadableErrors = printMachineReadableErrors;
    }

    public static class ConfigAction implements TaskConfigAction<DataBindingExportBuildInfoTask> {
        private final VariantScope variantScope;
        private final boolean printMachineReadableErrors;

        public ConfigAction(VariantScope variantScope, boolean printMachineReadableErrors) {
            this.variantScope = variantScope;
            this.printMachineReadableErrors = printMachineReadableErrors;
        }

        @Override
        public String getName() {
            return variantScope.getTaskName("dataBindingExportBuildInfo");
        }

        @Override
        public Class<DataBindingExportBuildInfoTask> getType() {
            return DataBindingExportBuildInfoTask.class;
        }

        @Override
        public void execute(DataBindingExportBuildInfoTask task) {
            BaseVariantData variantData = variantScope.getVariantData();
            task.setXmlProcessor(variantData.getLayoutXmlProcessor());
            task.setSdkDir(variantScope.getGlobalScope().getSdkHandler().getSdkFolder());
            task.setXmlOutFolder(variantScope.getLayoutInfoOutputForDataBinding());

            task.setExportClassListTo(variantData.getType().isExportDataBindingClassList() ?
                    variantScope.getGeneratedClassListOutputFileForDataBinding() : null);
            task.setPrintMachineReadableErrors(printMachineReadableErrors);
            //variantData.addJavaSourceFoldersToModel(variantScope.getClassOutputForDataBinding());
            variantData.registerJavaGeneratingTask(task,
                    variantScope.getClassOutputForDataBinding());
        }
    }
}