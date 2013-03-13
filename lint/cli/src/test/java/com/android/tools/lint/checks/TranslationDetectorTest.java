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
public class TranslationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TranslationDetector();
    }

    @Override
    protected boolean includeParentPath() {
        return true;
    }

    public void testTranslation() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            // Sample files from the Home app
            "res/values/strings.xml:20: Error: \"show_all_apps\" is not translated in nl-rNL [MissingTranslation]\n" +
            "    <string name=\"show_all_apps\">All</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in nl-rNL [MissingTranslation]\n" +
            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:25: Error: \"menu_settings\" is not translated in cs, de-rDE, es, es-rUS, nl-rNL [MissingTranslation]\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values-cs/arrays.xml:3: Error: \"security_questions\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "  <string-array name=\"security_questions\">\n" +
            "                ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/strings.xml:12: Also translated here\n" +
            "res/values-de-rDE/strings.xml:11: Error: \"continue_skip_label\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "5 errors, 0 warnings\n" +
            "",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-cs/arrays.xml",
                 "res/values-es/donottranslate.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testTranslationWithCompleteRegions() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            // Sample files from the Home app
            "res/values/strings.xml:19: Error: \"home_title\" is not translated in es-rUS [MissingTranslation]\n" +
            "    <string name=\"home_title\">Home Sample</string>\n" +
            "            ~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:20: Error: \"show_all_apps\" is not translated in es-rUS, nl-rNL [MissingTranslation]\n" +
            "    <string name=\"show_all_apps\">All</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in es-rUS, nl-rNL [MissingTranslation]\n" +
            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:25: Error: \"menu_settings\" is not translated in cs, de-rDE, es-rUS, nl-rNL [MissingTranslation]\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:29: Error: \"wallpaper_instructions\" is not translated in es-rUS [MissingTranslation]\n" +
            "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-land/strings.xml:19: <No location-specific message\n" +
            "res/values-de-rDE/strings.xml:11: Error: \"continue_skip_label\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "6 errors, 0 warnings\n" +
            "",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testHandleBom() throws Exception {
        // This isn't really testing translation detection; it's just making sure that the
        // XML parser doesn't bomb on BOM bytes (byte order marker) at the beginning of
        // the XML document
        assertEquals(
            "No warnings.",
            lintProject(
                 "res/values-de/strings.xml"
            ));
    }

    public void testTranslatedArrays() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values/translatedarrays.xml",
                 "res/values-cs/translatedarrays.xml"));
    }

    public void testTranslationSuppresss() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/strings_ignore.xml=>res/values/strings.xml",
                    "res/values-es/strings_ignore.xml=>res/values-es/strings.xml",
                    "res/values-nl-rNL/strings.xml=>res/values-nl-rNL/strings.xml"));
    }

    public void testMixedTranslationArrays() throws Exception {
        // See issue http://code.google.com/p/android/issues/detail?id=29263
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/strings3.xml=>res/values/strings.xml",
                        "res/values-fr/strings.xml=>res/values-fr/strings.xml"));
    }

    public void testLibraryProjects() throws Exception {
        // If a library project provides additional locales, that should not force
        // the main project to include all those translations
        assertEquals(
            "No warnings.",

             lintProject(
                 // Master project
                 "multiproject/main-manifest.xml=>AndroidManifest.xml",
                 "multiproject/main.properties=>project.properties",
                 "res/values/strings2.xml",

                 // Library project
                 "multiproject/library-manifest.xml=>../LibraryProject/AndroidManifest.xml",
                 "multiproject/library.properties=>../LibraryProject/project.properties",

                 "res/values/strings.xml=>../LibraryProject/res/values/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-cs/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-de/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-nl/strings.xml"
             ));
    }

    public void testNonTranslatable1() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "res/values-nb/nontranslatable.xml:3: Error: The resource string \"dummy\" has been marked as translatable=\"false\" [ExtraTranslation]\n" +
            "    <string name=\"dummy\">Ignore Me</string>\n" +
            "            ~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/nontranslatable.xml",
                    "res/values/nontranslatable2.xml=>res/values-nb/nontranslatable.xml"));
    }

    public void testNonTranslatable2() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "res/values-nb/nontranslatable.xml:3: Error: Non-translatable resources should only be defined in the base values/ folder [ExtraTranslation]\n" +
            "    <string name=\"dummy\" translatable=\"false\">Ignore Me</string>\n" +
            "                         ~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/nontranslatable.xml=>res/values-nb/nontranslatable.xml"));
    }

    public void testSpecifiedLanguageOk() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values-es/strings.xml=>res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml"));
    }

    public void testSpecifiedLanguage() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values-es/strings_locale.xml=>res/values/strings.xml",
                 "res/values-es-rUS/strings.xml"));
    }

    public void testAnalytics() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=43070
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/analytics.xml",
                        "res/values-es/donottranslate.xml" // to make app multilingual
                ));
    }

    public void testIssue33845() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=33845
        assertEquals(""
                + "res/values/strings.xml:5: Error: \"dateTimeFormat\" is not translated in de [MissingTranslation]\n"
                + "    <string name=\"dateTimeFormat\">MM/dd/yyyy - HH:mm</string>\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "locale33845/.classpath=>.classpath",
                        "locale33845/AndroidManifest.xml=>AndroidManifest.xml",
                        "locale33845/project.properties=>project.properties",
                        "locale33845/res/values/strings.xml=>res/values/strings.xml",
                        "locale33845/res/values-de/strings.xml=>res/values-de/strings.xml",
                        "locale33845/res/values-en-rGB/strings.xml=>res/values-en-rGB/strings.xml"
                ));
    }
}
