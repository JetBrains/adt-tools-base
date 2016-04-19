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

package com.android.build.gradle.internal.tasks.multidex;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class CreateManifestKeepListTest {

    private static final String MANIFEST = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    package=\"com.android.tests.flavored.f2.debug\"\n"
            + "    android:versionName=\"1.0.0-f2.D\" >\n"
            + "\n"
            + "    <application\n"
            + "        android:icon=\"@drawable/icon\"\n"
            + "        android:label=\"@string/app_name\" >\n"
            + "        <activity\n"
            + "            android:name=\"com.android.tests.flavored.OtherActivity\"\n"
            + "            android:label=\"@string/other_activity_invalid\" >\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.intent.action.MAIN\" />\n"
            + "\n"
            + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
            + "            </intent-filter>\n"
            + "        </activity>\n"
            + "        <activity\n"
            + "            android:name=\"com.android.tests.flavored.Main\"\n"
            + "            android:label=\"@string/app_name\" >\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.intent.action.MAIN\" />\n"
            + "\n"
            + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
            + "            </intent-filter>\n"
            + "        </activity>\n"
            + "    </application>\n"
            + "\n"
            + "</manifest>";

    private static final String EXPECTED_KEEP_FILTERED =
            "-keep class com.android.tests.flavored.Main { <init>(); }\n"
                    + "-keep public class * extends android.app.backup.BackupAgent {\n"
                    + "    <init>();\n"
                    + "}\n"
                    + "-keep public class * extends java.lang.annotation.Annotation {\n"
                    + "    *;\n"
                    + "}\n"
                    + "-keep class com.android.tools.fd.** {\n"
                    + "    *;\n"
                    + "}\n"
                    + "-dontnote com.android.tools.fd.**,android.support.multidex.MultiDexExtractor\n";

    private static final String EXPECTED_KEEP =
            "-keep class com.android.tests.flavored.OtherActivity { <init>(); }\n"
                    + EXPECTED_KEEP_FILTERED;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generateKeepListFromManifestWithFilter() throws Exception {

        File manifest = temporaryFolder.newFile("AndroidManifest.xml");
        Files.write(MANIFEST, manifest, Charsets.UTF_8);

        File unfilteredOutput = new File(temporaryFolder.newFolder(), "manifest_keep.txt");

        CreateManifestKeepList.generateKeepListFromManifest(
                manifest,
                unfilteredOutput,
                null /*proguardFile*/,
                null /*filter*/);

        assertThat(Files.toString(unfilteredOutput, Charsets.UTF_8)).isEqualTo(EXPECTED_KEEP);

        File filteredOutput = new File(temporaryFolder.newFolder(), "manifest_keep.txt");

        CreateManifestKeepList.generateKeepListFromManifest(
                manifest,
                filteredOutput,
                null /*proguardFile*/,
                removeActivity("com.android.tests.flavored.OtherActivity"));

        assertThat(Files.toString(filteredOutput, Charsets.UTF_8))
                .isEqualTo(EXPECTED_KEEP_FILTERED);
    }


    @NonNull
    private static CreateManifestKeepList.Filter removeActivity(@NonNull final String name) {
        return (qualifiedName, attributes) ->
                !qualifiedName.equals("activity") ||
                        !name.equals(attributes.get("android:name"));
    }
}
