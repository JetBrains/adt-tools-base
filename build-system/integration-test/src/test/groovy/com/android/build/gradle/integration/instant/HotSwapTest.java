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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.InstantRun;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Smoke test for hot swap builds.
 */
public class HotSwapTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Expect expect = Expect.create();

    @Before
    public void activityClass() throws IOException {
        createActivityClass("", "");
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel = InstantRunTestUtils
                .getInstantRunModel(project.getSingleModel());

        project.execute(InstantRunTestUtils.getInstantRunArgs(), "assembleDebug");

        // As no injected API level, will default to no splits.
        DexFileSubject dexFile = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug")).hasMainDexFile().that();
        dexFile.hasClass("Lcom/example/helloworld/HelloWorld;")
                .that().hasMethod("onCreate");
        dexFile.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;");

        checkHotSwapCompatibleChange(instantRunModel);
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(InstantRun instantRunModel) throws Exception {
        createActivityClass("import java.util.logging.Logger;",
                "Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(\"Added some logging\");");

        project.execute(InstantRunTestUtils.getInstantRunArgs(),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildContext context = InstantRunTestUtils.loadContext(instantRunModel);

        assertNotNull(context.getLastBuild());
        assertThat(context.getLastBuild().getArtifacts()).hasSize(1);

        InstantRunBuildContext.Artifact artifact =
                Iterables.getOnlyElement(context.getLastBuild().getArtifacts());

        assertThat(artifact.getType()).isEqualTo(InstantRunBuildContext.FileType.RELOAD_DEX);

        expect.about(DexFileSubject.FACTORY)
                .that(artifact.getLocation())
                .hasClass("Lcom/example/helloworld/HelloWorld$override;")
                .that().hasMethod("onCreate");
    }

    private void createActivityClass(String imports, String newMethodBody)
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
