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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import org.jetbrains.annotations.Nullable;

public class InstantRunAnalyticsHelper {

    /**
     * Generate a scrubbed proto of the instant run build context for analytics.
     */
    @NonNull
    public static AndroidStudioStats.InstantRunStatus generateAnalyticsProto(
            @NonNull InstantRunBuildContext instantRunBuildContext) {
        AndroidStudioStats.InstantRunStatus.Builder builder =
                AndroidStudioStats.InstantRunStatus.newBuilder();

        builder.setBuildMode(convert(instantRunBuildContext.getBuildMode()));
        builder.setPatchingPolicy(convert(instantRunBuildContext.getPatchingPolicy()));
        builder.setVerifierStatus(convert(instantRunBuildContext.getVerifierResult().orElse(null)));

        InstantRunBuildContext.Build last = instantRunBuildContext.getLastBuild();
        if (last != null) {
            for (InstantRunBuildContext.Artifact artifact : last.getArtifacts()) {
                builder.addArtifact(
                        AndroidStudioStats.InstantRunArtifact.newBuilder()
                                .setType(convert(artifact.getType())));
            }
        }
        return builder.build();
    }


    @VisibleForTesting
    @NonNull
    static AndroidStudioStats.InstantRunStatus.BuildMode convert(
            @NonNull InstantRunBuildMode mode) {
        switch (mode) {
            case HOT_WARM:
                return AndroidStudioStats.InstantRunStatus.BuildMode.HOT_WARM;
            case COLD:
                return AndroidStudioStats.InstantRunStatus.BuildMode.COLD;
            case FULL:
                return AndroidStudioStats.InstantRunStatus.BuildMode.FULL;
            default:
                return AndroidStudioStats.InstantRunStatus.BuildMode.UNKNOWN_BUILD_MODE;
        }
    }

    @VisibleForTesting
    @NonNull
    static AndroidStudioStats.InstantRunStatus.PatchingPolicy convert(
            @Nullable InstantRunPatchingPolicy policy) {
        if (policy == null) {
            return AndroidStudioStats.InstantRunStatus.PatchingPolicy.UNKNOWN_PATCHING_POLICY;
        }
        switch (policy) {
            case PRE_LOLLIPOP:
                return AndroidStudioStats.InstantRunStatus.PatchingPolicy.PRE_LOLLIPOP;
            case MULTI_DEX:
                return AndroidStudioStats.InstantRunStatus.PatchingPolicy.MULTI_DEX;
            case MULTI_APK:
                return AndroidStudioStats.InstantRunStatus.PatchingPolicy.MULTI_APK;
            default:
                return AndroidStudioStats.InstantRunStatus.PatchingPolicy.UNKNOWN_PATCHING_POLICY;
        }
    }

    @VisibleForTesting
    @NonNull
    static AndroidStudioStats.InstantRunStatus.VerifierStatus convert(
            @Nullable InstantRunVerifierStatus status) {
        if (status == null) {
            return AndroidStudioStats.InstantRunStatus.VerifierStatus.UNKNOWN_VERIFIER_STATUS;
        }
        try {
            return AndroidStudioStats.InstantRunStatus.VerifierStatus.valueOf(status.toString());
        } catch (IllegalArgumentException ignored) {
            return AndroidStudioStats.InstantRunStatus.VerifierStatus.UNKNOWN_VERIFIER_STATUS;
        }
    }

    @VisibleForTesting
    @NonNull
    static AndroidStudioStats.InstantRunArtifact.Type convert(
            @NonNull InstantRunBuildContext.FileType type) {
        switch (type) {
            case MAIN:
                return AndroidStudioStats.InstantRunArtifact.Type.MAIN;
            case SPLIT_MAIN:
                return AndroidStudioStats.InstantRunArtifact.Type.SPLIT_MAIN;
            case RELOAD_DEX:
                return AndroidStudioStats.InstantRunArtifact.Type.RELOAD_DEX;
            case RESTART_DEX:
                return AndroidStudioStats.InstantRunArtifact.Type.RESTART_DEX;
            case DEX:
                return AndroidStudioStats.InstantRunArtifact.Type.DEX;
            case SPLIT:
                return AndroidStudioStats.InstantRunArtifact.Type.SPLIT;
            case RESOURCES:
                return AndroidStudioStats.InstantRunArtifact.Type.RESOURCES;
            default:
                return null;
        }

    }

    private InstantRunAnalyticsHelper() {
        // Utility class
    }
}
