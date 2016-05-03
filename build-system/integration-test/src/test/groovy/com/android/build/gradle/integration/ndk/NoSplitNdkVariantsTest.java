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

package com.android.build.gradle.integration.ndk;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.testing.api.DeviceException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipFile;

/**
 * Integration test of the native plugin with multiple variants without using splits.
 */
public class NoSplitNdkVariantsTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "apply plugin: 'com.android.application'\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "    defaultConfig {\n"
                + "        ndk {\n"
                + "            moduleName 'hello-jni'\n"
                + "        }\n"
                + "    }\n"
                + "    buildTypes {\n"
                + "        release\n"
                + "        debug {\n"
                + "            jniDebuggable true\n"
                + "        }\n"
                + "    }\n"
                + "    productFlavors {\n"
                + "        x86 {\n"
                + "            ndk {\n"
                + "                abiFilter 'x86'\n"
                + "            }\n"
                + "        }\n"
                + "        arm {\n"
                + "            ndk {\n"
                + "                abiFilters 'armeabi-v7a', 'armeabi'\n"
                + "            }\n"
                + "        }\n"
                + "        mips {\n"
                + "            ndk {\n"
                + "                abiFilter 'mips'\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleX86Release() throws IOException {
        project.execute("assembleX86Release");

        // Verify .so are built for all platform.
        try (ZipFile apk = new ZipFile(project.getApk("x86", "release", "unsigned"))) {
            assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
            assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
            assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
            assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
        }
    }

    @Test
    public void assembleArmRelease() throws IOException {
        project.execute("assembleArmRelease");

        // Verify .so are built for all platform.
        try (ZipFile apk = new ZipFile(project.getApk("arm", "release", "unsigned"))) {
            assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
            assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
        }

    }

    @Test
    public void assembleMipsRelease() throws IOException {
        project.execute("assembleMipsRelease");

        // Verify .so are built for all platform.
        try (ZipFile apk = new ZipFile(project.getApk("mips", "release", "unsigned"))) {
            assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
            assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
            assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
            assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
        }
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws DeviceException {

        if (GradleTestProject.DEVICE_PROVIDER_NAME.equals(BuilderConstants.CONNECTED)) {
            Collection<String> abis = DeviceHelper.getDeviceAbis();
            if (abis.contains("x86")) {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "x86DebugAndroidTest");
            } else {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "ArmDebugAndroidTest");
            }
        } else {
            project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "X86DebugAndroidTest");
        }
    }
}
