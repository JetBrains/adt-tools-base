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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class InstantRunAnalyticsHelperTest {

    @Mock
    public InstantRunBuildContext mInstantRunBuildContext;
    @Mock
    public InstantRunBuildContext.Build mBuild;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAnalyticsHelper() {
        when(mInstantRunBuildContext.getLastBuild()).thenReturn(mBuild);
        when(mInstantRunBuildContext.getBuildMode()).thenReturn(InstantRunBuildMode.HOT_WARM);
        when(mInstantRunBuildContext.getPatchingPolicy())
                .thenReturn(InstantRunPatchingPolicy.MULTI_DEX);
        when(mInstantRunBuildContext.getVerifierResult()).thenReturn(Optional.of(
                InstantRunVerifierStatus.COMPATIBLE));
        when(mBuild.getArtifacts()).thenReturn(ImmutableList.of(
                new InstantRunBuildContext.Artifact(InstantRunBuildContext.FileType.RESOURCES,
                        new File("resources.ap_")),
                new InstantRunBuildContext.Artifact(InstantRunBuildContext.FileType.RELOAD_DEX,
                        new File("reload.dex"))
        ));

        AndroidStudioStats.InstantRunStatus proto =
                InstantRunAnalyticsHelper.generateAnalyticsProto(mInstantRunBuildContext);

        assertEquals(AndroidStudioStats.InstantRunStatus.BuildMode.HOT_WARM, proto.getBuildMode());
        assertEquals(AndroidStudioStats.InstantRunStatus.PatchingPolicy.MULTI_DEX,
                proto.getPatchingPolicy());
        assertEquals(AndroidStudioStats.InstantRunStatus.VerifierStatus.COMPATIBLE,
                proto.getVerifierStatus());

        assertEquals(2, proto.getArtifactCount());

        List<AndroidStudioStats.InstantRunArtifact> artifacts = proto.getArtifactList();
        assertEquals("RESOURCES", artifacts.get(0).getType().toString());
        assertEquals("RELOAD_DEX", artifacts.get(1).getType().toString());
    }


    @Test
    public void checkBuildModeEnum() {
        for (InstantRunBuildMode mode : InstantRunBuildMode.values()) {
            assertEquals(mode.toString(), InstantRunAnalyticsHelper.convert(mode).toString());
        }
    }

    @Test
    public void checkPatchingPolicyEnum() {
        for (InstantRunPatchingPolicy policy : InstantRunPatchingPolicy.values()) {
            assertEquals(policy.toString(), InstantRunAnalyticsHelper.convert(policy).toString());
        }
        assertEquals(AndroidStudioStats.InstantRunStatus.PatchingPolicy.UNKNOWN_PATCHING_POLICY,
                InstantRunAnalyticsHelper.convert((InstantRunPatchingPolicy) null));
    }

    @Test
    public void checkVerifierStatusEnum() {
        for (InstantRunVerifierStatus status : InstantRunVerifierStatus.values()) {
            assertEquals(status.toString(), InstantRunAnalyticsHelper.convert(status).toString());
        }
        assertEquals(AndroidStudioStats.InstantRunStatus.VerifierStatus.UNKNOWN_VERIFIER_STATUS,
                InstantRunAnalyticsHelper.convert((InstantRunVerifierStatus) null));
    }
}
