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
class NativeSoPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    private GradleTestProject appProject
    private GradleTestProject libProject
    private GradleTestProject libProject2
    private GradleTestProject testProject
    private GradleTestProject jarProject

    @Before
    void setUp() {
        appProject = project.getSubproject('app')
        libProject = project.getSubproject('library')
        libProject2 = project.getSubproject('library2')
        testProject = project.getSubproject('test')
        jarProject = project.getSubproject('jar')

        // rewrite settings.gradle to remove un-needed modules
        Files.write("""
include 'app'
include 'library'
include 'library2'
include 'test'
include 'jar'
""", new File(project.getTestDir(), "settings.gradle"), Charsets.UTF_8)

        // setup dependencies.
        appProject.getBuildFile() << """
android {
    publishNonDefault true
}

dependencies {
    compile project(':library')
    compile project(':jar')
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
        createOriginalSoFile(appDir,  "main",        "libapp.so",         "app:abcd")
        createOriginalSoFile(appDir,  "androidTest", "libapptest.so",     "appTest:abcd")

        File testDir = testProject.getTestDir()
        createOriginalSoFile(testDir, "main",        "libtest.so",        "test:abcd")

        File libDir = libProject.getTestDir()
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd")
        createOriginalSoFile(libDir,  "androidTest", "liblibrarytest.so",  "libraryTest:abcd")

        File lib2Dir = libProject2.getTestDir()
        createOriginalSoFile(lib2Dir, "main",        "liblibrary2.so",     "library2:abcd")
        createOriginalSoFile(lib2Dir, "androidTest", "liblibrary2test.so", "library2Test:abcd")

        File jarDir = jarProject.getTestDir()
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "lib", "x86")
        FileUtils.mkdirs(resFolder)
        new File(resFolder, "libjar.so") << "jar:abcd";
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
        project.execute("clean", "assembleDebug", "assembleAndroidTest")

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "liblibrary2.so",     "library2:abcd")
        checkTestApk(libProject2, "liblibrary2.so",     "library2:abcd")
        checkTestApk(libProject2, "liblibrary2test.so", "library2Test:abcd")

        checkAar(    libProject,  "liblibrary.so",     "library:abcd")
        // aar does not contain dependency's assets
        checkAar(    libProject, "liblibrary2.so",     null)
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "liblibrary.so",      "library:abcd")
        checkTestApk(libProject, "liblibrary2.so",     "library2:abcd")
        checkTestApk(libProject, "liblibrarytest.so",  "libraryTest:abcd")
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "liblibrary2test.so", null)

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "libapp.so",         "app:abcd")
        checkApk(    appProject, "liblibrary.so",      "library:abcd")
        checkApk(    appProject, "liblibrary2.so",     "library2:abcd")
        checkApk(    appProject, "libjar.so",          "jar:abcd")
        checkTestApk(appProject, "libapptest.so",     "appTest:abcd")
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "liblibrarytest.so",  null)
        checkTestApk(appProject, "liblibrary2test.so", null)
    }

    // ---- APP DEFAULT ---

    @Test
    void "test app project with new asset file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
            project.execute("app:assembleDebug")

            checkApk(appProject, "libnewapp.so", "newfile content")
        }
    }

    @Test
    void "test app project with removed asset file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/main/jniLibs/x86/libapp.so")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libapp.so", null)
        }
    }

    @Test
    void "test app project with modified asset file"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/main/jniLibs/x86/libapp.so", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libapp.so", "new content")
        }
    }

    @Test
    void "test app project with new asset file overriding dependency"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/jniLibs/x86/liblibrary.so", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary.so", "new content")

            // now remove it to test it works in the other direction
            it.removeFile("src/main/jniLibs/x86/liblibrary.so")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary.so", "library:abcd")
        }
    }

    @Test
    void "test app project with new asset file in debug source set"() {
        project.execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/jniLibs/x86/libapp.so", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libapp.so", "new content")

            // now remove it to test it works in the other direction
            it.removeFile("src/debug/jniLibs/x86/libapp.so")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libapp.so", "app:abcd")
        }
    }

    @Test
    void "test app project with modified asset in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary.so", "new content")
        }
    }

    @Test
    void "test app project with added asset in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/jniLibs/x86/libnewlibrary.so", "new content")
            project.execute("app:assembleDebug")

            checkApk(appProject, "libnewlibrary.so", "new content")
        }
    }

    @Test
    void "test app project with removed asset in dependency"() {
        project.execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/jniLibs/x86/liblibrary.so")
            project.execute("app:assembleDebug")

            checkApk(appProject, "liblibrary.so", null)
        }
    }

    // ---- APP TEST ---

    @Test
    void "test app project test with new asset file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/androidTest/jniLibs/x86/libnewapp.so", "new file content");
            project.execute("app:assembleAT")

            checkTestApk(appProject, "libnewapp.so", "new file content")
        }
    }

    @Test
    void "test app project test with removed asset file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/androidTest/jniLibs/x86/libapptest.so")
            project.execute("app:assembleAT")

            checkTestApk(appProject, "libapptest.so", null)
        }
    }

    @Test
    void "test app project test with modified asset file"() {
        project.execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/androidTest/jniLibs/x86/libapptest.so", "new content")
            project.execute("app:assembleAT")

            checkTestApk(appProject, "libapptest.so", "new content")
        }
    }

    // ---- LIB DEFAULT ---

    @Test
    void "test lib project with new asset file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/jniLibs/x86/libnewlibrary.so", "newfile content");
            project.execute("library:assembleDebug")

            checkAar(libProject, "libnewlibrary.so", "newfile content")
        }
    }

    @Test
    void "test lib project with removed asset file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/jniLibs/x86/liblibrary.so")
            project.execute("library:assembleDebug")

            checkAar(libProject, "liblibrary.so", null)
        }
    }

    @Test
    void "test lib project with modified asset file"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content")
            project.execute("library:assembleDebug")

            checkAar(libProject, "liblibrary.so", "new content")
        }
    }

    @Test
    void "test lib project with new asset file in debug source set"() {
        project.execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/debug/jniLibs/x86/liblibrary.so", "new content")
            project.execute("library:assembleDebug")

            checkAar(libProject, "liblibrary.so", "new content")

            // now remove it to test it works in the other direction
            it.removeFile("src/debug/jniLibs/x86/liblibrary.so")
            project.execute("library:assembleDebug")

            checkAar(libProject, "liblibrary.so", "library:abcd")
        }
    }

    // ---- LIB TEST ---

    @Test
    void "test lib project test with new asset file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/jniLibs/x86/libnewlibrary.so", "new file content");
            project.execute("library:assembleAT")

            checkTestApk(libProject, "libnewlibrary.so", "new file content")
        }
    }

    @Test
    void "test lib project test with removed asset file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/androidTest/jniLibs/x86/liblibrarytest.so")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrarytest.so", null)
        }
    }

    @Test
    void "test lib project test with modified asset file"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/androidTest/jniLibs/x86/liblibrarytest.so", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrarytest.so", "new content")
        }
    }

    @Test
    void "test lib project test with new asset file overriding tested lib"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/jniLibs/x86/liblibrary.so", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrary.so", "new content")

            // now remove it to test it works in the other direction
            it.removeFile("src/androidTest/jniLibs/x86/liblibrary.so")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrary.so", "library:abcd")
        }
    }

    @Test
    void "test lib project test with new asset file overriding dependency"() {
        project.execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/jniLibs/x86/liblibrary2.so", "new content")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrary2.so", "new content")

            // now remove it to test it works in the other direction
            it.removeFile("src/androidTest/jniLibs/x86/liblibrary2.so")
            project.execute("library:assembleAT")

            checkTestApk(libProject, "liblibrary2.so", "library2:abcd")
        }
    }

    // ---- TEST DEFAULT ---

    @Test
    void "test test-project with new asset file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.addFile("src/main/jniLibs/x86/libnewtest.so", "newfile content");
            project.execute("test:assembleDebug")

            checkApk(testProject, "libnewtest.so", "newfile content")
        }
    }

    @Test
    void "test test-project with removed asset file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.removeFile("src/main/jniLibs/x86/libtest.so")
            project.execute("test:assembleDebug")

            checkApk(testProject, "libtest.so", null)
        }
    }

    @Test
    void "test test-project with modified asset file"() {
        project.execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.replaceFile("src/main/jniLibs/x86/libtest.so", "new content")
            project.execute("test:assembleDebug")

            checkApk(testProject, "libtest.so", "new content")
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
