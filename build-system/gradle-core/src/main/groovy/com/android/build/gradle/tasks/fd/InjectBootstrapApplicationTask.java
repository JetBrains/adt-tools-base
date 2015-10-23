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

package com.android.build.gradle.tasks.fd;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;

/**
 * Task which injects the bootstrapping application for incremental class and resource
 * deployment.
 */
public class InjectBootstrapApplicationTask extends BaseTask {
    private static final String BOOTSTRAP_APPLICATION = "com.android.tools.fd.runtime.BootstrapApplication";

    private File manifestFile;

    /**
     * Right now, it's rewriting the manifest file in place, without even declaring it as an
     * output, we should rework this asap.
     */
    @InputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @TaskAction
    public void rewrite() throws IOException {
        if (getManifestFile() != null) {
            injectApplication(getManifestFile());
        }
    }

    public static class ConfigAction implements TaskConfigAction<InjectBootstrapApplicationTask> {

        private VariantOutputScope scope;

        public ConfigAction(VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("inject", "Bootstrap");
        }

        @NonNull
        @Override
        public Class<InjectBootstrapApplicationTask> getType() {
            return InjectBootstrapApplicationTask.class;
        }

        @Override
        public void execute(@NonNull InjectBootstrapApplicationTask task) {
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            task.setVariantName(scope.getVariantScope().getVariantConfiguration().getFullName());
            BaseVariantOutputData outputData = scope.getVariantOutputData();
            if (outputData.manifestProcessorTask != null) {
                File manifest = outputData.manifestProcessorTask.getManifestOutputFile();
                task.setManifestFile(manifest);
            }
        }
    }

    public static void injectApplication(@NonNull File merged) throws IOException {
        if (merged.exists()) {
            try {
                Document document = XmlUtils.parseUtfXmlFile(merged, true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node node = children.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE &&
                                node.getNodeName().equals(TAG_APPLICATION)) {
                            Element application = (Element) node;
                            String applicationClass = null;
                            if (application.hasAttributeNS(ANDROID_URI, ATTR_NAME)) {
                                String name = application.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                // Task run twice on the same manifest?
                                if (BOOTSTRAP_APPLICATION.equals(name)) {
                                    return;
                                }
                                assert !name.startsWith(".") : name;
                                if (!name.isEmpty()) {
                                    applicationClass = name;
                                    // Stash for later stage (see comment in
                                    application.setAttribute(ATTR_NAME, name);
                                }
                            }
                            application.setAttributeNS(ANDROID_URI, ATTR_NAME,
                                    BOOTSTRAP_APPLICATION);

                            if (applicationClass != null) {
                                System.out.println("Instrumented " + applicationClass + " with fast deploy");
                            } else {
                                System.out.println("Instrumented app with a fast deploy bootstrapping application");
                            }

                            break;
                        }
                    }

                    // Write the new modified manifest
                    String xml = AndroidBuilder.formatXml(document, false);

                    // TEMPORARY HACK: For some reason, the namespace aware parser seems
                    // to drop our namespace!
                    if (xml.contains(" name=\"com.android.tools.fd.runtime.BootstrapApplication\"")) {
                        xml = xml.replace(" name=\"com.android.tools.fd.runtime.BootstrapApplication\"",
                                " android:name=\"com.android.tools.fd.runtime.BootstrapApplication\"");
                    }

                    Files.write(xml, merged, Charsets.UTF_8);
                }
            } catch (Exception e) {
                throw new GradleException("Exception while patching manifest for InstantRun", e);
            }
        }
    }

}
