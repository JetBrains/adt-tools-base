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

package com.android.build.gradle.integration.instant;

import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.truth.FileSubject;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hot swap test for sub projects.
 */
public class LibDependencyTest {

    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                .fromTestProject("libDependency")
                .create();

    @Rule
    public Expect expect = Expect.create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Before
    public void activityClass() throws IOException {
        createLibraryClass("Before");
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws IOException, ProcessException {
        System.out.printf("ABCDEFGH------------------------------------------------->\n");
        sProject.execute("clean");
        Map<String, AndroidProject> projects = sProject.getAllModels();
        AndroidProject project = projects.get(":app");
        InstantRun instantRunModel = getInstantRunModel(project);

        // Check that original class is included.
        sProject.execute(getInstantRunArgs(), "clean", "assembleRelease", "assembleDebug");
        expect.about(ApkSubject.FACTORY)
                .that(sProject.getSubproject("app").getApk("debug")).getDexFile("classes.dex")
                .that().hasClass("Lcom/android/tests/libstest/lib/MainActivity;")
                .that().hasMethod("onCreate");

        checkHotSwapCompatibleChange(instantRunModel);
        System.out.printf("<------------------------------------------------ABCDEFGH\n");
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(InstantRun instantRunModel)
            throws IOException, ProcessException {
        createLibraryClass("Hot swap change");

        sProject.execute(getInstantRunArgs(), instantRunModel.getIncrementalAssembleTaskName());

        expect.about(DexFileSubject.FACTORY)
                .that(instantRunModel.getReloadDexFile())
                .hasClass("Lcom/android/tests/libstest/lib/Lib$override;");

        // the restart .dex should not be present.
        expect.about(FileSubject.FACTORY).that(instantRunModel.getRestartDexFile()).doesNotExist();
    }


    private static void createLibraryClass(String message)
            throws IOException {
        String javaCompile = "package com.android.tests.libstest.lib;\n"
            +"public class Lib {\n"
                +"public static String someString() {\n"
                  +"return \"someStringMessage=" + message + "\";\n"
                +"}\n"
            +"}\n";
        Files.write(javaCompile,
                sProject.file("lib/src/main/java/com/android/tests/libstest/lib/Lib.java"),
                Charsets.UTF_8);
    }

    private static List<String> getInstantRunArgs(OptionalCompilationStep... flags) {
        String property = "-P" + AndroidProject.OPTIONAL_COMPILATION_STEPS + "=" +
                OptionalCompilationStep.INSTANT_DEV + "," + Joiner.on(',').join(flags);
        return Collections.singletonList(property);
    }

    private static InstantRun getInstantRunModel(AndroidProject project) {
        Collection<Variant> variants = project.getVariants();
        for (Variant variant : variants) {
            if ("debug".equals(variant.getName())) {
                return variant.getMainArtifact().getInstantRun();
            }
        }
        throw new AssertionError("Could not find debug variant.");
    }
}
