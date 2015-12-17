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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
/**
 * Assemble tests for packagingOptions.
 *
 * Creates two jar files and test various packaging options.
 */
@CompileStatic
class PackagingOptionsTest {

    // Projects to create jar files.
    private static AndroidTestApp jarProject1 = new EmptyAndroidTestApp()
    static {
        jarProject1.addFile(new TestSourceFile("", "build.gradle", "apply plugin: 'java'"))
        jarProject1.addFile(new TestSourceFile("src/main/resources", "conflict.txt", "foo"))
    }
    private static AndroidTestApp jarProject2 = new EmptyAndroidTestApp()
    static {
        jarProject2.addFile(new TestSourceFile("", "build.gradle", "apply plugin: 'java'"))
        jarProject2.addFile(new TestSourceFile("src/main/resources", "conflict.txt", "foo"))
        // add an extra file so that jar1 is different from jar2.
        jarProject2.addFile(new TestSourceFile("src/main/resources", "dummy2.txt", "bar"))
    }

    @ClassRule
    public static GradleTestProject jar1 = GradleTestProject.builder()
            .fromTestApp(jarProject1)
            .withName("jar1")
            .create()
    @ClassRule
    public static GradleTestProject jar2 = GradleTestProject.builder()
            .fromTestApp(jarProject2)
            .withName("jar2")
            .create()

    @BeforeClass
    static void createJars() {
        jar1.execute("assemble")
        jar2.execute("assemble")
    }


    // Main test project.
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Before
    void setUp() {
        Files.copy(jar1.file("build/libs/jar1.jar"), project.file("jar1.jar"))
        Files.copy(jar2.file("build/libs/jar2.jar"), project.file("jar2.jar"))

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""

    }

    @Test
    void "check pickFirst"() {
        project.getBuildFile() << """
android {
    packagingOptions {
        pickFirst 'conflict.txt'
    }
}

dependencies {
    compile files('jar1.jar')
    compile files('jar2.jar')
}
"""
        project.execute("clean", "assembleDebug")
        assertThatZip(project.getApk("debug")).contains("conflict.txt")
    }

    @Test
    void "check exclude on jars"() {
        project.getBuildFile() << """
android {
    packagingOptions {
        exclude 'conflict.txt'
    }
}

dependencies {
    compile files('jar1.jar')
    compile files('jar2.jar')
}
"""
        project.execute("clean", "assembleDebug")
        assertThatZip(project.getApk("debug")).doesNotContain("conflict.txt")
    }

    @Test
    void "check exclude on direct files"() {
        project.getBuildFile() << """
android {
    packagingOptions {
        exclude 'conflict.txt'
    }
}
"""
        createFile('src/main/resources/conflict.txt')
        project.execute("clean", "assembleDebug")
        assertThatZip(project.getApk("debug")).doesNotContain('conflict.txt')
    }

    @Test
    void "check merge on jar entries"() {
        project.getBuildFile() << """
android {
    packagingOptions {
        merge 'conflict.txt'
    }
}

dependencies {
    compile files('jar1.jar')
    compile files('jar2.jar')
}
"""
        project.execute("clean", "assembleDebug")

        assertThatZip(project.getApk("debug")).containsFileWithContent("conflict.txt", "foofoo")
    }

    @Test
    void "check merge on local res file"() {
        project.getBuildFile() << """
android {
    packagingOptions {
        // this will not be used since debug will override the main one.
        merge 'file.txt'
    }
}
"""
        createFile('src/main/resources/file.txt') << "main"
        createFile('src/debug/resources/file.txt') << "debug"
        project.execute("clean", "assembleDebug")
        assertThatZip(project.getApk("debug")).containsFileWithContent("file.txt", "debug")
    }

    @Test
    void "check merge on a direct file and a jar entry"() {
        project.getBuildFile() << """
dependencies {
    compile files('jar1.jar')
}
"""
        createFile('src/main/resources/conflict.txt') << "project-foo"
        project.execute("clean", "assembleDebug")
        // we expect to only see the one in src/main because it overrides the dependency one.
        assertThatZip(project.getApk("debug")).containsFileWithContent("conflict.txt", "project-foo")
    }

    /**
     * Create a new empty file including its directories.
     */
    private File createFile(String filename) {
        File newFile = project.file(filename)
        newFile.getParentFile().mkdirs()
        newFile.createNewFile()
        assertThat(newFile).exists()
        return newFile
    }
}
