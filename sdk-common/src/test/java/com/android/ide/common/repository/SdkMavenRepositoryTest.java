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

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import junit.framework.TestCase;

import java.io.File;

public class SdkMavenRepositoryTest extends TestCase {

    public static final File SDK_HOME = new File("/sdk");
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

    @SuppressWarnings("ConstantConditions")
    public void testGetByGroupId() {
        assertSame(SdkMavenRepository.ANDROID, SdkMavenRepository.getByGroupId(
                GradleCoordinate.parseCoordinateString(
                        "com.android.support:appcompat-v7:13.0.0").getGroupId()));
        assertSame(SdkMavenRepository.ANDROID, SdkMavenRepository.getByGroupId(
                GradleCoordinate.parseCoordinateString(
                        "com.android.support.test:espresso:0.2").getGroupId()));
        assertSame(SdkMavenRepository.GOOGLE, SdkMavenRepository.getByGroupId(
                GradleCoordinate.parseCoordinateString(
                        "com.google.android.gms:play-services:5.2.08").getGroupId()));
        assertSame(SdkMavenRepository.GOOGLE, SdkMavenRepository.getByGroupId(
                GradleCoordinate.parseCoordinateString(
                        "com.google.android.gms:play-services-wearable:5.0.77").getGroupId()));
        assertNull(SdkMavenRepository.getByGroupId(GradleCoordinate.parseCoordinateString(
                "com.google.guava:guava:11.0.2").getGroupId()));
    }
}