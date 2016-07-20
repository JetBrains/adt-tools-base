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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * Tests that users can align an APK externally generated.
 */
@RunWith(Parameterized.class)
public class AlignExternallyGeneratedApkTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void createTaskToAlignTheApkAndRunIt() throws Exception {
        /*
         * First, fetch a test apk and copy it somewhere. This must be an unaligned APK.
         */
        File testApk = mTemporaryFolder.newFile();
        try (
                InputStream is = getClass().getResourceAsStream("test-unaligned-apk.apk");
                OutputStream os = new FileOutputStream(testApk)) {
            assertNotNull(is);
            ByteStreams.copy(is, os);
        }

        /*
         * Make sure the apk is *not* aligned.
         */
        try (ZFile zf = new ZFile(testApk)) {
            StoredEntry se = zf.get("resources.arsc");
            assertNotNull(se);
            long startOffset = se.getCentralDirectoryHeader().getOffset() + se.getLocalHeaderSize();
            assertTrue(startOffset % 4 != 0);
        }

        /*
         * Define where we want the aligned APK.
         */
        File alignedApk = mTemporaryFolder.newFile();
        alignedApk.delete();

        /*
         * Add an afterEvaluate section to the project that creates a zipalign task named ZA
         * that aligns testApk into alignedApk.
         */
        Files.append(
                "afterEvaluate {\n"
                        + "project ->\n"
                        + "android.applicationVariants.all { variant ->\n"
                        + "variant.outputs.each { output ->\n"
                        + "if (output.name.equals(\"debug\")) {\n"
                        + "output.createZipAlignTask(\n"
                        + "\"ZA\",\n"
                        + "new File(\""+ testApk.getAbsolutePath() + "\"),\n"
                        + "new File(\"" + alignedApk.getAbsolutePath() + "\"));\n"
                        + "}\n"
                        + "}\n"
                        + "}\n"
                        + "}\n",
                project.getBuildFile(),
                Charsets.US_ASCII);

        /*
         * Disable zipalign if using old packaging.
         */
        if (mPackaging == Packaging.OLD_PACKAGING) {
            List<String> lines = Files.readLines(project.getBuildFile(), Charsets.US_ASCII);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals("android {")) {
                    lines.add(i + 1, "buildTypes { debug { zipAlignEnabled false } }");
                    found = true;
                    break;
                }
            }

            Files.write(
                    String.join(System.lineSeparator(), lines),
                    project.getBuildFile(),
                    Charsets.US_ASCII);

            assertTrue(found);
        }

        /*
         * Now run task ZA.
         */
        project.executor()
                .withPackaging(mPackaging)
                .run("ZA");

        /*
         * Make sure alignedApk exists and it is different from the original APK.
         */
        assertThatApk(alignedApk).contains("classes.dex");
        assertFalse(Files.equal(alignedApk, testApk));

        /*
         * Make sure the apk is aligned.
         */
        try (ZFile zf = new ZFile(alignedApk)) {
            StoredEntry se = zf.get("resources.arsc");
            assertNotNull(se);
            long startOffset = se.getCentralDirectoryHeader().getOffset() + se.getLocalHeaderSize();
            assertTrue(startOffset % 4 == 0);
        }
    }
}
