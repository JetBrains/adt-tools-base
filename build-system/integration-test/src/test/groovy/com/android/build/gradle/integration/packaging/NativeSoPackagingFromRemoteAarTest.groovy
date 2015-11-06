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

package com.android.build.gradle.integration.packaging
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * test for packaging of asset files.
 */
@CompileStatic
class NativeSoPackagingFromRemoteAarTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    private GradleTestProject appProject
    private GradleTestProject libProject

    @Before
    void setUp() {
        appProject = project.getSubproject('app')
        libProject = project.getSubproject('library')

        // rewrite settings.gradle to remove un-needed modules
        Files.write("""
include 'app'
include 'library'
""", new File(project.getTestDir(), "settings.gradle"), Charsets.UTF_8)

        // setup dependencies.
        appProject.getBuildFile() << """
repositories {
    maven { url '../testrepo' }
}
dependencies {
    compile 'com.example.android.nativepackaging:library:1.0'
}
"""

        libProject.getBuildFile() << """
apply plugin: 'maven'

group = 'com.example.android.nativepackaging'
archivesBaseName = 'library'
version = '1.0'

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("../testrepo"))
        }
    }
}

"""

        // put some default files in the library project, to check non incremental packaging
        // as well, and to provide files to change to test incremental support.
        File libDir = libProject.getTestDir()
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd")
        createOriginalSoFile(libDir,  "main",        "liblibrary2.so",     "library2:abcdef")

        // build and deploy the library
        project.execute(ImmutableList.of("--configure-on-demand"), "library:clean", "library:uploadArchives" )
        project.execute("app:clean", "app:assembleDebug")
    }

    private static void createOriginalSoFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content) {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "jniLibs", "x86")
        FileUtils.mkdirs(assetFolder)
        new File(assetFolder, filename) << content;
    }

    @Test
    void "test non incremental packaging"() {
        checkApk(appProject, "liblibrary.so",      "library:abcd")
        checkApk(appProject, "liblibrary2.so",     "library2:abcdef")
    }

    @Test
    void "test app project with new so in aar"() {
        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            project.execute("library:uploadArchives")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libnewapp.so", "newfile content")
        }
    }

    @Test
    void "test app project with removed so in aar"() {
        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/jniLibs/x86/liblibrary2.so");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            project.execute("library:uploadArchives")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary2.so", null)
        }
    }

    @Test
    void "test app project with edited so in aar"() {
        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/jniLibs/x86/liblibrary2.so", "new content");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            project.execute("library:uploadArchives")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary2.so", "new content")
        }
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        check(assertThatApk(project.getApk("debug")), "lib", filename, content)
    }

    /**
     * check a test apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkTestApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        check(assertThatApk(project.getTestApk("debug")), "lib", filename, content)
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        check(assertThatAar(project.getAar("debug")), "jni", filename, content)
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String folderName,
            @NonNull String filename,
            @Nullable String content) {
        if (content != null) {
            subject.containsFileWithContent(folderName + "/x86/" + filename, content)
        } else {
            subject.doesNotContain(folderName + "/x86/" + filename)
        }
    }
}
