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
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * test for packaging of android asset files.
 *
 * This only uses raw files. This is not about running aapt tests, this is only about
 * everythink around it, so raw files are easier to test in isolation.
 */
@CompileStatic
class ResPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    private GradleTestProject appProject
    private GradleTestProject libProject
    private GradleTestProject libProject2
    private GradleTestProject testProject

    @Before
    void setUp() {
        appProject = project.getSubproject('app')
        libProject = project.getSubproject('library')
        libProject2 = project.getSubproject('library2')
        testProject = project.getSubproject('test')

        // rewrite settings.gradle to remove un-needed modules
        project.settingsFile.text = """
include 'app'
include 'library'
include 'library2'
include 'test'
"""

        // setup dependencies.
        appProject.getBuildFile() << """
android {
    publishNonDefault true
}

dependencies {
    compile project(':library')
}
"""

        libProject.getBuildFile() << """
dependencies {
    compile project(':library2')
}
"""

        testProject.getBuildFile() << """
android {
    targetProjectPath ':app'
    targetVariant 'debug'
}
"""

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir()
        createOriginalResFile(appDir,  "main",        "file.txt",         "app:abcd")
        createOriginalResFile(appDir,  "androidTest", "filetest.txt",     "appTest:abcd")

        File testDir = testProject.getTestDir()
        createOriginalResFile(testDir, "main",        "file.txt",         "test:abcd")

        File libDir = libProject.getTestDir()
        createOriginalResFile(libDir,  "main",        "filelib.txt",      "library:abcd")
        createOriginalResFile(libDir,  "androidTest", "filelibtest.txt",  "libraryTest:abcd")

        File lib2Dir = libProject2.getTestDir()
        createOriginalResFile(lib2Dir, "main",        "filelib2.txt",     "library2:abcd")
        createOriginalResFile(lib2Dir, "androidTest", "filelib2test.txt", "library2Test:abcd")
    }

    @After
    void cleanUp() {
        project = null
        appProject = null
        testProject = null
        libProject = null
        libProject2 = null
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content) {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "res", "raw")
        FileUtils.mkdirs(assetFolder)
        new File(assetFolder, filename) << content;
    }

    @Test
    void "test non incremental packaging"() {
        project.execute("clean", "assembleDebug", "assembleAndroidTest")

        // chek the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "filelib2.txt",     "library2:abcd")
        checkTestApk(libProject2, "filelib2.txt",     "library2:abcd")
        checkTestApk(libProject2, "filelib2test.txt", "library2Test:abcd")

        checkAar(    libProject,  "filelib.txt",     "library:abcd")
        // aar does not contain dependency's assets
        checkAar(    libProject, "filelib2.txt",     null)
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "filelib.txt",      "library:abcd")
        checkTestApk(libProject, "filelib2.txt",     "library2:abcd")
        checkTestApk(libProject, "filelibtest.txt",  "libraryTest:abcd")
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "filelib2test.txt", null)

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "file.txt",         "app:abcd")
        checkApk(    appProject, "filelib.txt",      "library:abcd")
        checkApk(    appProject, "filelib2.txt",     "library2:abcd")
        checkTestApk(appProject, "filetest.txt",     "appTest:abcd")
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "filelibtest.txt",  null)
        checkTestApk(appProject, "filelib2test.txt", null)
    }

    // ---- APP DEFAULT ---

    @Test
    void "test app project with new res file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            project.execute("app:assembleDebug")

            checkApk(appProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test app project with removed res file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/main/res/raw/file.txt")
            project.execute("app:assembleDebug")

            checkApk(appProject, "file.txt", null)
        }
    }

    @Test
    void "test app project with modified res file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/main/res/raw/file.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }
    }

    @Test
    void "test app project with new debug res file overriding main"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/res/raw/file.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    void "test app project with new res file overriding dependency"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/res/raw/filelib.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("app:assembleDebug")
        checkApk(appProject, "filelib.txt", "library:abcd")
    }

    @Test
    void "test app project with new res file in debug source set"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/res/raw/file.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    void "test app project with modified res in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/res/raw/filelib.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", "new content")
        }
    }

    @Test
    void "test app project with added res in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/res/raw/new_lib_file.txt", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "new_lib_file.txt", "new content")
        }
    }

    @Test
    void "test app project with removed res in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/res/raw/filelib.txt")
            project.execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", null)
        }
    }

    // ---- APP TEST ---

    @Test
    void "test app project test with new res file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            project.execute("app:assembleAT")

            checkTestApk(appProject, "newfile.txt", "new file content")
        }
    }

    @Test
    void "test app project test with removed res file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/androidTest/res/raw/filetest.txt")
            project.execute("app:assembleAT")

            checkTestApk(appProject, "filetest.txt", null)
        }
    }

    @Test
    void "test app project test with modified res file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/androidTest/res/raw/filetest.txt", "new content")
            project.execute("app:assembleAT")

            checkTestApk(appProject, "filetest.txt", "new content")
        }
    }

    // ---- LIB DEFAULT ---

    @Test
    void "test lib project with new res file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            project.execute("library:assembleDebug")

            checkAar(libProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test lib project with removed res file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/res/raw/filelib.txt")
            project.execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", null)
        }
    }

    @Test
    void "test lib project with modified res file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/res/raw/filelib.txt", "new content")
            project.execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", "new content")
        }
    }

    @Test
    void "test lib project with new res file in debug source set"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/debug/res/raw/filelib.txt", "new content")
            project.execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("library:assembleDebug")
        checkAar(libProject, "filelib.txt", "library:abcd")
    }

    // ---- LIB TEST ---

    @Test
    void "test lib project test with new res file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            project.execute("library:assembleAT")

            checkTestApk(libProject, "newfile.txt", "new file content")
        }
    }

    @Test
    void "test lib project test with removed res file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/androidTest/res/raw/filelibtest.txt")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "filelibtest.txt", null)
        }
    }

    @Test
    void "test lib project test with modified res file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/androidTest/res/raw/filelibtest.txt", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "filelibtest.txt", "new content")
        }
    }

    @Test
    void "test lib project test with new res file overriding tested lib"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/filelib.txt", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("library:assembleAT")
        checkTestApk(libProject, "filelib.txt", "library:abcd")

    }

    @Test
    void "test lib project test with new res file overriding dependency"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/filelib2.txt", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "filelib2.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        project.execute("library:assembleAT")
        checkTestApk(libProject, "filelib2.txt", "library2:abcd")
    }

    // ---- TEST DEFAULT ---

    @Test
    void "test test-project with new res file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            project.execute("test:assembleDebug")

            checkApk(testProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test test-project with removed res file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.removeFile("src/main/res/raw/file.txt")
            project.execute("test:assembleDebug")

            checkApk(testProject, "file.txt", null)
        }
    }

    @Test
    void "test test-project with modified res file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.replaceFile("src/main/res/raw/file.txt", "new content")
            project.execute("test:assembleDebug")

            checkApk(testProject, "file.txt", "new content")
        }
    }

    // -----------------------

    /**
     * check an apk has (or not) the given res file name.
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
        check(assertThatApk(project.getApk("debug")), filename, content)
    }

    /**
     * check a test apk has (or not) the given res file name.
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
        check(assertThatApk(project.getTestApk("debug")), filename, content)
    }

    /**
     * check an aat has (or not) the given res file name.
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
        check(assertThatApk(project.getAar("debug")), filename, content)
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content) {
        if (content != null) {
            subject.containsFileWithContent("res/raw/" + filename, content)
        } else {
            subject.doesNotContainResource("raw/" + filename)
        }
    }
}
