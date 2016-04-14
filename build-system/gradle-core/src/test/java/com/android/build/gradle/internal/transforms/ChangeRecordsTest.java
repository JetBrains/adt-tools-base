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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.api.transform.Status;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tests for the {@link ChangeRecords} class.
 */
public class ChangeRecordsTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testNormalOverriding() {
        String someFilePath = "path/to/some/file";
        ChangeRecords changeRecords = new ChangeRecords();
        changeRecords.add(Status.CHANGED, someFilePath);
        // now override it.
        changeRecords.add(Status.REMOVED, someFilePath);

        assertThat(changeRecords.getChangeFor(someFilePath)).isEqualTo(Status.REMOVED);
    }

    @Test
    public void testNoChange() {
        ChangeRecords changeRecords = new ChangeRecords();
        assertThat(changeRecords.getChangeFor("foo")).isNull();
    }

    @Test
    public void testPersistence() throws IOException {
        ChangeRecords changeRecords = new ChangeRecords();
        changeRecords.add(Status.CHANGED, "changed/file");
        changeRecords.add(Status.REMOVED, "removed/file");
        changeRecords.add(Status.ADDED, "added/file");
        File file = tmpFolder.newFile("ChangeRecordsTest.txt");
        changeRecords.write(file);
        List<String> records = Files.readLines(file, Charsets.UTF_8);
        assertThat(records).containsExactlyElementsIn(
                ImmutableList.of("CHANGED,changed/file",
                        "REMOVED,removed/file", "ADDED,added/file"));
    }

    @Test
    public void testLoading() throws IOException {
        File file = tmpFolder.newFile("ChangeRecordsTest.txt");
        Files.write("CHANGED,/some/changed/file\n"
                + "CHANGED,/another/changed/file\n"
                + "REMOVED,/some/removed/file\n"
                + "ADDED,/some/added/file\n", file, Charsets.UTF_8);
        ChangeRecords changeRecords = ChangeRecords.load(file);
        assertThat(changeRecords.getChangeFor("/some/changed/file")).isEqualTo(Status.CHANGED);
        assertThat(changeRecords.getChangeFor("/another/changed/file")).isEqualTo(Status.CHANGED);
        assertThat(changeRecords.getChangeFor("/some/removed/file")).isEqualTo(Status.REMOVED);
        assertThat(changeRecords.getChangeFor("/some/added/file")).isEqualTo(Status.ADDED);
    }

    @Test
    public void testMerging() {
        String someFilePath = "path/to/some/file";
        ChangeRecords changeRecords = new ChangeRecords();
        changeRecords.add(Status.CHANGED, someFilePath);

        // now create an older set of changes.
        ChangeRecords olderChangeRecords = new ChangeRecords();
        olderChangeRecords.add(Status.ADDED, someFilePath);
        olderChangeRecords.add(Status.CHANGED, "some/other/file");
        changeRecords.addAll(olderChangeRecords);

        assertThat(changeRecords.getChangeFor("path/to/some/file")).isEqualTo(Status.CHANGED);
        assertThat(changeRecords.getChangeFor("some/other/file")).isEqualTo(Status.CHANGED);
    }

    @Test
    public void testGetFilesForStatusAPI() {
        ChangeRecords changeRecords = new ChangeRecords();
        changeRecords.add(Status.ADDED, "some/added/file1");
        changeRecords.add(Status.ADDED, "some/added/file2");
        changeRecords.add(Status.CHANGED, "some/other/file1");
        changeRecords.add(Status.CHANGED, "some/other/file2");
        changeRecords.add(Status.REMOVED, "some/removed/file1");
        changeRecords.add(Status.REMOVED, "some/removed/file2");

        assertThat(changeRecords.getFilesForStatus(Status.ADDED)).containsExactly(
                "some/added/file1", "some/added/file2");
        assertThat(changeRecords.getFilesForStatus(Status.CHANGED)).containsExactly(
                "some/other/file1", "some/other/file2");
        assertThat(changeRecords.getFilesForStatus(Status.REMOVED)).containsExactly(
                "some/removed/file1", "some/removed/file2");
    }
}