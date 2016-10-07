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

package com.android.build.gradle.internal.coverage;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.truth.Expect;

import org.gradle.api.logging.Logging;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Ignore("Temporarily disabled until new coverage.ec is generated.")
public class JacocoReportTaskTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void sanityCheckReport() throws IOException, URISyntaxException {
        // Coverage file generated from BasicTest project.
        File coverageFile = copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/coverage.ec",
                mTemporaryFolder.newFolder());
        File sourceRoot = setUpSourceDirectory();
        File classDir = setUpClassDirectory();
        File reportDir = mTemporaryFolder.newFolder();

        JacocoReportTask.generateReport(
                ImmutableList.of(coverageFile),
                reportDir,
                classDir,
                ImmutableList.of(sourceRoot),
                4,
                "debug",
                Logging.getLogger(JacocoReportTaskTest.class));

        File indexHtml = new File(reportDir, "index.html");
        Document document = Jsoup.parse(indexHtml, Charsets.UTF_8.name(),
                indexHtml.getParentFile().toURI().toString());

        Elements totals = document.select("td:contains(142 of 147)");
        expect.that(totals).named("Total coverage table cell").hasSize(1);

        document = navigateTo(
                getLinkWithText(document, "com.android.tools.build.tests.myapplication"));
        getLinkWithText(document, "MainActivity.new View.OnClickListener() {...}");
        expect.that(document.text()).doesNotContain("BuildConfig");
        document = navigateTo(getLinkWithText(document, "MainActivity"));
        document = navigateTo(getLinkWithText(document, "doA()"));

        // Check log statement is marked as covered.
        Elements covered = document.select("span.fc");
        assertThat(covered).isNotEmpty();
        assertThat(covered.get(0).text()).contains("Log.d(\"Test1\", \"Do a\");");
    }

    @NonNull
    private static Element getLinkWithText(@NonNull Document document, @NonNull String text) {
        return document.select("a[href]").stream()
                .filter(candidateLink -> candidateLink.text().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Could not find link with text " + text + " in document " + document));
    }

    @NonNull
    private static Document navigateTo(@NonNull Element link)
            throws IOException, URISyntaxException {
        File target = new File(new URI(link.attr("abs:href")).toURL().getFile());
        return Jsoup.parse(target, Charsets.UTF_8.name(), target.getParentFile().toURI().toString());
    }

    private static File copyResourceToFolder(
            @NonNull String resourceName,
            @NonNull File folder) throws IOException {
        ByteSource resource = Resources.asByteSource(Resources.getResource(resourceName));
        File file = new File(folder, resourceName);
        FileUtils.mkdirs(file.getParentFile());
        resource.copyTo(Files.asByteSink(file));
        return file;
    }

    private File setUpSourceDirectory() throws IOException {
        File sourceRoot = mTemporaryFolder.newFolder();
        copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/MainActivity.java",
                sourceRoot);
        copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/BuildConfig.java",
                sourceRoot);
        return new File(sourceRoot, "jacocoReport");
    }

    private File setUpClassDirectory() throws IOException {
        File sourceRoot = mTemporaryFolder.newFolder();
        copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/MainActivity.class",
                sourceRoot);
        copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/MainActivity$1.class",
                sourceRoot);
        copyResourceToFolder(
                "jacocoReport/com/android/tools/build/tests/myapplication/BuildConfig.class",
                sourceRoot);
        return new File(sourceRoot, "jacocoReport");
    }
}
