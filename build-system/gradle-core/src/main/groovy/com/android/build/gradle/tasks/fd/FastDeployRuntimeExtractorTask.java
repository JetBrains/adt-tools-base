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

import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * Task to extract the FastDeploy runtime from the gradle-core jar file into a folder to be picked
 * up for co-packaging in the resulting application APK.
 */
public class FastDeployRuntimeExtractorTask extends DefaultAndroidTask {

    private File outputDir;

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File destDir) {
        this.outputDir = destDir;
    }


    @TaskAction
    public void extract() throws IOException {
        extractLibrary(getOutputDir());
    }

    static void extractLibrary(@NonNull File destDir) throws IOException {
        URL fdrJar = FastDeployRuntimeExtractorTask.class.getResource("/fdr/classes.jar");
        if (fdrJar == null) {
            System.err.println("Couldn't find embedded Fast Deployment runtime library");
            return;
        }
        InputStream inputStream = fdrJar.openStream();
        try {
            JarInputStream jarInputStream =
                    new JarInputStream(inputStream);
            try {
                ZipEntry entry = jarInputStream.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    if (name.startsWith("META-INF")) {
                        continue;
                    }
                    File dest = new File(destDir, name.replace('/', separatorChar));
                    if (entry.isDirectory()) {
                        if (!dest.exists()) {
                            boolean created = dest.mkdirs();
                            if (!created) {
                                throw new IOException(dest.getPath());
                            }
                        }
                    } else {
                        byte[] bytes = ByteStreams.toByteArray(jarInputStream);
                        Files.write(bytes, dest);
                    }
                    entry = jarInputStream.getNextEntry();
                }
            } finally {
                jarInputStream.close();
            }
        } finally {
            inputStream.close();
        }
    }

    public static class ConfigAction implements TaskConfigAction<FastDeployRuntimeExtractorTask> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @Override
        public String getName() {
            return scope.getTaskName("fastDeploy", "Extractor");
        }

        @Override
        public Class<FastDeployRuntimeExtractorTask> getType() {
            return FastDeployRuntimeExtractorTask.class;
        }

        @Override
        public void execute(FastDeployRuntimeExtractorTask fastDeployRuntimeExtractorTask) {
            fastDeployRuntimeExtractorTask.setVariantName(
                    scope.getVariantConfiguration().getFullName());
            // change this to use a special directory and have the classes.jar use that
            // special directory as input, although this may go away with the pipeline architecture.
            fastDeployRuntimeExtractorTask.setOutputDir(
                    scope.getInitialIncrementalSupportJavaOutputDir());
        }
    }
}
