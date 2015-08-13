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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Creates a gradle-based module.
 */
public abstract class GradleModule {

    @NonNull
    private final File moduleDir;
    @NonNull
    private final String gradlePath;
    @NonNull
    private final List<? extends GradleModule> projectDeps;

    @NonNull
    public abstract String getBuildGradleContent();
    public abstract void createFiles() throws IOException;

    protected GradleModule(
            @NonNull File moduleDir,
            @NonNull String gradlePath,
            @NonNull List<? extends GradleModule> projectDeps) {
        this.moduleDir = moduleDir;
        this.gradlePath = gradlePath;
        this.projectDeps = projectDeps;
    }

    public void create() throws IOException {
        boolean mkdirs = moduleDir.mkdirs();
        if (!mkdirs) {
            throw new RuntimeException("Failed to create folder: " + moduleDir);
        }

        setupBuildGradle();
        createFiles();
    }

    @NonNull
    public File getModuleDir() {
        return moduleDir;
    }

    @NonNull
    public String getGradlePath() {
        return gradlePath;
    }

    @NonNull
    public List<? extends GradleModule> getProjectDeps() {
        return projectDeps;
    }

    public void setupBuildGradle() throws IOException {
        String content = getBuildGradleContent();

        content += "dependencies {\n";
        for (GradleModule dep : projectDeps) {
            content += "  compile project('" + dep.getGradlePath() + "')\n";
        }
        content += "}\n";


        Files.write(content, new File(moduleDir, "build.gradle"), Charset.defaultCharset());
    }
}
