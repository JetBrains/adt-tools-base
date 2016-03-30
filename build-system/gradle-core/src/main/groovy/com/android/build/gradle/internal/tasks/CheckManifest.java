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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Class that checks the presence of the manifest.
 */
@ParallelizableTask
public class CheckManifest extends DefaultAndroidTask {

    private File manifest;

    @InputFile
    public File getManifest() {
        return manifest;
    }

    public void setManifest(@NonNull File manifest) {
        this.manifest = manifest;
    }

    @TaskAction
    void check() {
        // use getter to resolve convention mapping
        File f = getManifest();
        if (!f.isFile()) {
            throw new IllegalArgumentException(String.format(
                    "Main Manifest missing for variant %1$s. Expected path: %2$s",
                    getVariantName(), getManifest().getAbsolutePath()));
        }
    }


    public static class ConfigAction implements TaskConfigAction<CheckManifest> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("check", "Manifest");
        }

        @NonNull
        @Override
        public Class<CheckManifest> getType() {
            return CheckManifest.class;
        }

        @Override
        public void execute(@NonNull CheckManifest checkManifestTask) {
            scope.getVariantData().checkManifestTask = checkManifestTask;
            checkManifestTask.setVariantName(
                    scope.getVariantData().getVariantConfiguration().getFullName());
            ConventionMappingHelper.map(checkManifestTask, "manifest", (Callable<File>) () ->
                    scope.getVariantData().getVariantConfiguration().getDefaultSourceSet()
                                    .getManifestFile());
        }
    }
}
