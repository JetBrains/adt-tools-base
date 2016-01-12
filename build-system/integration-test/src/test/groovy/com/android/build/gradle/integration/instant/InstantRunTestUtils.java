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

package com.android.build.gradle.integration.instant;

import com.android.annotations.NonNull;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

final class InstantRunTestUtils {

    @NonNull
    static InstantRunBuildContext loadContext(@NonNull InstantRun instantRunModel)
            throws Exception {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.loadFromXmlFile(instantRunModel.getInfoFile());
        return context;
    }

    @NonNull
    static InstantRun getInstantRunModel(@NonNull AndroidProject project) {
        Collection<Variant> variants = project.getVariants();
        for (Variant variant : variants) {
            if ("debug".equals(variant.getName())) {
                return variant.getMainArtifact().getInstantRun();
            }
        }
        throw new AssertionError("Could not find debug variant.");
    }

    @NonNull
    static List<String> getInstantRunArgs(OptionalCompilationStep... flags) {
        return ImmutableList.of(buildOptionalCompilationStepsProperty(flags));
    }

    @NonNull
    static List<String> getInstantRunArgs(int apiLevel,
            @NonNull OptionalCompilationStep... flags) {
        String version = String.format("-Pandroid.injected.build.api=%d", apiLevel);
        return ImmutableList.of(buildOptionalCompilationStepsProperty(flags), version);
    }

    @NonNull
    private static String buildOptionalCompilationStepsProperty(
            @NonNull OptionalCompilationStep[] optionalCompilationSteps) {
        StringBuilder builder = new StringBuilder();
        builder.append("-P").append(AndroidProject.OPTIONAL_COMPILATION_STEPS).append('=')
                .append(OptionalCompilationStep.INSTANT_DEV);
        for (OptionalCompilationStep step : optionalCompilationSteps) {
            builder.append(',').append(step);
        }
        return builder.toString();
    }
}
