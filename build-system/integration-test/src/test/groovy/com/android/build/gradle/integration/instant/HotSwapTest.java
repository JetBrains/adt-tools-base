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
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

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
    public void buildIncrementallyWithInstantRunForDalvik()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        buildIncrementallyWithInstantRun(15);
    }

    @Test
    public void buildIncrementallyWithInstantRunForLollipop()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        buildIncrementallyWithInstantRun(21);
    }

    @Test
    public void buildIncrementallyWithInstantRunForMarshMallow()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        buildIncrementallyWithInstantRun(23);
    }

    private void buildIncrementallyWithInstantRun(int apiLevel)
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        sProject.execute("clean");
        InstantRun instantRunModel = getInstantRunModel(sProject.getSingleModel());

        sProject.execute(getInstantRunArgs(apiLevel), "assembleDebug");
        ApkSubject debugApk = expect.about(ApkSubject.FACTORY)
                .that(sProject.getApk("debug"));
        List<String> entries = debugApk.entries();
        for (String entry : entries) {
            if (entry.endsWith(".dex")) {
                DexFileSubject dexFile = debugApk.getDexFile(entry).that();
                if (dexFile.containsClass("Lcom/example/helloworld/HelloWorld;")) {
                    dexFile.hasClass("Lcom/example/helloworld/HelloWorld;")
                            .that().hasMethod("onCreate");
                }
            }
        }
        checkHotSwapCompatibleChange(apiLevel, instantRunModel);
        checkColdSwapCompatibleChange(apiLevel, instantRunModel);
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(int apiLevel, InstantRun instantRunModel)
            throws IOException, ProcessException {
        createActivityClass("import java.util.logging.Logger;",
                "Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(\"Added some logging\");");

        sProject.execute(getInstantRunArgs(apiLevel),
                instantRunModel.getIncrementalAssembleTaskName());

        expect.about(DexFileSubject.FACTORY)
                .that(instantRunModel.getReloadDexFile())
                .hasClass("Lcom/example/helloworld/HelloWorld$override;")
                .that().hasMethod("onCreate");

        // the restart.dex should not be present.
        expect.about(FileSubject.FACTORY).that(instantRunModel.getRestartDexFile()).doesNotExist();
    }

    private void createColdSwapCompatibleChange(int apiLevel, InstantRun instantRunModel)
            throws IOException {

        createActivityClass("import java.util.logging.Logger;", "newMethod();\n"
                + "    }\n"
                + "    public void newMethod() {\n"
                + "        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)\n"
                + "                .warning(\"Added some logging\");\n"
                + "");

        sProject.execute(getInstantRunArgs(apiLevel),
                instantRunModel.getIncrementalAssembleTaskName());
        expect.about(FileSubject.FACTORY).that(instantRunModel.getReloadDexFile())
                .doesNotExist();    }

    /**
     * Check that an incompatible change produce the right artifact to restart the application
     * depending on the target apiLevel.
     */
    private void checkColdSwapCompatibleChange(int apiLevel, InstantRun instantRunModel)
            throws IOException, ProcessException, ParserConfigurationException, SAXException {

        createColdSwapCompatibleChange(apiLevel, instantRunModel);

        // read the resulting build-info file containing changed artifacts.
        Document document = XmlUtils.parseUtfXmlFile(
                instantRunModel.getInfoFile(), false /* namespaceAware */);

        NodeList artifacts = document.getElementsByTagName("artifact");
        expect.that(artifacts.getLength()).isEqualTo(1);
        NamedNodeMap attributes = artifacts.item(0).getAttributes();
        expect.that(attributes.getLength()).isEqualTo(2);


        if (apiLevel < 21) {
            checkUpdatedClassPresence(instantRunModel.getRestartDexFile()).hasMethod("newMethod");
            // also check the build-info content
            expect.that(attributes.getNamedItem("type").getNodeValue()).isEqualTo("RESTART_DEX");
            File dexFile = new File(attributes.getNamedItem("location").getNodeValue());
            expect.that(dexFile.getAbsolutePath()).isEqualTo(
                    instantRunModel.getRestartDexFile().getAbsolutePath());
            return;
        }
        if (apiLevel < 23) {
            expect.that(attributes.getNamedItem("type").getNodeValue()).isEqualTo("DEX");
            File dexFile = new File(attributes.getNamedItem("location").getNodeValue());
            checkUpdatedClassPresence(dexFile);
            return;
        }
        // fall back into marsh-mellow case.
        expect.that(attributes.getNamedItem("type").getNodeValue()).isEqualTo("SPLIT");
        File apkFile = new File(attributes.getNamedItem("location").getNodeValue());
        checkUpdatedClassPresence(apkFile);

    }

    private DexClassSubject checkUpdatedClassPresence(File dexFile)
            throws IOException, ProcessException {
        DexClassSubject helloWorldClass = expect.about(DexFileSubject.FACTORY)
                .that(dexFile)
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

    private static List<String> getInstantRunArgs(int apiLevel, OptionalCompilationStep... flags) {
        String property = "-P" + AndroidProject.OPTIONAL_COMPILATION_STEPS + "=" +
                OptionalCompilationStep.INSTANT_DEV + "," + Joiner.on(',').join(flags);
        String version = String.format("-Pandroid.injected.build.api=%d", apiLevel);
        return ImmutableList.of(property, version);
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
