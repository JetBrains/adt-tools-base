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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

/**
 * Checks that the packaging options filtering are honored. Currently, only tests for excluding
 * regular expressions.
 */
@RunWith(FilterableParameterized.class)
public class PackagingOptionsFilteringTest {

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    /**
     * Creates a dummy file.
     *
     * @param contents the file's contents
     * @param paths the path to the file starting from the project base directory
     * @throws IOException I/O failed
     */
    private void dummyFile(@NonNull byte[] contents, @NonNull String... paths) throws IOException {
        File file = FileUtils.join(project.getTestDir(), paths);
        FileUtils.mkdirs(file.getParentFile());
        Files.write(file.toPath(), contents);
    }

    /**
     * Appends text to the build file.
     *
     * @param text text to append
     * @throws IOException I/O failed
     */
    private void appendBuild(@NonNull String text) throws IOException {
        File buildFile = project.getBuildFile();
        String contents = com.google.common.io.Files.toString(buildFile, Charsets.US_ASCII);
        contents += System.lineSeparator() + text;
        com.google.common.io.Files.write(contents, buildFile, Charsets.US_ASCII);
    }

    /**
     * Folders that are named {@code .svn} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultSvnFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        dummyFile(c0, "src", "main", "resources", ".svn", "ignored-1");
        dummyFile(c0, "src", "main", "resources", "not-ignored-1");
        dummyFile(c1, "src", "main", "resources", "foo", ".svn", "ignored-2");
        dummyFile(c1, "src", "main", "resources", "foo", "not-ignored-2");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", ".svn", "ignored-3");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "not-ignored-3");
        dummyFile(c3, "src", "main", "resources", "foo", "svn", "not-ignored-4");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource(".svn/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/.svn/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/.svn/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/svn/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code CVS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultCvsFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        dummyFile(c0, "src", "main", "resources", "CVS", "ignored-1");
        dummyFile(c0, "src", "main", "resources", "not-ignored-1");
        dummyFile(c1, "src", "main", "resources", "foo", "CVS", "ignored-2");
        dummyFile(c1, "src", "main", "resources", "foo", "not-ignored-2");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "CVS", "ignored-3");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "not-ignored-3");
        dummyFile(c3, "src", "main", "resources", "foo", "cvs.cvs", "not-ignored-4");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource("CVS/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/cvs/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/Cvs/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/cvs.cvs/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code SCCS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultSccsFolderInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        dummyFile(c0, "src", "main", "resources", "SCCS", "ignored-1");
        dummyFile(c0, "src", "main", "resources", "not-ignored-1");
        dummyFile(c1, "src", "main", "resources", "foo", "sccs", "ignored-2");
        dummyFile(c1, "src", "main", "resources", "foo", "not-ignored-2");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "SccS", "ignored-3");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "not-ignored-3");
        dummyFile(c3, "src", "main", "resources", "foo", "SCCS.1", "not-ignored-4");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource("SCCS/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/SCCS/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/SCCS/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/SCCS.1/not-ignored-4", c3);
    }

    /**
     * Folders that are named {@code SCCS} are ignored.
     *
     * @throws Exception test failed
     */
    @Test
    public void byDefaultUnderscoreFoldersInJavaResourcesAreIgnored() throws Exception {
        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };
        byte[] c3 = new byte[] { 9 };

        dummyFile(c0, "src", "main", "resources", "_", "ignored-1");
        dummyFile(c0, "src", "main", "resources", "not-ignored-1");
        dummyFile(c1, "src", "main", "resources", "foo", "__", "ignored-2");
        dummyFile(c1, "src", "main", "resources", "foo", "not-ignored-2");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "_blah", "ignored-3");
        dummyFile(c2, "src", "main", "resources", "foo", "bar", "not-ignored-3");
        dummyFile(c3, "src", "main", "resources", "foo", "x_", "not-ignored-4");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource("_/ignored-1");
        apk.containsJavaResourceWithContent("not-ignored-1", c0);
        apk.doesNotContainJavaResource("foo/__/ignored-2");
        apk.containsJavaResourceWithContent("foo/not-ignored-2", c1);
        apk.doesNotContainJavaResource("foo/bar/_blah/ignored-3");
        apk.containsJavaResourceWithContent("foo/bar/not-ignored-3", c2);
        apk.containsJavaResourceWithContent("foo/x_/not-ignored-4", c3);
    }

    /**
     * Exclude patterns can be redefined.
     *
     * @throws Exception test failed
     */
    @Test
    public void redefineExcludePatterns() throws Exception {
        appendBuild("android {");
        appendBuild("    packagingOptions {");
        appendBuild("        excludePattern '**/*ign'");
        appendBuild("        excludePattern '**/sensitive/**'");
        appendBuild("    }");
        appendBuild("}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };

        dummyFile(c0, "src", "main", "resources", "I_am_ign");
        dummyFile(c0, "src", "main", "resources", "sccs", "I stay");
        dummyFile(c1, "src", "main", "resources", "Ignoring", "this", "fileign");
        dummyFile(c1, "src", "main", "resources", "SSensitive", "files", "may", "leak");
        dummyFile(c2, "src", "main", "resources", "some", "sensitive", "files", "dont");
        dummyFile(c2, "src", "main", "resources", "pkg", "cvs", "very-sensitive-info");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource("I_am_ign");
        apk.containsJavaResourceWithContent("sccs/I stay", c0);
        apk.doesNotContainJavaResource("Ignoring/this/fileign");
        apk.containsJavaResourceWithContent("SSensitive/files/may/leak", c1);
        apk.doesNotContainJavaResource("some/sensitive/files/dont");
        apk.containsJavaResourceWithContent("pkg/cvs/very-sensitive-info", c2);
    }

    /**
     * Exclude patterns can be redefined (same as {@link #redefineExcludePatterns()}, but using
     * a different syntax).
     *
     * @throws Exception test failed
     */
    @Test
    public void redefineExcludePatterns2() throws Exception {
        appendBuild("android {");
        appendBuild("    packagingOptions {");
        appendBuild("        excludePatterns = [");
        appendBuild("            '**/*ign',");
        appendBuild("            '**/sensitive/**'");
        appendBuild("        ]");
        appendBuild("    }");
        appendBuild("}");

        byte[] c0 = new byte[] { 0, 1, 2, 3 };
        byte[] c1 = new byte[] { 4, 5 };
        byte[] c2 = new byte[] { 6, 7, 8 };

        dummyFile(c0, "src", "main", "resources", "I_am_ign");
        dummyFile(c0, "src", "main", "resources", "sccs", "I stay");
        dummyFile(c1, "src", "main", "resources", "Ignoring", "this", "fileign");
        dummyFile(c1, "src", "main", "resources", "SSensitive", "files", "may", "leak");
        dummyFile(c2, "src", "main", "resources", "some", "sensitive", "files", "dont");
        dummyFile(c2, "src", "main", "resources", "pkg", "cvs", "very-sensitive-info");

        project.execute(":assembleDebug");

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        apk.doesNotContainJavaResource("I_am_ign");
        apk.containsJavaResourceWithContent("sccs/I stay", c0);
        apk.doesNotContainJavaResource("Ignoring/this/fileign");
        apk.containsJavaResourceWithContent("SSensitive/files/may/leak", c1);
        apk.doesNotContainJavaResource("some/sensitive/files/dont");
        apk.containsJavaResourceWithContent("pkg/cvs/very-sensitive-info", c2);
    }
}
