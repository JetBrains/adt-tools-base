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
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * a Java Module with no files.
 */
public class JavaGradleModule extends GradleModule implements TestProject{

    public JavaGradleModule(
            @NonNull File moduleDir,
            @NonNull String gradlePath,
            @NonNull List<? extends GradleModule> projectDeps) {
        super(moduleDir, gradlePath, projectDeps);
    }

    @NonNull
    @Override
    public String getBuildGradleContent() {
        return "apply plugin: 'java'\n";
    }

    @Override
    public void createFiles() {
        // do nothing
    }

    @Override
    public void write(@NonNull File projectDir, @Nullable String buildScriptContent)
            throws IOException {
        File sources = new File(projectDir, "src/main");
        sources.mkdirs();

        File resources = new File(projectDir, "src/resources");
        resources.mkdirs();

        if (buildScriptContent == null || buildScriptContent.isEmpty())
            buildScriptContent = getBuildGradleContent();

        Files.append(buildScriptContent,
                new File(projectDir, "build.gradle"),
                Charset.defaultCharset());
    }

    @Override
    public boolean containsFullBuildScript() {
        return false;
    }
}

