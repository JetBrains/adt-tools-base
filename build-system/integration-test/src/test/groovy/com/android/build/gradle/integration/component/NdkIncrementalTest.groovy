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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.FileHelper
import com.android.build.gradle.integration.common.utils.IncrementalTaskOutputVerifier
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.regex.Pattern

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test incremental compilation for NDK.
 */
@CompileStatic
class NdkIncrementalTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .forExperimentalPlugin(true)
            .captureStdOut(true)
            .create();

    @Before
    public void setUp() {
        project.file("src/main/jni/empty.c").createNewFile()

        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
    }
}
"""
        project.execute("assembleDebug")
    }

    @Test
    public void "check adding file"() {
        File helloJniO = FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/hello-jni\\.o")).first()
        long helloJniTimestamp = helloJniO.lastModified()

        // check new-file.o does not exist.
        assertThat(FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/new-file\\.o"))).hasSize(0)

        project.file("src/main/jni/new-file.c") << " ";

        project.stdout.reset()
        project.execute("assembleDebug")

        IncrementalTaskOutputVerifier verifier =
                new IncrementalTaskOutputVerifier(project.stdout.toString());
        verifier.assertThatFile(project.file("src/main/jni/new-file.c")).hasBeenAdded()
        verifier.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged()

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp)

        assertThat(FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/new-file\\.o"))).hasSize(1)
    }

    @Test
    public void "check removing file"() {
        File helloJniO = FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/hello-jni\\.o")).first()
        long helloJniTimestamp = helloJniO.lastModified()

        assertThat(FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/empty\\.o"))).hasSize(1)

        project.file("src/main/jni/empty.c").delete()

        project.stdout.reset()
        project.execute("assembleDebug")

        IncrementalTaskOutputVerifier verifier =
                new IncrementalTaskOutputVerifier(project.stdout.toString());
        verifier.assertThatFile(project.file("src/main/jni/empty.c")).hasBeenRemoved()
        verifier.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged()

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp)

        assertThat(FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/empty\\.o"))).hasSize(0)
    }

    @Test
    public void "check changing file"() {
        File helloJniO = FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/hello-jni\\.o")).first()
        long helloJniTimestamp = helloJniO.lastModified()

        File emptyO = FileHelper.find(
                project.file("build/intermediates/objectFiles"),
                Pattern.compile("x86Debug.*/empty\\.o")).first()
        long emptyTimestamp = emptyO.lastModified()

        project.file("src/main/jni/empty.c") << " ";

        project.stdout.reset()
        project.execute("assembleDebug")

        IncrementalTaskOutputVerifier verifier =
                new IncrementalTaskOutputVerifier(project.stdout.toString());
        verifier.assertThatFile(project.file("src/main/jni/empty.c")).hasChanged()
        verifier.assertThatFile(project.file("src/main/jni/hello-jni.c")).hasNotBeenChanged()

        assertThat(helloJniO).wasModifiedAt(helloJniTimestamp)
        assertThat(emptyO).isNewerThan(emptyTimestamp)
    }
}
