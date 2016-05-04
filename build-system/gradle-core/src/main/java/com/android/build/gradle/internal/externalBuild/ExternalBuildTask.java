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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.utils.FileUtils;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Entry point for external third party plugin. Reads the manifest file produced by an external
 * build system and invokes the relevant InstantRun enabled tasks to produce deploy-able artifacts.
 */
public class ExternalBuildTask extends DefaultTask {

    private ExternalBuildExtension externalBuildExtension;
    private File outputDir;

    @Input
    File getBuildManifest() {
        return new File(externalBuildExtension.getBuildManifestPath());
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    private ExternalBuildProcessor manifestProcessor;

    @TaskAction
    public void taskAction() throws IOException {

        FileUtils.mkdirs(outputDir);
        FileUtils.cleanOutputDir(outputDir);

        ExternalBuildApkManifest.ApkManifest manifest;
        File file = getProject().file(getBuildManifest());
        if (!file.exists()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        // read the manifest file
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            manifest = ExternalBuildApkManifest.ApkManifest.parseFrom(is);
        }
        if (manifest != null) {
            manifestProcessor.process(manifest);
        }
    }

    public static final class ConfigAction implements TaskConfigAction<ExternalBuildTask> {

        private final ExternalBuildExtension externalBuildExtension;
        private final File outputDir;
        private final ExternalBuildProcessor processor;

        public ConfigAction(
                @NonNull File outputDir,
                @NonNull ExternalBuildExtension externalBuildExtension,
                @NonNull ExternalBuildProcessor processor) {
            this.outputDir = outputDir;
            this.externalBuildExtension = externalBuildExtension;
            this.processor = processor;
        }

        @NonNull
        @Override
        public String getName() {
            return "process";
        }

        @NonNull
        @Override
        public Class<ExternalBuildTask> getType() {
            return ExternalBuildTask.class;
        }

        @Override
        public void execute(@NonNull ExternalBuildTask task) {

            task.externalBuildExtension = externalBuildExtension;
            task.outputDir = new File(outputDir, "outputs");
            task.manifestProcessor = processor;
        }
    }
}
