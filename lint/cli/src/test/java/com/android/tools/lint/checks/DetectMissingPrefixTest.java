/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class DetectMissingPrefixTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new DetectMissingPrefix();
    }

    public void test() throws Exception {
        assertEquals(
            "res/layout/namespace.xml:2: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:other=\"http://foo.bar\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" orientation=\"true\">\n" +
            "                                                                                                                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/namespace.xml:3: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "    <Button style=\"@style/setupWizardOuterFrame\" android.text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n" +
            "                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/namespace.xml:5: Error: Unexpected namespace prefix \"other\" found for tag LinearLayout [MissingPrefix]\n" +
            "    <LinearLayout other:orientation=\"horizontal\"/>\n" +
            "                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n",

            lintFiles("res/layout/namespace.xml"));
    }

    public void testCustomNamespace() throws Exception {
        assertEquals(
            "res/layout/namespace2.xml:8: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "    customprefix:orientation=\"vertical\"\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintFiles("res/layout/namespace2.xml"));
    }

    public void testManifest() throws Exception {
        assertEquals(
            "AndroidManifest.xml:4: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "    versionCode=\"1\"\n" +
            "    ~~~~~~~~~~~~~~~\n" +
            "AndroidManifest.xml:11: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "        android.label=\"@string/app_name\" >\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "AndroidManifest.xml:18: Error: Attribute is missing the Android namespace prefix [MissingPrefix]\n" +
            "                <category name=\"android.intent.category.LAUNCHER\" />\n" +
            "                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "3 errors, 0 warnings\n",

            lintFiles("missingprefix.xml=>AndroidManifest.xml"));
    }

    public void testLayoutAttributes() throws Exception {
        assertEquals(
            "No warnings.",

            lintFiles("res/layout/namespace3.xml"));
    }

    public void testLayoutAttributes2() throws Exception {
        assertEquals(
            "No warnings.",

            lintFiles("res/layout/namespace4.xml"));
    }

    public void testUnusedNamespace() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject("res/layout/message_edit_detail.xml"));
    }
}
