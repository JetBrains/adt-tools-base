/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldAppWithJavaLibs;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Testing the merging of the java resources
 */
@RunWith(FilterableParameterized.class)
public class JavaResMergePackagingTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldAppWithJavaLibs.createWithLibs(2))
            .create();

    private static final String APP_IMPL = "com.example.helloworld.AppServiceImpl";

    private static final String LIB1_IMPL = "com.example.helloworld.LibServiceImpl";

    private static final String LIB2_IMPL = "com.example.helloworld.Lib2ServiceImpl";

    private static final String META_INF_SERVICES =
            "META-INF/services/com.example.helloworld.IService";

    @Before
    public void addResources() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "subprojects { apply from: \"$rootDir/../commonLocalRepo.gradle\"}");

        GradleTestProject appProject = project.getSubproject(":app");
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "dependencies{\n"
                        + "    compile project (':lib1')\n"
                        + "    compile project (':lib2')\n"
                        + "    compile 'org.bouncycastle:bcprov-jdk16:1.46'\n"
                        + "}\n");

        FileUtils.createFile(appProject.file("src/main/resources/" + META_INF_SERVICES), APP_IMPL);
        FileUtils.createFile(project.getSubproject(":lib1")
                .file("src/main/resources/" + META_INF_SERVICES), LIB1_IMPL);
        FileUtils.createFile(project.getSubproject(":lib2")
                .file("src/main/resources/" + META_INF_SERVICES), LIB2_IMPL);
    }

    @Test
    public void checkMergeAppWithLibs() throws IOException, ProcessException {
        assembleDebug();
        File apk = project.getSubproject(":app").getApk("debug");

        // In tests, newline is standardized on \n
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, APP_IMPL);
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, LIB1_IMPL);
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, LIB2_IMPL);
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, ".+\n.+\n.+");

        // Make sure we don't package signatures from dependencies.
        assertThatApk(apk).doesNotContainJavaResource("META-INF/BCKEY.SF");
        assertThatApk(apk).doesNotContainJavaResource("META-INF/BCKEY.DSA");

        // Make sure we don't package maven metadata from dependencies.
        assertThatApk(apk)
                .doesNotContainJavaResource("META-INF/maven/com.google.guava/guava/pom.xml");
    }

    @Test
    public void checkMergeOnlyLibs() throws IOException, ProcessException {
        GradleTestProject appProject = project.getSubproject(":app");
        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "\n" +
                        "android {\n" +
                        "    packagingOptions{\n" +
                        "        merge '" + META_INF_SERVICES + "'\n" +
                        "    }\n" +
                        "}\n");

        FileUtils.delete(appProject.file("src/main/resources/" + META_INF_SERVICES));

        assembleDebug();
        File apk = project.getSubproject(":app").getApk("debug");

        // in tests, newline is standardized on \n
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, LIB1_IMPL);
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, LIB2_IMPL);
        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, ".+\n.+");
    }

    @Test
    public void checkProjectPrecedenceForOtherPaths() throws IOException, ProcessException {
        String resPath = "my/data/file.txt";

        GradleTestProject appProject = project.getSubproject(":app");
        FileUtils.createFile(appProject.file("src/main/resources/" + resPath), "appData");

        GradleTestProject libProject = project.getSubproject(":lib1");
        FileUtils.createFile(libProject.file("src/main/resources/" + resPath), "lib1Data");

        assembleDebug();
        File apk = appProject.getApk("debug");

        assertThatApk(apk).containsJavaResourceWithContent(resPath, "appData");
    }

    @Test
    public void checkProjectSelectedWhenPickFirst() throws IOException, ProcessException {
        GradleTestProject appProject = project.getSubproject(":app");

        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "\n" +
                "android {\n" +
                "    packagingOptions{\n" +
                "        pickFirst '" + META_INF_SERVICES + "'\n" +
                "    }\n" +
                "}\n");

        assembleDebug();
        File apk = project.getSubproject(":app").getApk("debug");

        assertThatApk(apk).containsFileWithMatch(META_INF_SERVICES, APP_IMPL);
    }

    @Test
    public void checkNoNewlineAddedForOtherPaths() throws IOException, ProcessException {
        String resPath = "my/data/file.txt";

        GradleTestProject appProject = project.getSubproject(":app");
        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "\n" +
                "android {\n" +
                "    packagingOptions{\n" +
                "        merge 'my/data/file.txt'\n" +
                "    }\n" +
                "}\n");

        GradleTestProject lib1Project = project.getSubproject(":lib1");
        FileUtils.createFile(lib1Project.file("src/main/resources/" + resPath), "lib1Data");
        GradleTestProject lib2Project = project.getSubproject(":lib2");
        FileUtils.createFile(lib2Project.file("src/main/resources/" + resPath), "lib2Data");

        assembleDebug();
        File apk = appProject.getApk("debug");

        assertThatApk(apk).containsJavaResource(resPath);
        assertThatApk(apk).containsFileWithMatch(resPath, "[^(\n)]");
    }

    private void assembleDebug() {
        project.executor().withPackaging(mPackaging).run("clean", ":app:assembleDebug");
    }
}

