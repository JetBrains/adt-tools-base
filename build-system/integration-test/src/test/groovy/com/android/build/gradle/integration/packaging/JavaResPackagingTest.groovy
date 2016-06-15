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
import com.android.build.gradle.integration.common.fixture.RunGradleTasks
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * test for packaging of java resources.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class JavaResPackagingTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return RunGradleTasks.Packaging.getParameters();
    }

    @Parameterized.Parameter
    public RunGradleTasks.Packaging mPackaging;

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
        project.settingsFile.text = """
include 'app'
include 'library'
include 'library2'
include 'test'
include 'jar'
"""

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
        createOriginalResFile(appDir,  "main",        "app.txt",         "app:abcd")
        createOriginalResFile(appDir,  "androidTest", "apptest.txt",     "appTest:abcd")

        File testDir = testProject.getTestDir()
        createOriginalResFile(testDir, "main",        "test.txt",        "test:abcd")

        File libDir = libProject.getTestDir()
        createOriginalResFile(libDir,  "main",        "library.txt",      "library:abcd")
        createOriginalResFile(libDir,  "androidTest", "librarytest.txt",  "libraryTest:abcd")

        File lib2Dir = libProject2.getTestDir()
        createOriginalResFile(lib2Dir, "main",        "library2.txt",     "library2:abcd")
        createOriginalResFile(lib2Dir, "androidTest", "library2test.txt", "library2Test:abcd")

        File jarDir = jarProject.getTestDir()
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "com", "foo")
        FileUtils.mkdirs(resFolder)
        new File(resFolder, "jar.txt") << "jar:abcd";
    }

    @After
    void cleanUp() {
        project = null
        appProject = null
        testProject = null
        libProject = null
        libProject2 = null
        jarProject = null
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content) {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "resources", "com", "foo")
        FileUtils.mkdirs(assetFolder)
        new File(assetFolder, filename) << content;
    }

    private void execute(String... tasks) {
        project.executor().withPackaging(mPackaging).run(tasks)
    }

    @Test
    void "test non incremental packaging"() {
        execute("clean", "assembleDebug", "assembleAndroidTest")

        // chek the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "library2.txt",     "library2:abcd")
        checkTestApk(libProject2, "library2.txt",     "library2:abcd")
        checkTestApk(libProject2, "library2test.txt", "library2Test:abcd")

        checkAar(    libProject,  "library.txt",     "library:abcd")
        // aar does not contain dependency's assets
        checkAar(    libProject, "library2.txt",     null)
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "library.txt",      "library:abcd")
        checkTestApk(libProject, "library2.txt",     "library2:abcd")
        checkTestApk(libProject, "librarytest.txt",  "libraryTest:abcd")
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "library2test.txt", null)

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "app.txt",          "app:abcd")
        checkApk(    appProject, "library.txt",      "library:abcd")
        checkApk(    appProject, "library2.txt",     "library2:abcd")
        checkApk(    appProject, "jar.txt",          "jar:abcd")
        checkTestApk(appProject, "apptest.txt",      "appTest:abcd")
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "librarytest.txt",  null)
        checkTestApk(appProject, "library2test.txt", null)
    }

    // ---- APP DEFAULT ---

    @Test
    void "test app project with new res file"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/resources/com/foo/newapp.txt", "newfile content");
            execute("app:assembleDebug")

            checkApk(appProject, "newapp.txt", "newfile content")
        }
    }

    @Test
    void "test app project with removed res file"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/main/resources/com/foo/app.txt")
            execute("app:assembleDebug")

            checkApk(appProject, "app.txt", null)
        }
    }

    @Test
    void "test app project with modified res file"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/main/resources/com/foo/app.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "app.txt", "new content")
        }
    }

    @Test
    void "test app project with new debug res file overriding main"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/resources/com/foo/app.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "app.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "app.txt", "app:abcd")
    }

    @Test
    void "test app project with new res file overriding dependency"() {
        String resourcePath = "src/main/resources/com/foo/library.txt"

        execute("app:clean", "app:assembleDebug")
        checkApk(appProject, "library.txt", "library:abcd")


        TemporaryProjectModification.doTest(appProject) {
            it.addFile(resourcePath, "new content")
            assertThat(appProject.file(resourcePath)).exists()
            execute("app:assembleDebug")

            checkApk(appProject, "library.txt", "new content")
        }

        // Trying to figure out why the test is flaky?
        assertThat(appProject.file(resourcePath)).doesNotExist()

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "library.txt", "library:abcd")
    }

    @Test
    void "test app project with new res file in debug source set"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/resources/com/foo/app.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "app.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "app.txt", "app:abcd")
    }

    @Test
    void "test app project with modified res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/resources/com/foo/library.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "library.txt", "new content")
        }
    }

    @Test
    void "test app project with added res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/resources/com/foo/newlibrary.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "newlibrary.txt", "new content")
        }
    }

    @Test
    void "test app project with removed res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/resources/com/foo/library.txt")
            execute("app:assembleDebug")

            checkApk(appProject, "library.txt", null)
        }
    }

    // ---- APP TEST ---

    @Test
    void "test app project test with new res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/androidTest/resources/com/foo/newapp.txt", "new file content");
            execute("app:assembleAT")

            checkTestApk(appProject, "newapp.txt", "new file content")
        }
    }

    @Test
    void "test app project test with removed res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/androidTest/resources/com/foo/apptest.txt")
            execute("app:assembleAT")

            checkTestApk(appProject, "apptest.txt", null)
        }
    }

    @Test
    void "test app project test with modified res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/androidTest/resources/com/foo/apptest.txt", "new content")
            execute("app:assembleAT")

            checkTestApk(appProject, "apptest.txt", "new content")
        }
    }

    // ---- LIB DEFAULT ---

    @Test
    void "test lib project with new res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/resources/com/foo/newlibrary.txt", "newfile content");
            execute("library:assembleDebug")

            checkAar(libProject, "newlibrary.txt", "newfile content")
        }
    }

    @Test
    void "test lib project with removed res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/resources/com/foo/library.txt")
            execute("library:assembleDebug")

            checkAar(libProject, "library.txt", null)
        }
    }

    @Test
    void "test lib project with modified res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/resources/com/foo/library.txt", "new content")
            execute("library:assembleDebug")

            checkAar(libProject, "library.txt", "new content")
        }
    }

    @Test
    void "test lib project with new res file in debug source set"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/debug/resources/com/foo/library.txt", "new content")
            execute("library:assembleDebug")

            checkAar(libProject, "library.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug")
        checkAar(libProject, "library.txt", "library:abcd")

    }

    // ---- LIB TEST ---

    @Test
    void "test lib project test with new res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/resources/com/foo/newlibrary.txt", "new file content");
            execute("library:assembleAT")

            checkTestApk(libProject, "newlibrary.txt", "new file content")
        }
    }

    @Test
    void "test lib project test with removed res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/androidTest/resources/com/foo/librarytest.txt")
            execute("library:assembleAT")

            checkTestApk(libProject, "librarytest.txt", null)
        }
    }

    @Test
    void "test lib project test with modified res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/androidTest/resources/com/foo/librarytest.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "librarytest.txt", "new content")
        }
    }

    @Test
    void "test lib project test with new res file overriding tested lib"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/resources/com/foo/library.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "library.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "library.txt", "library:abcd")
    }

    @Test
    void "test lib project test with new res file overriding dependency"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/resources/com/foo/library2.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "library2.txt", "new content")

        }

        // file's been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "library2.txt", "library2:abcd")
    }

    // ---- TEST DEFAULT ---

    @Test
    void "test test-project with new res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.addFile("src/main/resources/com/foo/newtest.txt", "newfile content");
            execute("test:assembleDebug")

            checkApk(testProject, "newtest.txt", "newfile content")
        }
    }

    @Test
    void "test test-project with removed res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.removeFile("src/main/resources/com/foo/test.txt")
            execute("test:assembleDebug")

            checkApk(testProject, "test.txt", null)
        }
    }

    @Test
    void "test test-project with modified res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.replaceFile("src/main/resources/com/foo/test.txt", "new content")
            execute("test:assembleDebug")

            checkApk(testProject, "test.txt", "new content")
        }
    }

    // --------------------------------

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
    private void checkTestApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        check(assertThatApk(project.getTestApk(mPackaging, "debug")), filename, content)
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
        check(assertThatAar(project.getAar("debug")), filename, content)
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content) {
        if (content != null) {
            subject.containsJavaResourceWithContent("com/foo/" + filename, content)
        } else {
            subject.doesNotContainJavaResource("com/foo/" + filename)
        }
    }
}
