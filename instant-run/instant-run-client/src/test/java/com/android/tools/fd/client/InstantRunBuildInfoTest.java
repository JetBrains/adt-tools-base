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
package com.android.tools.fd.client;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class InstantRunBuildInfoTest {

    @Test
    public void testBuildId() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals("1451508349243", info.getTimeStamp());
    }

    @Test
    public void testApiLevel() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals(23, info.getFeatureLevel());
    }

    @Test
    public void testHasNoChanges() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info-no-artifacts.xml");
        assertTrue("If there are no artifacts, then it doesn't matter what the verifier said.", info.hasNoChanges());

        info = getBuildInfo("instantrun", "build-info-res.xml");
        assertFalse("If there is an artifact, then there are changes", info.hasNoChanges());

        info = getBuildInfo("instantrun", "no-changes.xml");
        assertTrue(info.hasNoChanges());
    }

    @Test
    public void testFormat() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals(1, info.getFormat());
    }

    @Test
    public void testSplitApks() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");

        List<InstantRunArtifact> artifacts = info.getArtifacts();
        assertEquals(11, artifacts.size());
        assertTrue(info.hasMainApk());
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1451508349243")).count() == 11);
    }

    @Test
    public void testSplitApks2() throws IOException {
        // Ensure that when we get a main APK (but not all the splits) as part of
        // a build info, we pull in all the slices from the first build too
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info2.xml");

        List<InstantRunArtifact> artifacts = info.getArtifacts();
        assertEquals(12, artifacts.size());
        assertTrue(info.hasMainApk());
        assertTrue(info.hasOneOf(InstantRunArtifactType.SPLIT));
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1452207930094")).count() == 1);
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1452205343311")).count() == 11);
    }

    @NonNull
    private static InstantRunBuildInfo getBuildInfo(@NonNull String... buildInfoPath)
            throws IOException {
        String path = Joiner.on('/').join(buildInfoPath);
        String xml = Resources.toString(Resources.getResource(path), Charsets.UTF_8);
        InstantRunBuildInfo buildInfo = InstantRunBuildInfo.get(xml);
        assertNotNull("Unable to create build info from resource @" + path, buildInfo);
        return buildInfo;
    }

}
