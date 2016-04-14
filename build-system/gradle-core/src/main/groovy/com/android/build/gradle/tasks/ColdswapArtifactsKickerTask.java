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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.scope.VariantScope;

import java.io.File;
import java.io.IOException;

/**
 * Kicker task to force execution of the InstantRun slicer and dexer even if the files have not
 * changed (since we delay and batch restart artifacts creation).
 */
public class ColdswapArtifactsKickerTask extends KickerTask {

    @Override
    protected void doFullTaskAction() throws IOException {
        // if the restart flag is set, we should allow for downstream tasks to execute.
        boolean restartDexRequested =
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY);
        boolean changesAreCompatible =
                variantScope.getInstantRunBuildContext().hasPassedVerification();

        // so if the restart artifacts are requested or the changes are incompatible
        // we should always run, otherwise it can wait.
        MarkerFile.createMarkerFile(getMarkerFile(),
                restartDexRequested || !changesAreCompatible
                        ? MarkerFile.Command.RUN
                        : MarkerFile.Command.BLOCK);
    }

    public static class ConfigAction extends KickerTask.ConfigAction<ColdswapArtifactsKickerTask> {

        public ConfigAction(@NonNull String name, @NonNull VariantScope scope) {
            super(name, scope);
        }

        public static File getMarkerFile(VariantScope scope) {
            return new File(scope.getInstantRunSupportDir(), "coldswap.marker");
        }

        @NonNull
        @Override
        public Class<ColdswapArtifactsKickerTask> getType() {
            return ColdswapArtifactsKickerTask.class;
        }

        @Override
        public void execute(@NonNull ColdswapArtifactsKickerTask task) {
            super.execute(task);
            task.markerFile = getMarkerFile(scope);
        }
    }

}
