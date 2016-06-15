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
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.nio.charset.Charset

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * test for packaging of android asset files.
 *
 * This only uses raw files. This is not about running aapt tests, this is only about
 * everything around it, so raw files are easier to test in isolation.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class ResPackagingTest {

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

    private void execute(String... tasks) {
        project.executor().withPackaging(mPackaging).run(tasks)
    }

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
        execute("clean", "assembleDebug", "assembleAndroidTest")

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
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("app:assembleDebug")

            checkApk(appProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test app project with removed res file"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/main/res/raw/file.txt")
            execute("app:assembleDebug")

            checkApk(appProject, "file.txt", null)
        }
    }

    @Test
    void "test app project with modified res file"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/main/res/raw/file.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }
    }

    @Test
    void "test app project with new debug res file overriding main"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/res/raw/file.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    void "test app project with new res file overriding dependency"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/main/res/raw/filelib.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "filelib.txt", "library:abcd")
    }

    @Test
    void "test app project with new res file in debug source set"() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/debug/res/raw/file.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    void "test app project with modified res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/res/raw/filelib.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", "new content")
        }
    }

    @Test
    void "test app project with added res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/res/raw/new_lib_file.txt", "new content")
            execute("app:assembleDebug")

            checkApk(appProject, "new_lib_file.txt", "new content")
        }
    }

    @Test
    void "test app project with removed res in dependency"() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/res/raw/filelib.txt")
            execute("app:assembleDebug")

            checkApk(appProject, "filelib.txt", null)
        }
    }

    @Test
    void "test app resources are filtered by min sdk full"() {
        // Here are which files go into where:
        //  (none)  v14     v16
        //  f1
        //  f2      f2
        //  f3      f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build without minSdk defined, we should get everything exactly as shown.
        //
        // If we build with minSdkVersion = 14 we should end up with:
        // (none)   v14     v16
        //  f1
        //          f2
        //          f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build with minSdkVersion = 16 we should end up with:
        // (none)   v14     v16
        //  f1
        //          f2
        //                  f3
        //                  f4
        //                  f5

        File raw = appProject.file("src/main/res/raw")
        raw.mkdirs()

        File raw14 = appProject.file("src/main/res/raw-v14")
        raw14.mkdirs()

        File raw16 = appProject.file("src/main/res/raw-v16")
        raw16.mkdirs()

        byte[] f1NoneC = [ 0 ] as byte[]
        byte[] f2NoneC = [ 1 ] as byte[]
        byte[] f2v14C = [ 2 ] as byte[]
        byte[] f3NoneC = [ 3 ] as byte[]
        byte[] f3v14C = [ 4 ] as byte[]
        byte[] f3v16C = [ 5 ] as byte[]
        byte[] f4v14C = [ 6 ] as byte[]
        byte[] f4v16C = [ 7 ] as byte[]
        byte[] f5v16C = [ 8 ] as byte[]

        File f1None = new File(raw, "f1")
        Files.write(f1NoneC, f1None)

        File f2None = new File(raw, "f2")
        Files.write(f2NoneC, f2None)

        File f2v14 = new File(raw14, "f2")
        Files.write(f2v14C, f2v14);

        File f3None = new File(raw, "f3")
        Files.write(f3NoneC, f3None)

        File f3v14 = new File(raw14, "f3")
        Files.write(f3v14C, f3v14)

        File f3v16 = new File(raw16, "f3")
        Files.write(f3v16C, f3v16)

        File f4v14 = new File(raw14, "f4")
        Files.write(f4v14C, f4v14)

        File f4v16 = new File(raw16, "f4")
        Files.write(f4v16C, f4v16)

        File f5v16 = new File(raw16, "f5")
        Files.write(f5v16C, f5v16)


        File appGradleFile = appProject.file("build.gradle")
        String appGradleFileContents = Files.toString(appGradleFile, Charset.defaultCharset())

        // Set no min SDK version and generate the APK.
        String newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute("clean", ":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f2", f2NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f3", f3NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f3", f3v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f4", f4v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)

        // Set min SDK version 14 and generate the APK.
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 14")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute("clean", ":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f3", f3v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f4", f4v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)

        // Set min SDK version 16 and generate the APK.
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 16")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute("clean", ":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw-v14/f3")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw-v14/f4")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)
    }

    @Test
    void "test app resources are filtered by min sdk incremental"() {
        // Note: this test is very similar to the previous one but, instead of trying all 3
        // versions independently, we start with min SDK 14, then change to no min SDK and set
        // min SDK to 16. The outputs should be the same as in the previous test.

        File raw = appProject.file("src/main/res/raw")
        raw.mkdirs()

        File raw14 = appProject.file("src/main/res/raw-v14")
        raw14.mkdirs()

        File raw16 = appProject.file("src/main/res/raw-v16")
        raw16.mkdirs()

        byte[] f1NoneC = [ 0 ] as byte[]
        byte[] f2NoneC = [ 1 ] as byte[]
        byte[] f2v14C = [ 2 ] as byte[]
        byte[] f3NoneC = [ 3 ] as byte[]
        byte[] f3v14C = [ 4 ] as byte[]
        byte[] f3v16C = [ 5 ] as byte[]
        byte[] f4v14C = [ 6 ] as byte[]
        byte[] f4v16C = [ 7 ] as byte[]
        byte[] f5v16C = [ 8 ] as byte[]

        File f1None = new File(raw, "f1")
        Files.write(f1NoneC, f1None)

        File f2None = new File(raw, "f2")
        Files.write(f2NoneC, f2None)

        File f2v14 = new File(raw14, "f2")
        Files.write(f2v14C, f2v14);

        File f3None = new File(raw, "f3")
        Files.write(f3NoneC, f3None)

        File f3v14 = new File(raw14, "f3")
        Files.write(f3v14C, f3v14)

        File f3v16 = new File(raw16, "f3")
        Files.write(f3v16C, f3v16)

        File f4v14 = new File(raw14, "f4")
        Files.write(f4v14C, f4v14)

        File f4v16 = new File(raw16, "f4")
        Files.write(f4v16C, f4v16)

        File f5v16 = new File(raw16, "f5")
        Files.write(f5v16C, f5v16)


        File appGradleFile = appProject.file("build.gradle")
        String appGradleFileContents = Files.toString(appGradleFile, Charset.defaultCharset())

        // Set min SDK version 14 and generate the APK.
        String newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 14")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute("clean", ":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f3", f3v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f4", f4v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)

        // Set no min SDK version and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute(":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f2", f2NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f3", f3NoneC)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f3", f3v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f4", f4v14C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)

        // Set min SDK version 16 and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 16")
        Files.write(newBuild, appGradleFile, Charset.defaultCharset())
        execute(":app:assembleDebug")

        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v14/f2", f2v14C)
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw-v14/f3")
        assertThatApk(appProject.getApk("debug")).doesNotContain("res/raw-v14/f4")
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f3", f3v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f4", f4v16C)
        assertThatApk(appProject.getApk("debug")).containsFileWithContent("res/raw-v16/f5", f5v16C)
    }

    // ---- APP TEST ---

    @Test
    void "test app project test with new res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            execute("app:assembleAT")

            checkTestApk(appProject, "newfile.txt", "new file content")
        }
    }

    @Test
    void "test app project test with removed res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.removeFile("src/androidTest/res/raw/filetest.txt")
            execute("app:assembleAT")

            checkTestApk(appProject, "filetest.txt", null)
        }
    }

    @Test
    void "test app project test with modified res file"() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) {
            it.replaceFile("src/androidTest/res/raw/filetest.txt", "new content")
            execute("app:assembleAT")

            checkTestApk(appProject, "filetest.txt", "new content")
        }
    }

    // ---- LIB DEFAULT ---

    @Test
    void "test lib project with new res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("library:assembleDebug")

            checkAar(libProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test lib project with removed res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/main/res/raw/filelib.txt")
            execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", null)
        }
    }

    @Test
    void "test lib project with modified res file"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/main/res/raw/filelib.txt", "new content")
            execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", "new content")
        }
    }

    @Test
    void "test lib project with new res file in debug source set"() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/debug/res/raw/filelib.txt", "new content")
            execute("library:assembleDebug")

            checkAar(libProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug")
        checkAar(libProject, "filelib.txt", "library:abcd")
    }

    // ---- LIB TEST ---

    @Test
    void "test lib project test with new res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            execute("library:assembleAT")

            checkTestApk(libProject, "newfile.txt", "new file content")
        }
    }

    @Test
    void "test lib project test with removed res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.removeFile("src/androidTest/res/raw/filelibtest.txt")
            execute("library:assembleAT")

            checkTestApk(libProject, "filelibtest.txt", null)
        }
    }

    @Test
    void "test lib project test with modified res file"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.replaceFile("src/androidTest/res/raw/filelibtest.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "filelibtest.txt", "new content")
        }
    }

    @Test
    void "test lib project test with new res file overriding tested lib"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/filelib.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "filelib.txt", "new content")
        }

        // files been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "filelib.txt", "library:abcd")

    }

    @Test
    void "test lib project test with new res file overriding dependency"() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) {
            it.addFile("src/androidTest/res/raw/filelib2.txt", "new content")
            execute("library:assembleAT")

            checkTestApk(libProject, "filelib2.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "filelib2.txt", "library2:abcd")
    }

    // ---- TEST DEFAULT ---

    @Test
    void "test test-project with new res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("test:assembleDebug")

            checkApk(testProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    void "test test-project with removed res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.removeFile("src/main/res/raw/file.txt")
            execute("test:assembleDebug")

            checkApk(testProject, "file.txt", null)
        }
    }

    @Test
    void "test test-project with modified res file"() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) {
            it.replaceFile("src/main/res/raw/file.txt", "new content")
            execute("test:assembleDebug")

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
