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

package com.android.ide.common.repository;

import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;

public class SdkMavenRepositoryTest extends TestCase {
    private static final File SDK_HOME = new File("/sdk");

    private MockFileOp mFileOp;
    private AndroidSdkHandler mSdkHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFileOp = new MockFileOp();
        mSdkHandler = new AndroidSdkHandler(SDK_HOME, mFileOp);
    }

    private void registerAndroidRepo() {
        mFileOp.recordExistingFile(
                "/sdk/extras/android/m2repository/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<addon:sdk-addon xmlns:addon=\"http://schemas.android.com/sdk/android/repo/addon2/01\""
                        + "                 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" >"
                        + "    <localPackage path=\"extras;android;m2repository\">"
                        + "        <type-details xsi:type=\"addon:extraDetailsType\">"
                        + "            <vendor>"
                        + "                <id>android</id>"
                        + "                <display>Android</display>"
                        + "            </vendor>"
                        + "        </type-details>"
                        + "        <revision>"
                        + "            <major>25</major>"
                        + "            <minor>0</minor>"
                        + "            <micro>0</micro>"
                        + "        </revision>"
                        + "        <display-name>Android Support Repository, rev 25</display-name>"
                        + "    </localPackage>"
                        + "</addon:sdk-addon>\n");
    }

    private void registerGoogleRepo() {
        mFileOp.recordExistingFile(
                "/sdk/extras/google/m2repository/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<addon:sdk-addon xmlns:addon=\"http://schemas.android.com/sdk/android/repo/addon2/01\""
                        + "                 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" >"
                        + "    <localPackage path=\"extras;google;m2repository\">"
                        + "        <type-details xsi:type=\"addon:extraDetailsType\">"
                        + "            <vendor>"
                        + "                <id>google</id>"
                        + "                <display>Google Inc.</display>"
                        + "            </vendor>"
                        + "        </type-details>"
                        + "        <revision>"
                        + "            <major>23</major>"
                        + "            <minor>0</minor>"
                        + "            <micro>0</micro>"
                        + "        </revision>"
                        + "        <display-name>Google Repository, rev 23</display-name>"
                        + "    </localPackage>"
                        + "</addon:sdk-addon>\n");

    }

    public void testGetLocation() {
        registerGoogleRepo();
        registerAndroidRepo();
        assertNull(SdkMavenRepository.ANDROID.getRepositoryLocation(null, false, mFileOp));

        File android = SdkMavenRepository.ANDROID.getRepositoryLocation(SDK_HOME, true, mFileOp);
        assertNotNull(android);

        File google = SdkMavenRepository.GOOGLE.getRepositoryLocation(SDK_HOME, true, mFileOp);
        assertNotNull(google);
    }

    public void testGetBestMatch() {
        registerAndroidRepo();
        mFileOp.recordExistingFolder("/sdk/extras/android/m2repository/com/android/support/support-v4/19.0.0");
        mFileOp.recordExistingFolder("/sdk/extras/android/m2repository/com/android/support/support-v4/19.1.0");
        mFileOp.recordExistingFolder("/sdk/extras/android/m2repository/com/android/support/support-v4/20.0.0");
        mFileOp.recordExistingFolder("/sdk/extras/android/m2repository/com/android/support/support-v4/22.0.0-rc1");
        assertNull(SdkMavenRepository.ANDROID.getHighestInstalledVersion(
                null, "com.android.support", "support-v4", "19", false, mFileOp));

        GradleCoordinate gc1 = SdkMavenRepository.ANDROID.getHighestInstalledVersion(
                SDK_HOME, "com.android.support", "support-v4", "19", false, mFileOp);
        assertEquals(GradleCoordinate.parseCoordinateString(
                "com.android.support:support-v4:19.1.0"), gc1);

        GradleCoordinate gc2 = SdkMavenRepository.ANDROID.getHighestInstalledVersion(
                SDK_HOME, "com.android.support", "support-v4", "20", false, mFileOp);
        assertEquals(GradleCoordinate.parseCoordinateString(
                "com.android.support:support-v4:20.0.0"), gc2);

        GradleCoordinate gc3 = SdkMavenRepository.ANDROID.getHighestInstalledVersion(
                SDK_HOME, "com.android.support", "support-v4", "22", false, mFileOp);
        assertNull(gc3);

        GradleCoordinate gc4 = SdkMavenRepository.ANDROID.getHighestInstalledVersion(
                SDK_HOME, "com.android.support", "support-v4", "22", true, mFileOp);
        assertEquals(GradleCoordinate.parseCoordinateString(
                "com.android.support:support-v4:22.0.0-rc1"), gc4);
    }

    public void testIsInstalled() {
        assertFalse(SdkMavenRepository.ANDROID.isInstalled(null, mFileOp));
        assertFalse(SdkMavenRepository.ANDROID.isInstalled(null));
        assertFalse(SdkMavenRepository.ANDROID.isInstalled(mSdkHandler));
        assertFalse(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));

        registerAndroidRepo();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler.getSdkManager(progress).loadSynchronously(0, progress, null, null);
        assertFalse(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));
        assertTrue(SdkMavenRepository.ANDROID.isInstalled(mSdkHandler));

        registerGoogleRepo();
        mSdkHandler.getSdkManager(progress).loadSynchronously(0, progress, null, null);
        assertTrue(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));
    }

    public void testGetDirName() {
        assertEquals("android", SdkMavenRepository.ANDROID.getDirName());
        assertEquals("google", SdkMavenRepository.GOOGLE.getDirName());
    }

    public void testGetByGroupId() {
        mFileOp.recordExistingFolder(
                "/sdk/extras/android/m2repository/com/android/support/appcompat-v7/19.0.0");
        mFileOp.recordExistingFolder(
                "/sdk/extras/google/m2repository/com/google/android/gms/play-services/5.2.08");

        assertSame(
                SdkMavenRepository.ANDROID,
                SdkMavenRepository.find(SDK_HOME, "com.android.support", "appcompat-v7", mFileOp));
        assertSame(
                SdkMavenRepository.GOOGLE,
                SdkMavenRepository.find(
                        SDK_HOME, "com.google.android.gms", "play-services", mFileOp));
        assertNull(
                SdkMavenRepository.find(SDK_HOME, "com.google.guava", "guava", mFileOp));
    }

    public void testGetSdkPath() throws Exception {
        GradleCoordinate coord = new GradleCoordinate("foo.bar.baz", "artifact1",
                GradleCoordinate.parseRevisionNumber("1.2.3-alpha1"), null);
        String result = DetailsTypes.MavenType.getRepositoryPath(
                coord.getGroupId(), coord.getArtifactId(), coord.getRevision());
        assertEquals("extras;m2repository;foo;bar;baz;artifact1;1.2.3-alpha1", result);

        coord = new GradleCoordinate("foo.bar.baz", "artifact1", 1);
        result = DetailsTypes.MavenType.getRepositoryPath(
                coord.getGroupId(), coord.getArtifactId(), coord.getRevision());
        assertEquals("extras;m2repository;foo;bar;baz;artifact1;1", result);
    }

    public void testGetCoordinateFromSdkPath() throws Exception {
        GradleCoordinate result = SdkMavenRepository
                .getCoordinateFromSdkPath("extras;m2repository;foo;bar;baz;artifact1;1.2.3-alpha1");
        assertEquals(new GradleCoordinate("foo.bar.baz", "artifact1",
                GradleCoordinate.parseRevisionNumber("1.2.3-alpha1"), null), result);

        result = SdkMavenRepository
                .getCoordinateFromSdkPath("extras;m2repository;foo;bar;baz;artifact1;1");
        assertEquals(new GradleCoordinate("foo.bar.baz", "artifact1", 1), result);

        result = SdkMavenRepository.getCoordinateFromSdkPath("bogus;foo;bar;baz;artifact1;1");
        assertNull(result);
    }

    public void testFindBestPackage() {
        FakePackage r1 = new FakePackage("extras;m2repository;group;artifact;1", new Revision(1),
                null);
        FakePackage r123 = new FakePackage("extras;m2repository;group;artifact;1.2.3",
                new Revision(1), null);
        FakePackage r2 = new FakePackage("extras;m2repository;group;artifact;2", new Revision(1),
                null);
        FakePackage r211 = new FakePackage("extras;m2repository;group;artifact;2.1.1",
                new Revision(1), null);
        FakePackage bogus = new FakePackage("foo;group;artifact;2.1.2", new Revision(1), null);
        FakePackage other = new FakePackage("extras;m2repository;group2;artifact;2.1.3",
                new Revision(1), null);
        List<RepoPackage> packages = ImmutableList.of(r1, r123, r2, r211, bogus, other);

        GradleCoordinate pattern = new GradleCoordinate("group", "artifact", 1);
        assertEquals(r1, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, 2, 3);
        assertEquals(r123, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, GradleCoordinate.PLUS_REV_VALUE);
        assertEquals(r123, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, 0);
        assertEquals(r1, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 2, 1, 2);
        assertNull(SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 2, 1, 3);
        assertNull(SdkMavenRepository.findBestPackageMatching(pattern, packages));
    }
}