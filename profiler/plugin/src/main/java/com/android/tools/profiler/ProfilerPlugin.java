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

package com.android.tools.profiler;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * A gradle plugin which, when applied, instruments the target Android app with support code for
 * helping profile it.
 */
public class ProfilerPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        // TODO: The following line won't work for the experimental plugin. For that we may need to
        // register a rule that will get executed at the right time. Investigate this before
        // shipping the plugin.
        BaseExtension android = (BaseExtension) project.getExtensions().getByName("android");

        android.registerTransform(new Transform() {
            @NonNull
            @Override
            public String getName() {
                return "studioprofiler";
            }

            @NonNull
            @Override
            public Set<QualifiedContent.ContentType> getInputTypes() {
                return TransformManager.CONTENT_CLASS;
            }

            @NonNull
            @Override
            public Set<QualifiedContent.Scope> getScopes() {
                return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
            }

            @Override
            public boolean isIncremental() {
                return true;
            }

            @Override
            public void transform(@NonNull TransformInvocation invocation)
                    throws TransformException, InterruptedException, IOException {

                assert invocation.getOutputProvider() != null;
                File outputDir = invocation.getOutputProvider().getContentLocation(
                        "main", getOutputTypes(), getScopes(), Format.DIRECTORY);
                FileUtils.mkdirs(outputDir);

                for (TransformInput ti : invocation.getInputs()) {
                    Preconditions.checkState(ti.getJarInputs().isEmpty());
                    for (DirectoryInput di : ti.getDirectoryInputs()) {
                        File inputDir = di.getFile();
                        if (invocation.isIncremental()) {
                            for (Map.Entry<File, Status> entry : di.getChangedFiles().entrySet()) {
                                switch (entry.getValue()) {
                                    case ADDED:
                                    case CHANGED:
                                        instrumentFile(outputDir, inputDir, entry.getKey());
                                        break;
                                }
                            }
                        } else {
                            for (File inputFile : FileUtils.getAllFiles(inputDir)) {
                                instrumentFile(outputDir, inputDir, inputFile);
                            }
                        }
                    }
                }
            }

            private void instrumentFile(File outputDir, File inputDir, File inputFile)
                    throws IOException {
                File outputFile = new File(outputDir,
                        FileUtils.relativePath(inputFile, inputDir));
                Files.createParentDirs(outputFile);

                // TODO: This MainActivity check is temporary and here as a proof of concept that
                // instrumenting an Android app works. Later, we'll replace this with logic that
                // inserts a profiler support library as a dependency into the user's app as well as
                // instrument it.
                if (inputFile.getName().equals("MainActivity.class")) {
                    OnCreateInstrumentor.instrument(inputFile, outputFile);
                } else {
                    Files.copy(inputFile, outputFile);
                }
            }
        });

    }
}
