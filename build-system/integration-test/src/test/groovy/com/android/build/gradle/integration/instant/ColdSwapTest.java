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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext.Artifact;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext.FileType;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Smoke test for cold swap builds.
 */
public class ColdSwapTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Expect expect = Expect.create();

    @Before
    public void activityClass() throws IOException {
        createActivityClass("", "");
    }

    @Test
    public void withDalvik() throws Exception {
        final int apiLevel = 15;

        // Initial build
        InstantRun instantRunModel = doInitialBuild(apiLevel);

        DexFileSubject dexFile = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug")).hasMainDexFile().that();
        dexFile.hasClass("Lcom/example/helloworld/HelloWorld;")
                .that().hasMethod("onCreate");
        dexFile.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;");

        InstantRunBuildContext initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        long startBuildId = initialContext.getBuildId();

        // Cold swap
        makeColdSwapChange();

        project.execute(InstantRunTestUtils.getInstantRunArgs(apiLevel),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildContext coldSwapContext = InstantRunTestUtils.loadContext(instantRunModel);
        assertNotNull(coldSwapContext.getLastBuild());
        expect.that(coldSwapContext.getLastBuild().getVerifierStatus()).named("verifier status")
                .hasValue(InstantRunVerifierStatus.METHOD_ADDED);
        expect.that(coldSwapContext.getBuildId()).named("build id").isNotEqualTo(startBuildId);

        assertThat(coldSwapContext.getLastBuild().getArtifacts()).hasSize(1);
        Artifact artifact = Iterables.getOnlyElement(coldSwapContext.getLastBuild().getArtifacts());
        assertThat(artifact.getType()).named("file type")
                .isEqualTo(FileType.RESTART_DEX);
        checkUpdatedClassPresence(artifact.getLocation());
    }

    @Test
    public void withLollipop() throws Exception {
        final int apiLevel = 21;
        // Initial build
        InstantRun instantRunModel = doInitialBuild(apiLevel);

        boolean foundHelloWorld = false;
        boolean foundBootstrapApplication = false;

        ApkSubject debugApk = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug"));
        List<String> entries = debugApk.entries();
        for (String entry : entries) {
            if (entry.endsWith(".dex")) {
                DexFileSubject dexFile = debugApk.hasDexFile(entry).that();
                if (dexFile.containsClass("Lcom/example/helloworld/HelloWorld;")) {
                    dexFile.hasClass("Lcom/example/helloworld/HelloWorld;")
                            .that().hasMethod("onCreate");
                    foundHelloWorld = true;
                }
                if (dexFile.containsClass("Lcom/android/tools/fd/runtime/BootstrapApplication;")) {
                    foundBootstrapApplication = true;
                }
            }
        }
        expect.withFailureMessage("HelloWorld class should be in one dex file")
                .that(foundHelloWorld).isTrue();
        expect.withFailureMessage("BootstrapApplication class should be in one dex file")
                .that(foundBootstrapApplication).isTrue();

        InstantRunBuildContext initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        long startBuildId = initialContext.getBuildId();

        // Cold swap
        makeColdSwapChange();

        project.execute(InstantRunTestUtils.getInstantRunArgs(apiLevel),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildContext coldSwapContext = InstantRunTestUtils.loadContext(instantRunModel);
        assertNotNull(coldSwapContext.getLastBuild());

        expect.that(coldSwapContext.getLastBuild().getVerifierStatus()).named("verifier status")
                .hasValue(InstantRunVerifierStatus.METHOD_ADDED);
        expect.that(coldSwapContext.getBuildId()).named("build id").isNotEqualTo(startBuildId);

        assertThat(coldSwapContext.getLastBuild().getArtifacts()).hasSize(1);
        Artifact artifact = Iterables.getOnlyElement(coldSwapContext.getLastBuild().getArtifacts());

        expect.that(artifact.getType()).isEqualTo(FileType.DEX);

        checkUpdatedClassPresence(artifact.getLocation());
    }

    @Ignore
    @Test
    public void withMarshmallow() throws Exception {
        final int apiLevel = 23;
        // Initial build
        InstantRun instantRunModel = doInitialBuild(apiLevel);

        // Classes are sharded into split apks.
        assertThatApk(project.getApk("debug")).doesNotContain("classes.dex");

        InstantRunBuildContext initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        long startBuildId = initialContext.getBuildId();

        // Cold swap
        makeColdSwapChange();

        project.execute(InstantRunTestUtils.getInstantRunArgs(apiLevel),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildContext coldSwapContext = InstantRunTestUtils.loadContext(instantRunModel);
        assertNotNull(coldSwapContext.getLastBuild());
        expect.that(coldSwapContext.getLastBuild().getVerifierStatus()).named("verifier status")
                .hasValue(InstantRunVerifierStatus.METHOD_ADDED);
        expect.that(coldSwapContext.getBuildId()).named("build id").isNotEqualTo(startBuildId);

        assertThat(coldSwapContext.getLastBuild().getArtifacts()).hasSize(1);
        Artifact artifact = Iterables.getOnlyElement(coldSwapContext.getLastBuild().getArtifacts());
        expect.that(artifact.getType()).named("artifact type").isEqualTo(FileType.SPLIT);
        checkUpdatedClassPresence(artifact.getLocation());
    }


    private InstantRun doInitialBuild(int apiLevel) {
        project.execute("clean");
        InstantRun instantRunModel = InstantRunTestUtils
                .getInstantRunModel(project.getSingleModel());

        project.execute(InstantRunTestUtils
                        .getInstantRunArgs(apiLevel, OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");
        return instantRunModel;
    }

    private void makeColdSwapChange() throws IOException {
        createActivityClass("import java.util.logging.Logger;", "newMethod();\n"
                + "    }\n"
                + "    public void newMethod() {\n"
                + "        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)\n"
                + "                .warning(\"Added some logging\");\n"
                + "");


    }

    private void checkUpdatedClassPresence(@NonNull File dexFile) throws Exception {
        DexClassSubject helloWorldClass = expect.about(DexFileSubject.FACTORY)
                .that(dexFile)
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that();
        helloWorldClass.hasMethod("onCreate");
        helloWorldClass.hasMethod("newMethod");
    }

    private void createActivityClass(@NonNull String imports, @NonNull String newMethodBody)
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
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                Charsets.UTF_8);
    }

}
