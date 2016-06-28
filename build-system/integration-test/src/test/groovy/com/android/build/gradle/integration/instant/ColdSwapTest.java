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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Smoke test for cold swap builds.
 */
@RunWith(FilterableParameterized.class)
public class ColdSwapTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging packaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void activityClass() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        createActivityClass("", "");
    }

    @Test
    public void withDalvik() throws Exception {
        new ColdSwapTester(project).withPackaging(packaging).testDalvik(new ColdSwapTester.Steps() {
            @Override
            public void checkApk(@NonNull File apk) throws Exception {
                checkDalvikApk(apk);
            }

            @Override
            public void makeChange() throws Exception {
                makeColdSwapChange();
            }

            @Override
            public void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception {
                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
            }

            @Override
            public void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts)
                    throws Exception {
                assertThat(artifacts).hasSize(1);
                checkDalvikApk(artifacts.get(0).getLocation());
            }
        });
    }

    private void checkDalvikApk(@NonNull File apk) throws IOException, ProcessException {
        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(apk);

        apkSubject.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.MAIN)
                .that().hasMethod("onCreate");
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/AppInfo;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
    }

    @Test
    public void withMultiDex() throws Exception {
        new ColdSwapTester(project).withPackaging(packaging).testMultiDex(new ColdSwapTester.Steps() {
            @Override
            public void checkApk(@NonNull File apk) throws Exception {
                ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(apk);

                apkSubject.hasClass("Lcom/example/helloworld/HelloWorld;",
                        AbstractAndroidSubject.ClassFileScope.INSTANT_RUN)
                        .that().hasMethod("onCreate");
                apkSubject.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                        AbstractAndroidSubject.ClassFileScope.ALL);
                apkSubject.hasClass("Lcom/android/tools/fd/runtime/AppInfo;",
                        AbstractAndroidSubject.ClassFileScope.ALL);
            }

            @Override
            public void makeChange() throws Exception {
                makeColdSwapChange();
            }

            @Override
            public void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception {
                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
            }

            @Override
            public void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts) throws Exception {
                assertThat(artifacts).hasSize(1);
                InstantRunBuildContext.Artifact artifact = Iterables.getOnlyElement(artifacts);

                expect.that(artifact.getType()).isEqualTo(InstantRunBuildContext.FileType.DEX);

                checkUpdatedClassPresence(artifact.getLocation());
            }
        });
    }

    @Test
    public void withMultiApk() throws Exception {
        new ColdSwapTester(project).withPackaging(packaging).testMultiApk(new ColdSwapTester.Steps() {
            @Override
            public void checkApk(@NonNull File apk) throws Exception {
            }

            @Override
            public void makeChange() throws Exception {
                makeColdSwapChange();
            }

            @Override
            public void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception {
                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
            }

            @Override
            public void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts) throws Exception {
                assertThat(artifacts).hasSize(2);
                for (InstantRunBuildContext.Artifact artifact : artifacts) {
                    expect.that(artifact.getType()).isAnyOf(
                            InstantRunBuildContext.FileType.SPLIT,
                            InstantRunBuildContext.FileType.SPLIT_MAIN);
                    if (artifact.getType().equals(InstantRunBuildContext.FileType.SPLIT)) {
                        checkUpdatedClassPresence(artifact.getLocation());
                    }
                }
            }
        });
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
