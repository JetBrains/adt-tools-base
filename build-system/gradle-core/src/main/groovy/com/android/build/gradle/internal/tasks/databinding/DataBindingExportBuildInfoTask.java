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

import com.android.SdkConstants;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.google.common.base.CharMatcher;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

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

    private File dataBindingClassOutput;

    @TaskAction
    public void exportInfo(IncrementalTaskInputs inputs) {
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

    @InputFiles
    public FileCollection getCompilerClasspath() {
        return null;
    }

    @InputFiles
    public Iterable<ConfigurableFileTree> getCompilerSources() {
        return null;
    }

    @Input
    public File getSdkDir() {
        return sdkDir;
    }

    public void setSdkDir(File sdkDir) {
        this.sdkDir = sdkDir;
    }

    @InputDirectory // output of the process layouts task
    public File getXmlOutFolder() {
        return xmlOutFolder;
    }

    public void setXmlOutFolder(File xmlOutFolder) {
        this.xmlOutFolder = xmlOutFolder;
    }

    @Input
    @Optional
    public File getExportClassListTo() {
        return exportClassListTo;
    }

    public void setExportClassListTo(File exportClassListTo) {
        this.exportClassListTo = exportClassListTo;
    }

    @Input
    public boolean isPrintMachineReadableErrors() {
        return printMachineReadableErrors;
    }

    public void setPrintMachineReadableErrors(boolean printMachineReadableErrors) {
        this.printMachineReadableErrors = printMachineReadableErrors;
    }

    @OutputDirectory
    public File getOutput() {
        return dataBindingClassOutput;
    }

    public void setDataBindingClassOutput(File dataBindingClassOutput) {
        this.dataBindingClassOutput = dataBindingClassOutput;
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
            final BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope
                    .getVariantData();
            task.setXmlProcessor(variantData.getLayoutXmlProcessor());
            task.setSdkDir(variantScope.getGlobalScope().getSdkHandler().getSdkFolder());
            task.setXmlOutFolder(variantScope.getLayoutInfoOutputForDataBinding());

            ConventionMappingHelper.map(task, "compilerClasspath", new Callable<FileCollection>() {
                @Override
                public FileCollection call() {
                    return variantScope.getJavaClasspath();
                }
            });
            ConventionMappingHelper
                    .map(task, "compilerSources", new Callable<Iterable<ConfigurableFileTree>>() {
                        @Override
                        public Iterable<ConfigurableFileTree> call() throws Exception {
                            return Iterables.filter(variantData.getJavaSources(),
                                    new Predicate<ConfigurableFileTree>() {
                                        @Override
                                        public boolean apply(ConfigurableFileTree input) {
                                            File dataBindingOut = variantScope
                                                    .getClassOutputForDataBinding();
                                            return !dataBindingOut.equals(input.getDir());
                                        }
                                    });
                        }
                    });

            task.setExportClassListTo(variantData.getType().isExportDataBindingClassList() ?
                    variantScope.getGeneratedClassListOutputFileForDataBinding() : null);
            task.setPrintMachineReadableErrors(printMachineReadableErrors);
            task.setDataBindingClassOutput(variantScope.getClassOutputForDataBinding());
        }
    }
}