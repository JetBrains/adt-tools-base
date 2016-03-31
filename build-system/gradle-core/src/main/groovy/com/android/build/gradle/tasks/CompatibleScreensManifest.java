/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.resources.Density;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;

import groovy.lang.Closure;

/**
 * Task to generate a manifest snippet that just contains a compatible-screens
 * node with the given density and the given list of screen sizes.
 */
@ParallelizableTask
public class CompatibleScreensManifest extends DefaultAndroidTask {

    private String screenDensity;

    private Set<String> screenSizes;

    private File manifestFile;

    @Input
    public String getScreenDensity() {
        return screenDensity;
    }

    public void setScreenDensity(String screenDensity) {
        this.screenDensity = screenDensity;
    }

    @Input
    public Set<String> getScreenSizes() {
        return screenSizes;
    }

    public void setScreenSizes(Set<String> screenSizes) {
        this.screenSizes = screenSizes;
    }

    @OutputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @TaskAction
    public void generate() throws IOException {
        StringBuilder content = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"\">\n" +
                "\n" +
                "    <compatible-screens>\n");

        String density = getScreenDensity();

        // convert unsupported values to numbers.
        density = convert(density, Density.XXHIGH, Density.XXXHIGH);

        for (String size : getScreenSizes()) {
            content.append(
                    "        <screen android:screenSize=\"").append(size).append("\" "
                    + "android:screenDensity=\"").append(density).append("\" />\n");
        }

        content.append(
                "    </compatible-screens>\n" +
                "</manifest>");

        Files.write(content.toString(), getManifestFile(), Charsets.UTF_8);
    }

    private static String convert(@NonNull String density, @NonNull Density... densitiesToConvert) {
        for (Density densityToConvert : densitiesToConvert) {
            if (densityToConvert.getResourceValue().equals(density)) {
                return Integer.toString(densityToConvert.getDpiValue());
            }
        }

        return density;
    }

    public static class ConfigAction implements TaskConfigAction<CompatibleScreensManifest> {

        @NonNull
        private final VariantOutputScope scope;
        @NonNull
        private final Set<String> screenSizes;

        public ConfigAction(
                @NonNull VariantOutputScope scope,
                @NonNull Set<String> screenSizes) {
            this.scope = scope;
            this.screenSizes = screenSizes;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("create", "CompatibleScreenManifest");
        }

        @NonNull
        @Override
        public Class<CompatibleScreensManifest> getType() {
            return CompatibleScreensManifest.class;
        }

        @Override
        public void execute(@NonNull CompatibleScreensManifest csmTask) {
            csmTask.setVariantName(scope.getVariantScope().getVariantConfiguration().getFullName());

            csmTask.setScreenDensity(scope.getVariantOutputData().getMainOutputFile()
                    .getFilter(com.android.build.OutputFile.DENSITY));
            csmTask.setScreenSizes(screenSizes);

            csmTask.setManifestFile(scope.getCompatibleScreensManifestFile());
        }
    }
}
