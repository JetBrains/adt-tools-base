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

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;
import android.databinding.tool.util.L;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

/**
 * Parses xml files and generates metadata. Will be removed when aapt supports binding tags.
 */
public class DataBindingProcessLayoutsTask extends DefaultTask {

    private LayoutXmlProcessor xmlProcessor;

    private File sdkDir;

    private int minSdk;

    private File layoutInputFolder;

    private File layoutOutputFolder;

    private File xmlInfoOutFolder;

    @InputDirectory
    public File getLayoutInputFolder() {
        return layoutInputFolder;
    }

    public void setLayoutInputFolder(File layoutInputFolder) {
        this.layoutInputFolder = layoutInputFolder;
    }

    @OutputDirectory
    public File getLayoutOutputFolder() {
        return layoutOutputFolder;
    }

    public void setLayoutOutputFolder(File layoutOutputFolder) {
        this.layoutOutputFolder = layoutOutputFolder;
    }

    @OutputDirectory
    public File getXmlInfoOutFolder() {
        return xmlInfoOutFolder;
    }

    @TaskAction
    public void processResources(IncrementalTaskInputs incrementalTaskInputs)
            throws ParserConfigurationException, SAXException, XPathExpressionException,
            IOException, JAXBException {
        final LayoutXmlProcessor.ResourceInput resourceInput =
                new LayoutXmlProcessor.ResourceInput(incrementalTaskInputs.isIncremental(),
                        getLayoutInputFolder(), getLayoutOutputFolder());
        if (incrementalTaskInputs.isIncremental()) {
            incrementalTaskInputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    if (inputFileDetails.isAdded()) {
                        resourceInput.added(inputFileDetails.getFile());
                    } else if (inputFileDetails.isModified()) {
                        resourceInput.changed(inputFileDetails.getFile());
                    }
                }
            });
            incrementalTaskInputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    resourceInput.removed(inputFileDetails.getFile());
                }
            });
        }
        xmlProcessor.processResources(resourceInput);
        Scope.assertNoError();
        xmlProcessor.writeLayoutInfoFiles(getXmlInfoOutFolder());
        Scope.assertNoError();
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

    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public void setXmlInfoOutFolder(File xmlInfoOutFolder) {
        this.xmlInfoOutFolder = xmlInfoOutFolder;
    }

    public static class ConfigAction implements TaskConfigAction<DataBindingProcessLayoutsTask> {

        private final VariantScope variantScope;

        public ConfigAction(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @Override
        public String getName() {
            return variantScope.getTaskName("dataBindingProcessLayouts");
        }

        @Override
        public Class<DataBindingProcessLayoutsTask> getType() {
            return DataBindingProcessLayoutsTask.class;
        }

        @Override
        public void execute(DataBindingProcessLayoutsTask task) {
            task.setXmlProcessor(variantScope.getVariantData().getLayoutXmlProcessor());
            task.setSdkDir(variantScope.getGlobalScope().getSdkHandler().getSdkFolder());
            task.setMinSdk(variantScope.getVariantConfiguration().getMinSdkVersion().getApiLevel());
            task.setLayoutInputFolder(variantScope.getMergeResourcesOutputDir());
            task.setLayoutOutputFolder(variantScope.getLayoutFolderOutputForDataBinding());
            task.setXmlInfoOutFolder(variantScope.getLayoutInfoOutputForDataBinding());
            variantScope.setResourceOutputDir(variantScope.getLayoutFolderOutputForDataBinding());
        }
    }
}
