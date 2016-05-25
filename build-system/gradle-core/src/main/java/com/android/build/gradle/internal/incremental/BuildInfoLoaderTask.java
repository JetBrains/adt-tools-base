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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.utils.FileUtils;
import com.google.common.io.Files;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * Task responsible for loading past iteration build-info.xml file and backup necessary files for
 * disconnected devices to be able to "catch up" to latest bits.
 *
 * It has no explicitly declared inputs and outputs, as it needs to run every time anyway.
 */
public class BuildInfoLoaderTask extends BaseTask {

    // Outputs
    File pastBuildsFolder;

    // Inputs
    String buildId;
    File buildInfoFile;
    File tmpBuildInfoFile;

    Logger logger;

    // Variant state that is modified.
    InstantRunBuildContext instantRunBuildContext;

    @TaskAction
    public void executeAction() {
        // saves the build information xml file.
        try {
            // load the persisted state, this will give us previous build-ids in case we need them.
            if (buildInfoFile.exists()) {
                instantRunBuildContext.loadFromXmlFile(buildInfoFile);
            }
            // check for the presence of a temporary buildInfoFile and if it exists, merge its
            // artifacts into the current build.
            if (tmpBuildInfoFile.exists()) {
                instantRunBuildContext.mergeFromFile(tmpBuildInfoFile);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Exception while loading build-info.xml : %s", e.getMessage()));
        }
        try {
            // move last iteration artifacts to our back up folder.
            InstantRunBuildContext.Build lastBuild = instantRunBuildContext.getLastBuild();
            if (lastBuild == null) {
                return;
            }

            // create a new backup folder with the old build-id as the name.
            File backupFolder = new File(pastBuildsFolder, String.valueOf(lastBuild.getBuildId()));
            FileUtils.mkdirs(backupFolder);
            for (InstantRunBuildContext.Artifact artifact : lastBuild.getArtifacts()) {
                if (!artifact.isAccumulative()) {
                    File oldLocation = artifact.getLocation();
                    // last iteration could have been a cold swap.
                    if (!oldLocation.isFile()) {
                        return;
                    }
                    File newLocation = new File(backupFolder, oldLocation.getName());
                    if (logger.isEnabled(LogLevel.DEBUG)) {
                        logger.debug(String.format("File moved from %1$s to %2$s",
                                oldLocation.getPath(), newLocation.getPath()));
                    }
                    Files.copy(oldLocation, newLocation);
                    // update the location in the model so it is saved with the build-info.xml
                    artifact.setLocation(newLocation);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Exception while doing past iteration backup : %s",
                            e.getMessage()));
        }
    }

    public static class ConfigAction implements TaskConfigAction<BuildInfoLoaderTask> {

        private final String taskName;

        private final InstantRunVariantScope variantScope;

        private final Logger logger;

        public ConfigAction(@NonNull InstantRunVariantScope scope, @NonNull Logger logger) {
            this.taskName = scope.getTransformVariantScope().getTaskName("buildInfo", "Loader");
            this.variantScope = scope;
            this.logger = logger;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<BuildInfoLoaderTask> getType() {
            return BuildInfoLoaderTask.class;
        }

        @Override
        public void execute(@NonNull BuildInfoLoaderTask task) {
            task.setDescription("InstantRun task to load and backup previous iterations artifacts");
            task.setVariantName(variantScope.getFullVariantName());
            variantScope.getInstantRunBuildContext().setTmpBuildInfo(
                    InstantRunWrapperTask.ConfigAction.getTmpBuildInfoFile(variantScope));
            task.buildInfoFile = InstantRunWrapperTask.ConfigAction.getBuildInfoFile(variantScope);
            task.tmpBuildInfoFile =
                    InstantRunWrapperTask.ConfigAction.getTmpBuildInfoFile(variantScope);
            task.pastBuildsFolder = variantScope.getInstantRunPastIterationsFolder();
            task.instantRunBuildContext = variantScope.getInstantRunBuildContext();
            task.logger = logger;
            task.buildId = String.valueOf(task.instantRunBuildContext.getBuildId());
        }
    }
}
