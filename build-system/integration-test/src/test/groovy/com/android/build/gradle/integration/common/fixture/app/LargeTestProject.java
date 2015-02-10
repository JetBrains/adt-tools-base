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
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Implementation of TestProject that can create large scale projects with lots of modules.
 */
public class LargeTestProject<T extends GradleModule> implements TestProject {

    public static final int SMALL_DEPTH = 4;
    public static final int SMALL_BREADTH = 2;
    public static final int MEDIUM_DEPTH = 4;
    public static final int MEDIUM_BREADTH = 3;
    public static final int LARGE_DEPTH = 5;
    public static final int LARGE_BREADTH = 4;
    public static final int VERY_LARGE_DEPTH = 5;
    public static final int VERY_LARGE_BREADTH = 5;

    @NonNull
    private final GradleModuleFactory factory;
    private final int maxDepth;
    private final int maxBreadth;

    public static final class Builder {

        private Class<? extends GradleModule> moduleClass;
        private int maxDepth = 1;
        private int maxBreadth = 1;

        public Builder withModule(@NonNull Class<? extends GradleModule> moduleClass) {
            this.moduleClass = moduleClass;
            return this;
        }

        public Builder withDepth(int depth) {
            this.maxDepth = depth;
            return this;
        }

        public Builder withBreadth(int breadth) {
            this.maxBreadth = breadth;
            return this;
        }

        public LargeTestProject create() throws IOException {
            return new LargeTestProject(new GradleModuleFactory(moduleClass), maxDepth, maxBreadth);
        }
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public LargeTestProject(
            @NonNull GradleModuleFactory factory, int maxDepth, int maxBreadth) {
        this.factory = factory;
        this.maxDepth = maxDepth;
        this.maxBreadth = maxBreadth;
    }

    @Override
    public void write(@NonNull File projectDir, @Nullable String buildScriptContent)
            throws IOException {

        GradleModule gradleModule = createProject(factory, 0, ":", projectDir, "0");

        createSettingsGradle(gradleModule, projectDir);
        createGradleProperties(projectDir);

        Files.write(
                buildScriptContent,
                new File(projectDir, "build.gradle"),
                Charset.defaultCharset());
    }

    @Override
    public boolean containsFullBuildScript() {
        return false;
    }

    private GradleModule createProject(
            @NonNull GradleModuleFactory factory,
            int depth,
            @NonNull String path,
            @NonNull File location,
            @NonNull String suffix) throws IOException {
        List<GradleModule> deps = Lists.newArrayList();
        if (depth < maxDepth) {
            for (int i = 0 ; i < maxBreadth ; i++) {
                String newSuffix = suffix + (i+1);
                GradleModule project = createProject(
                        factory,
                        depth + 1,
                        path + "lib" + newSuffix+":",
                        new File(location, "lib" + newSuffix),
                        newSuffix);
                deps.add(project);
            }
        }

        GradleModule project = factory.createModule(
                new File(location, "main" + suffix),
                path + "main" + suffix,
                deps);

        project.create();

        return project;
    }

    private static void createSettingsGradle(
            @NonNull GradleModule project,
            @NonNull File location) throws IOException {
        StringBuilder builder = new StringBuilder();
        buildSettingsGradle(project, builder);

        Files.write(builder.toString(), new File(location, "settings.gradle"),
                Charset.defaultCharset());
    }

    private static void buildSettingsGradle(
            @NonNull GradleModule project,
            @NonNull StringBuilder builder) {
        builder.append("include '").append(project.getGradlePath()).append("'\n");
        for (GradleModule dep : project.getProjectDeps()) {
            buildSettingsGradle(dep, builder);
        }
    }

    private static void createGradleProperties(@NonNull File location) throws IOException {
        Files.write(
                "org.gradle.jvmargs=-Xmx6096m -XX:MaxPermSize=1024m\n" +
                        "org.gradle.daemon=true\n",
                new File(location, "gradle.properties"), Charset.defaultCharset());
    }
}
