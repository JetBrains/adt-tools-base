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
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * Task to generate the build-info.xml on each gradle invocation with InstantRun enabled.
 */
public class BuildInfoGeneratorTask extends BaseTask {

    @OutputFile
    File buildInfoFile;

    @Input
    String buildId;

    Logger logger;

    InstantRunBuildContext instantRunBuildContext;

    @TaskAction
    public void executeAction() {
        // saves the build information xml file.
        try {
            String xml = instantRunBuildContext.toXml();
            if (logger.isEnabled(LogLevel.DEBUG)) {
                logger.debug("build-id $1$l, build-info.xml : %2$s",
                        instantRunBuildContext.getBuildId(), xml);
            }
            Files.createParentDirs(buildInfoFile);
            Files.write(instantRunBuildContext.toXml(), buildInfoFile, Charsets.UTF_8);
        } catch (Exception e) {

            throw new RuntimeException(
                    String.format("Exception while saving build-info.xml : %s", e.getMessage()));
        }
    }

    public static class ConfigAction implements TaskConfigAction<BuildInfoGeneratorTask> {

        public static String getName(VariantScope scope) {
            return scope.getTaskName("incremental", "BuildInfoGenerator");
        }

        public static File getBuildInfoFile(VariantScope scope) {
            return new File(scope.getRestartDexOutputFolder(), "build-info.xml");
        }

        private final String taskName;
        private final VariantScope variantScope;
        private final Logger logger;

        public ConfigAction(VariantScope scope, Logger logger) {
            this.taskName = ConfigAction.getName(scope);
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
        public Class<BuildInfoGeneratorTask> getType() {
            return BuildInfoGeneratorTask.class;
        }

        @Override
        public void execute(@NonNull BuildInfoGeneratorTask task) {
            task.setDescription("InstantRun task to build incremental artifacts");
            task.setVariantName(variantScope.getVariantConfiguration().getFullName());
            task.buildInfoFile = getBuildInfoFile(variantScope);
            task.instantRunBuildContext = variantScope.getInstantRunBuildContext();
            task.logger = logger;
            task.buildId = String.valueOf(task.instantRunBuildContext.getBuildId());
        }
    }

}
