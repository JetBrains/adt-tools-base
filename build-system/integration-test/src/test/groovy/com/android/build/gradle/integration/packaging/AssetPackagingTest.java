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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

/**
 * test for packaging of asset files.
 */
@RunWith(FilterableParameterized.class)
public class AssetPackagingTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;

    @Before
    public void setUp() throws IOException {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");

        // rewrite settings.gradle to remove un-needed modules
        Files.write(project.getSettingsFile().toPath(),
                Arrays.asList(
                        "include 'app'",
                        "include 'library'",
                        "include 'library2'",
                        "include 'test'"));

        // setup dependencies.
        TestFileUtils.appendToFile(appProject.getBuildFile(), "\n"
                + "android {\n"
                + "    publishNonDefault true\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "}");

        TestFileUtils.appendToFile(libProject.getBuildFile(), "\n"
                + "dependencies {\n"
                + "    compile project(':library2')\n"
                + "}");
        TestFileUtils.appendToFile(testProject.getBuildFile(), "\n"
                + "android {\n"
                + "    targetProjectPath ':app'\n"
                + "    targetVariant 'debug'\n"
                + "}");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir();
        createOriginalAsset(appDir, "main", "file.txt", "app:abcd");
        createOriginalAsset(appDir, "androidTest", "filetest.txt", "appTest:abcd");

        File testDir = testProject.getTestDir();
        createOriginalAsset(testDir, "main", "file.txt", "test:abcd");

        File libDir = libProject.getTestDir();
        createOriginalAsset(libDir, "main", "filelib.txt", "library:abcd");
        createOriginalAsset(libDir, "androidTest", "filelibtest.txt", "libraryTest:abcd");

        File lib2Dir = libProject2.getTestDir();
        // Include a gzipped asset, which should be extracted.
        createOriginalGzippedAsset(lib2Dir, "main", "filelib2.txt.gz",
                "library2:abcd".getBytes(Charsets.UTF_8));
        createOriginalAsset(lib2Dir, "androidTest", "filelib2test.txt", "library2Test:abcd");
    }

    @After
    public void cleanUp() {
        project = null;
        appProject = null;
        testProject = null;
        libProject = null;
        libProject2 = null;
    }

    private void execute(@NonNull String... tasks) {
        project.executor().withPackaging(mPackaging).run(tasks);
    }

    private static void createOriginalAsset(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content) throws IOException {
        createOriginalAsset(projectFolder, dimension, filename, content.getBytes(Charsets.UTF_8));
    }

    private static void createOriginalGzippedAsset(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull byte[] content) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(byteArrayOutputStream)) {
            out.write(content);
        }
        createOriginalAsset(projectFolder, dimension, filename, byteArrayOutputStream.toByteArray());
    }

    private static void createOriginalAsset(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull byte[] content) throws IOException {
        Path assetFolder = FileUtils.join(projectFolder, "src", dimension, "assets").toPath();
        Files.createDirectories(assetFolder);
        Path assetFile = assetFolder.resolve(filename);
        Files.write(assetFile, content);
    }

    @Test
    public void testNonIncrementalPackaging() throws IOException {
        execute("clean", "assembleDebug", "assembleAndroidTest");

        // chek the files are there. Start from the bottom of the dependency graph
        checkAar(libProject2, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject2, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject2, "filelib2test.txt", "library2Test:abcd");

        checkAar(libProject, "filelib.txt", "library:abcd");
        // aar does not contain dependency's assets
        checkAar(libProject, "filelib2.txt", null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "filelib.txt", "library:abcd");
        checkTestApk(libProject, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject, "filelibtest.txt", "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "filelib2test.txt", null);

        // app contain own assets + all dependencies' assets.
        checkApk(appProject, "file.txt", "app:abcd");
        checkApk(appProject, "filelib.txt", "library:abcd");
        checkApk(appProject, "filelib2.txt", "library2:abcd");
        checkTestApk(appProject, "filetest.txt", "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "filelibtest.txt", null);
        checkTestApk(appProject, "filelib2test.txt", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.removeFile("src/main/assets/file.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", null);
        });
    }

    @Test
    public void testAppProjectWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.replaceFile("src/main/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewDebugAssetFileOverridingMain() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/debug/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithNewAssetFileOverridingDependency() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "filelib.txt", "library:abcd");
    }

    @Test
    public void testAppProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/debug/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithModifiedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/main/assets/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/main/assets/new_lib_file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "new_lib_file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/main/assets/filelib.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", null);
        });
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/androidTest/assets/newfile.txt", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.removeFile("src/androidTest/assets/filetest.txt");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.replaceFile("src/androidTest/assets/filetest.txt", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/main/assets/filelib.txt");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/main/assets/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/debug/assets/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug");
        checkAar(libProject, "filelib.txt", "library:abcd");
    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/newfile.txt", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/androidTest/assets/filelibtest.txt");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/androidTest/assets/filelibtest.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/filelib.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib.txt", "library:abcd");

    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingDependency() throws Exception {
        execute("library:clean", "library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/filelib2.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib2.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib2.txt", "library2:abcd");
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("test:assembleDebug");

            checkApk(testProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testTestProjectWithRemovedAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.removeFile("src/main/assets/file.txt");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", null);
        });
    }

    @Test
    public void testTestProjectWithModifiedAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.replaceFile("src/main/assets/file.txt", "new content");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", "new content");
        });
    }

    // -----------------------

    @Test
    public void testPackageAssetsWithUnderscoreRegression() throws Exception {
        execute("app:clean", "app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/_newfile.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "_newfile.txt", "newfile content");
        });
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project  the project
     * @param filename the filename
     * @param content  the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        check(assertThatApk(project.getApk("debug")), filename, content);
    }

    /**
     * check a test apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project  the project
     * @param filename the filename
     * @param content  the content
     */
    private void checkTestApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        check(assertThatApk(project.getTestApk(mPackaging, "debug")), filename, content);
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project  the project
     * @param filename the filename
     * @param content  the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        check(assertThatAar(project.getAar("debug")), filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        if (content != null) {
            subject.containsFileWithContent("assets/" + filename, content);
        } else {
            subject.doesNotContain("assets/" + filename);
        }
    }
}
