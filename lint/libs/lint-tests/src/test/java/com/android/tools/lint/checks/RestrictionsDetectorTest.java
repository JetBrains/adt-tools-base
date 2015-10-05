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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.RestrictionsDetector.MAX_NESTING_DEPTH;
import static com.android.tools.lint.checks.RestrictionsDetector.MAX_NUMBER_OF_NESTED_RESTRICTIONS;

import com.android.tools.lint.detector.api.Detector;

public class RestrictionsDetectorTest  extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new RestrictionsDetector();
    }

    public void testSample() throws Exception {
        // Sample from https://developer.android.com/samples/AppRestrictionSchema/index.html
        // We expect no warnings.
        assertEquals("No warnings.",
                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"com.example.android.apprestrictionschema\"\n"
                                + "    android:versionCode=\"1\"\n"
                                + "    android:versionName=\"1.0\">\n"
                                + " \n"
                                + "    <!-- uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"21\" /-->\n"
                                + " \n"
                                + "    <application\n"
                                + "        android:allowBackup=\"true\"\n"
                                + "        android:icon=\"@drawable/ic_launcher\"\n"
                                + "        android:label=\"@string/app_name\"\n"
                                + "        android:theme=\"@style/AppTheme\">\n"
                                + " \n"
                                + "        <meta-data\n"
                                + "            android:name=\"android.content.APP_RESTRICTIONS\"\n"
                                + "            android:resource=\"@xml/app_restrictions\" />\n"
                                + " \n"
                                + "        <activity\n"
                                + "            android:name=\".MainActivity\"\n"
                                + "            android:label=\"@string/app_name\">\n"
                                + "            <intent-filter>\n"
                                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                + "            </intent-filter>\n"
                                + "        </activity>\n"
                                + "    </application>\n"
                                + " \n"
                                + " \n"
                                + "</manifest>"),
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " \n"
                                + "    <!--\n"
                                + "    Refer to the javadoc of RestrictionsManager for detail of this file.\n"
                                + "    https://developer.android.com/reference/android/content/RestrictionsManager.html\n"
                                + "    -->\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@bool/default_can_say_hello\"\n"
                                + "        android:description=\"@string/description_can_say_hello\"\n"
                                + "        android:key=\"can_say_hello\"\n"
                                + "        android:restrictionType=\"bool\"\n"
                                + "        android:title=\"@string/title_can_say_hello\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@string/default_message\"\n"
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"string\"\n"
                                + "        android:title=\"@string/title_message\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@integer/default_number\"\n"
                                + "        android:description=\"@string/description_number\"\n"
                                + "        android:key=\"number\"\n"
                                + "        android:restrictionType=\"integer\"\n"
                                + "        android:title=\"@string/title_number\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@string/default_rank\"\n"
                                + "        android:description=\"@string/description_rank\"\n"
                                + "        android:entries=\"@array/entries_rank\"\n"
                                + "        android:entryValues=\"@array/entry_values_rank\"\n"
                                + "        android:key=\"rank\"\n"
                                + "        android:restrictionType=\"choice\"\n"
                                + "        android:title=\"@string/title_rank\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@array/default_approvals\"\n"
                                + "        android:description=\"@string/description_approvals\"\n"
                                + "        android:entries=\"@array/entries_approvals\"\n"
                                + "        android:entryValues=\"@array/entry_values_approvals\"\n"
                                + "        android:key=\"approvals\"\n"
                                + "        android:restrictionType=\"multi-select\"\n"
                                + "        android:title=\"@string/title_approvals\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@string/default_secret_code\"\n"
                                + "        android:description=\"@string/description_secret_code\"\n"
                                + "        android:key=\"secret_code\"\n"
                                + "        android:restrictionType=\"hidden\"\n"
                                + "        android:title=\"@string/title_secret_code\"/>\n"
                                + " \n"
                                + "</restrictions>")

                ));
    }

    public void testMissingRequiredAttributes() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:key [ValidRestrictions]\n"
                        + "    <restriction />\n"
                        + "    ~~~~~~~~~~~~~~~\n"
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:restrictionType [ValidRestrictions]\n"
                        + "    <restriction />\n"
                        + "    ~~~~~~~~~~~~~~~\n"
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:title [ValidRestrictions]\n"
                        + "    <restriction />\n"
                        + "    ~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction />\n"
                                + "</restrictions>")
                ));
    }

    public void testNewSample() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction android:key=\"key_bool\"\n"
                                + "            android:restrictionType=\"bool\"\n"
                                + "            android:title=\"@string/title_bool\"\n"
                                + "            android:description=\"@string/desc_bool\"\n"
                                + "            android:defaultValue=\"true\"\n"
                                + "            />\n"
                                + "    <restriction android:key=\"key_int\"\n"
                                + "            android:restrictionType=\"integer\"\n"
                                + "            android:title=\"@string/title_int\"\n"
                                + "            android:defaultValue=\"15\"\n"
                                + "            />\n"
                                + "    <restriction android:key=\"key_string\"\n"
                                + "            android:restrictionType=\"string\"\n"
                                + "            android:defaultValue=\"@string/string_value\"\n"
                                + "            android:title=\"@string/missing_title\"\n" // MISSING IN SAMPLE!
                                + "            />\n"
                                + "    <restriction android:key=\"components\"\n"
                                + "                 android:restrictionType=\"bundle_array\"\n"
                                + "                 android:title=\"@string/title_bundle_array\"\n"
                                + "                 android:description=\"@string/desc_bundle_array\">\n"
                                + "        <restriction android:restrictionType=\"bundle\"\n"
                                + "                     android:key=\"someKey\"\n"
                                + "                     android:title=\"@string/title_bundle_comp\"\n"
                                + "                     android:description=\"@string/desc_bundle_comp\">\n"
                                + "            <restriction android:key=\"enabled\"\n"
                                + "                         android:restrictionType=\"bool\"\n"
                                + "                         android:defaultValue=\"true\"\n"
                                + "                         android:title=\"@string/missing_title\"\n" // MISSING IN SAMPLE!
                                + "                         />\n"
                                + "            <restriction android:key=\"name\"\n"
                                + "                         android:restrictionType=\"string\"\n"
                                + "                         android:title=\"@string/missing_title\"\n" // MISSING IN SAMPLE!
                                + "                         />\n"
                                + "        </restriction>\n"
                                + "\n"
                                + "    </restriction>\n"
                                + "    <restriction android:key=\"connection_settings\"\n"
                                + "                 android:restrictionType=\"bundle\"\n"
                                + "                 android:title=\"@string/title_bundle\"\n"
                                + "                 android:description=\"@string/desc_bundle\">\n"
                                + "        <restriction android:key=\"max_wait_time_ms\"\n"
                                + "                     android:restrictionType=\"integer\"\n"
                                + "                     android:title=\"@string/title_int\"\n"
                                + "                     android:defaultValue=\"1000\"\n"
                                + "                     />\n"
                                + "        <restriction android:key=\"host\"\n"
                                + "                     android:restrictionType=\"string\"\n"
                                + "                     android:title=\"@string/missing_title\"\n" // MISSING IN SAMPLE!
                                + "                     />\n"
                                + "    </restriction>\n"
                                + "</restrictions>\n")
                ));
    }

    public void testMissingRequiredAttributesForChoice() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:entries [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:entryValues [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "2 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:description=\"@string/description_number\"\n"
                                + "        android:key=\"number\"\n"
                                + "        android:restrictionType=\"choice\"\n"
                                + "        android:title=\"@string/title_number\"/>\n"
                                + "</restrictions>")
                ));
    }

    public void testMissingRequiredAttributesForHidden() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:2: Error: Missing required attribute android:defaultValue [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:description=\"@string/description_number\"\n"
                                + "        android:key=\"number\"\n"
                                + "        android:restrictionType=\"hidden\"\n"
                                + "        android:title=\"@string/title_number\"/>\n"
                                + "</restrictions>")
                ));
    }

    public void testValidNumber() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:3: Error: Invalid number [ValidRestrictions]\n"
                        + "        android:defaultValue=\"abc\"\n"
                        + "                              ~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"abc\"\n" // ERROR
                                + "        android:description=\"@string/description_number\"\n"
                                + "        android:key=\"message1\"\n"
                                + "        android:restrictionType=\"integer\"\n"
                                + "        android:title=\"@string/title_number\"/>\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@integer/default_number\"\n" // OK
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message2\"\n"
                                + "        android:restrictionType=\"integer\"\n"
                                + "        android:title=\"@string/title_number2\"/>\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"123\"\n" // OK
                                + "        android:description=\"@string/description_message2\"\n"
                                + "        android:key=\"message3\"\n"
                                + "        android:restrictionType=\"integer\"\n"
                                + "        android:title=\"@string/title_number3\"/>\n"
                                + "</restrictions>")
                ));
    }

    public void testUnexpectedTag() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:3: Error: Unexpected tag <wrongtag>, expected <restriction> [ValidRestrictions]\n"
                        + "    <wrongtag />\n"
                        + "     ~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <!-- Comments are okay -->\n"
                                + "    <wrongtag />\n"
                                + "</restrictions>")

                ));
    }

    public void testLocalizedKey() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:5: Error: Keys cannot be localized, they should be specified with a string literal [ValidRestrictions]\n"
                        + "        android:key=\"@string/can_say_hello\"\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@bool/default_can_say_hello\"\n"
                                + "        android:description=\"@string/description_can_say_hello\"\n"
                                + "        android:key=\"@string/can_say_hello\"\n"
                                + "        android:restrictionType=\"bool\"\n"
                                + "        android:title=\"@string/title_can_say_hello\"/>\n"
                                + "</restrictions>")

                ));
    }

    public void testDuplicateKeys() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:19: Error: Duplicate key can_say_hello [ValidRestrictions]\n"
                        + "        android:key=\"can_say_hello\"\n"
                        + "                     ~~~~~~~~~~~~~\n"
                        + "    res/xml/app_restrictions.xml:5: Previous use of key here\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@bool/default_can_say_hello\"\n"
                                + "        android:description=\"@string/description_can_say_hello\"\n"
                                + "        android:key=\"can_say_hello\"\n"
                                + "        android:restrictionType=\"bool\"\n"
                                + "        android:title=\"@string/title_can_say_hello\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@string/default_message\"\n"
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"string\"\n"
                                + "        android:title=\"@string/title_message\"/>\n"
                                + " \n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@integer/default_number\"\n"
                                + "        android:description=\"@string/description_number\"\n"
                                + "        android:key=\"can_say_hello\"\n" // ERROR: Duplicate
                                + "        android:restrictionType=\"integer\"\n"
                                + "        android:title=\"@string/title_number\"/>\n"
                                + "</restrictions>")

                ));
    }

    public void testNoDefaultValueForBundles() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:3: Error: Restriction type bundle_array should not have a default value [ValidRestrictions]\n"
                        + "        android:defaultValue=\"@string/default_message\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:defaultValue=\"@string/default_message\"\n"
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"bundle_array\"\n"
                                + "        android:title=\"@string/title_message\">\n"
                                + "      <restriction\n"
                                + "          android:defaultValue=\"@bool/default_can_say_hello\"\n"
                                + "          android:description=\"@string/description_can_say_hello\"\n"
                                + "          android:key=\"can_say_hello\"\n"
                                + "          android:restrictionType=\"string\"\n"
                                + "          android:title=\"@string/title_can_say_hello\"/>\n"
                                + "    </restriction>"
                                + "</restrictions>")

                ));
    }

    public void testNoChildrenForBundle() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:2: Error: Restriction type bundle should have at least one nested restriction [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"bundle\"\n"
                                + "        android:title=\"@string/title_message\"/>\n"
                                + "</restrictions>")

                ));
    }

    public void testNoChildrenForBundleArray() throws Exception {
        assertEquals(""
                        + "res/xml/app_restrictions.xml:2: Error: Expected exactly one child for restriction of type bundle_array [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "    <restriction\n"
                                + "        android:description=\"@string/description_message\"\n"
                                + "        android:key=\"message\"\n"
                                + "        android:restrictionType=\"bundle_array\"\n"
                                + "        android:title=\"@string/title_message\"/>\n"
                                + "</restrictions>")

                ));
    }

    public void testTooManyChildren() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_NUMBER_OF_NESTED_RESTRICTIONS + 2; i++) {
            //noinspection StringConcatenationInsideStringBufferAppend
            sb.append(""
                    + "    <restriction\n"
                    + "        android:defaultValue=\"@bool/default_can_say_hello" + i + "\"\n"
                    + "        android:description=\"@string/description_can_say_hello" + i + "\"\n"
                    + "        android:key=\"can_say_hello" + i + "\"\n"
                    + "        android:restrictionType=\"bool\"\n"
                    + "        android:title=\"@string/title_can_say_hello" + i + "\"/>\n"
                    + " \n");

        }

        assertEquals(""
                        + "res/xml/app_restrictions.xml:1: Error: Invalid nested restriction: too many nested restrictions (was 1002, max 1000) [ValidRestrictions]\n"
                        + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + sb.toString()
                                + "</restrictions>")

                ));
    }

    public void testNestingTooDeep() throws Exception {
        StringBuilder sb = new StringBuilder();
        int maxDepth = MAX_NESTING_DEPTH + 1;
        for (int i = 0; i < maxDepth; i++) {
            //noinspection StringConcatenationInsideStringBufferAppend
            sb.append(""
                    + "    <restriction\n"
                    + "        android:description=\"@string/description_can_say_hello" + i + "\"\n"
                    + "        android:key=\"can_say_hello" + i + "\"\n"
                    + "        android:restrictionType=\"bundle\"\n"
                    + "        android:title=\"@string/title_can_say_hello" + i + "\">\n"
                    + " \n");
        }
        sb.append(""
                + "    <restriction\n"
                + "        android:defaultValue=\"@string/default_message\"\n"
                + "        android:description=\"@string/description_message\"\n"
                + "        android:key=\"message\"\n"
                + "        android:restrictionType=\"string\"\n"
                + "        android:title=\"@string/title_message\"/>\n"
                + " \n");
        for (int i = 0; i < maxDepth; i++) {
            sb.append("    </restriction>\n");
        }

        assertEquals(""
                        + "res/xml/app_restrictions.xml:122: Error: Invalid nested restriction: nesting depth 21 too large (max 20 [ValidRestrictions]\n"
                        + "    <restriction\n"
                        + "    ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(
                        xml("res/xml/app_restrictions.xml", ""
                                + "<restrictions xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + sb.toString()
                                + "</restrictions>")

                ));
    }
}
