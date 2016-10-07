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
import com.android.builder.profile.ProcessRecorder;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * InstantRun related tasks wrapping code, this task is added twice to the task trees, once
 * for the full build (assembleVariant), once for the incremental build. Only one of these two
 * task will execute from the IDE.
 *
 * Task responsibility :
 * <ul><li>generate the build-info.xml on each gradle invocation with InstantRun enabled.</li>
 * <li>delete incremental change files when doing a full build.</li></ul>
 */
public class InstantRunWrapperTask extends BaseTask {

    /**
     * Output File
     */
    File buildInfoFile;

    /** Input */
    String buildId;

    File tmpBuildInfoFile;

    Logger logger;

    InstantRunBuildContext instantRunBuildContext;

    @TaskAction
    public void executeAction() {

        // done with the instant run context.
        instantRunBuildContext.close();

        // saves the build information xml file.
        try {
            // only write past builds in incremental mode.
            String xml = instantRunBuildContext.toXml();
            if (logger.isEnabled(LogLevel.DEBUG)) {
                logger.debug("build-id $1$l, build-info.xml : %2$s",
                        instantRunBuildContext.getBuildId(), xml);
            }
            Files.createParentDirs(buildInfoFile);
            Files.write(xml, buildInfoFile, Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Exception while saving build-info.xml : %s", e.getMessage()));
        }

        // since we closed and produce the build-info.xml, delete any temporary one.
        if (tmpBuildInfoFile.exists()) {
            if (!tmpBuildInfoFile.delete()) {
                logger.warn(String.format("Cannot delete %1$s", tmpBuildInfoFile));
            }
        }

        // Record instant run status in analytics for this build
        ProcessRecorder.getGlobalProperties().setInstantRunStatus(
                InstantRunAnalyticsHelper.generateAnalyticsProto(instantRunBuildContext));
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunWrapperTask> {

        public static File getBuildInfoFile(InstantRunVariantScope scope) {
            return new File(scope.getRestartDexOutputFolder(), "build-info.xml");
        }

        public static File getTmpBuildInfoFile(InstantRunVariantScope scope) {
            return new File(scope.getRestartDexOutputFolder(), "tmp-build-info.xml");
        }


        private final String taskName;
        private final InstantRunVariantScope variantScope;
        private final Logger logger;

        public ConfigAction(@NonNull InstantRunVariantScope scope,
                @NonNull Logger logger) {
            this.taskName = scope.getTransformVariantScope().getTaskName("buildInfoGenerator");
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
        public Class<InstantRunWrapperTask> getType() {
            return InstantRunWrapperTask.class;
        }

        @Override
        public void execute(@NonNull InstantRunWrapperTask task) {
            task.setDescription("InstantRun task to build incremental artifacts");
            task.setVariantName(variantScope.getFullVariantName());
            task.buildInfoFile = getBuildInfoFile(variantScope);
            task.tmpBuildInfoFile = getTmpBuildInfoFile(variantScope);
            task.instantRunBuildContext = variantScope.getInstantRunBuildContext();
            task.logger = logger;
            task.buildId = String.valueOf(task.instantRunBuildContext.getBuildId());
        }
    }

}
