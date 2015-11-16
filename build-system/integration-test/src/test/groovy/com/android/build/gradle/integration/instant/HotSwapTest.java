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

/**
 * Smoke test for hot swap builds.
 */
public class HotSwapTest {

    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Expect expect = Expect.create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Before
    public void activityClass() throws IOException {
        createActivityClass("", "");
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws IOException, ProcessException {
        sProject.execute("clean");
        InstantRun instantRunModel = getInstantRunModel(sProject.getSingleModel());

        // Check that original class is included.
        sProject.execute(getInstantRunArgs(), "assembleDebug");
        expect.about(ApkSubject.FACTORY)
                .that(sProject.getApk("debug")).hasMainDexFile()
                .that().hasClass("Lcom/example/helloworld/HelloWorld;")
                .that().hasMethod("onCreate");
        checkHotSwapCompatibleChange(instantRunModel);
        checkColdSwapCompatibleChange(instantRunModel);
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(InstantRun instantRunModel)
            throws IOException, ProcessException {
        createActivityClass("import java.util.logging.Logger;",
                "Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(\"Added some logging\");");

        sProject.execute(getInstantRunArgs(), instantRunModel.getIncrementalAssembleTaskName());

        expect.about(DexFileSubject.FACTORY)
                .that(instantRunModel.getReloadDexFile())
                .hasClass("Lcom/example/helloworld/HelloWorld$override;")
                .that().hasMethod("onCreate");

        // the restart.dex should not be present.
        expect.about(FileSubject.FACTORY).that(instantRunModel.getRestartDexFile()).doesNotExist();
    }

    /**
     * Check that a change that cannot be used with hot swap does not generate a reload dex file.
     */
    private void checkColdSwapCompatibleChange(InstantRun instantRunModel)
            throws IOException, ProcessException {

        createActivityClass("import java.util.logging.Logger;", "newMethod();\n"
                + "    }\n"
                + "    public void newMethod() {\n"
                + "        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)\n"
                + "                .warning(\"Added some logging\");\n"
                + "");

        sProject.execute(getInstantRunArgs(), instantRunModel.getIncrementalAssembleTaskName());
        expect.about(FileSubject.FACTORY).that(instantRunModel.getReloadDexFile()).doesNotExist();
        checkRestartDexHelloWorldClass(instantRunModel).hasMethod("newMethod");
    }

    private DexClassSubject checkRestartDexHelloWorldClass(InstantRun instantRunModel)
            throws IOException, ProcessException {
        DexClassSubject helloWorldClass = expect.about(DexFileSubject.FACTORY)
                .that(instantRunModel.getRestartDexFile())
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that();
        helloWorldClass.hasMethod("onCreate");
        return helloWorldClass;
    }

    private static void createActivityClass(String imports, String newMethodBody)
            throws IOException {
        String javaCompile = "package com.example.helloworld;\n" + imports +
                "\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        " +
                newMethodBody +
                "    }\n"
                + "}";
        Files.write(javaCompile,
                sProject.file("src/main/java/com/example/helloworld/HelloWorld.java"),
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
