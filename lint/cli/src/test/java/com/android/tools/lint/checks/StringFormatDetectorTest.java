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

import static com.android.tools.lint.checks.StringFormatDetector.isLocaleSpecific;

import com.android.tools.lint.detector.api.Detector;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class StringFormatDetectorTest  extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new StringFormatDetector();
    }

    public void testAll() throws Exception {
        assertEquals(
            "src/test/pkg/StringFormatActivity.java:13: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String [StringFormatMatches]\n" +
            "        String output1 = String.format(hello, target);\n" +
            "                                              ~~~~~~\n" +
            "    res/values-es/formatstrings.xml:3: Conflicting argument declaration here\n" +
            "src/test/pkg/StringFormatActivity.java:15: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        String output2 = String.format(hello2, target, \"How are you\");\n" +
            "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:24: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        String.format(getResources().getString(R.string.hello2), target, \"How are you\");\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:25: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        getResources().getString(hello2, target, \"How are you\");\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:26: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        getResources().getString(R.string.hello2, target, \"How are you\");\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:33: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String [StringFormatMatches]\n" +
            "        String output1 = String.format(hello, target);\n" +
            "                                              ~~~~~~\n" +
            "    res/values-es/formatstrings.xml:3: Conflicting argument declaration here\n" +
            "res/values-es/formatstrings.xml:3: Error: Inconsistent formatting types for argument #1 in format string hello ('%1$d'): Found both 's' and 'd' (in values/formatstrings.xml) [StringFormatMatches]\n" +
            "    <string name=\"hello\">%1$d</string>\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values/formatstrings.xml:3: Conflicting argument type here\n" +
            "res/values-es/formatstrings.xml:4: Warning: Inconsistent number of arguments in formatting string hello2; found both 2 and 3 [StringFormatCount]\n" +
            "    <string name=\"hello2\">%3$d: %1$s, %2$s?</string>\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values/formatstrings.xml:4: Conflicting number of arguments here\n" +
            "res/values/formatstrings.xml:5: Warning: Formatting string 'missing' is not referencing numbered arguments [1, 2] [StringFormatCount]\n" +
            "    <string name=\"missing\">Hello %3$s World</string>\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "7 errors, 2 warnings\n" +
            "",

            lintProject(
                    "res/values/formatstrings.xml",
                    "res/values-es/formatstrings.xml",
                    // Java files must be renamed in source tree
                    "src/test/pkg/StringFormatActivity.java.txt=>src/test/pkg/StringFormatActivity.java"
                ));
    }

    public void testArgCount() {
        assertEquals(3, StringFormatDetector.getFormatArgumentCount(
                "First: %1$s, Second %2$s, Third %3$s", null));
        assertEquals(11, StringFormatDetector.getFormatArgumentCount(
                "Skipping stuff: %11$s", null));
        assertEquals(1, StringFormatDetector.getFormatArgumentCount(
                "First: %1$s, Skip \\%2$s", null));
        assertEquals(1, StringFormatDetector.getFormatArgumentCount(
                "First: %s, Skip \\%s", null));

        Set<Integer> indices = new HashSet<Integer>();
        assertEquals(11, StringFormatDetector.getFormatArgumentCount(
                "Skipping stuff: %2$d %11$s", indices));
        assertEquals(2, indices.size());
        assertTrue(indices.contains(2));
        assertTrue(indices.contains(11));
    }

    public void testArgType() {
        assertEquals("s", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Second %2$s, Third %3$s", 1));
        assertEquals("d", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Second %2$-5d, Third %3$s", 2));
        assertEquals("s", StringFormatDetector.getFormatArgumentType(
                "Skipping stuff: %11$s",11));
        assertEquals("d", StringFormatDetector.getFormatArgumentType(
                "First: %1$s, Skip \\%2$s, Value=%2$d", 2));
    }

    public void testWrongSyntax() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings2.xml"
                ));
    }

    public void testDateStrings() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings-version1.xml=>res/values-tl/donottranslate-cldr.xml",
                    "res/values/formatstrings-version2.xml=>res/values/donottranslate-cldr.xml"
                ));
    }

    public void testUa() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings-version1.xml=>res/values-tl/donottranslate-cldr.xml",
                    "src/test/pkg/StringFormat2.java.txt=>src/test/pkg/StringFormat2.java"
                ));
    }

    public void testSuppressed() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/formatstrings_ignore.xml=>res/values/formatstrings.xml",
                    "res/values-es/formatstrings_ignore.xml=>res/values-es/formatstrings.xml",
                    "src/test/pkg/StringFormatActivity_ignore.java.txt=>src/test/pkg/StringFormatActivity.java"
                ));
    }

    public void testIssue27108() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject("res/values/formatstrings3.xml"));
    }

    public void testIssue39758() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                "res/values/formatstrings4.xml",
                "src/test/pkg/StringFormatActivity2.java.txt=>src/test/pkg/StringFormatActivity2.java"));
    }

    public void testIssue42798() throws Exception {
        // http://code.google.com/p/android/issues/detail?id=42798
        // String playsCount = String.format(Locale.FRANCE, this.context.getString(R.string.gridview_views_count), article.playsCount);
        assertEquals(""
                + "src/test/pkg/StringFormat3.java:12: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String [StringFormatMatches]\n"
                + "              context.getString(R.string.gridview_views_count), \"wrong\");\n"
                + "                                                                ~~~~~~~\n"
                + "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n"
                + "src/test/pkg/StringFormat3.java:13: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String [StringFormatMatches]\n"
                + "        String s4 = String.format(context.getString(R.string.gridview_views_count), \"wrong\");\n"
                + "                                                                                    ~~~~~~~\n"
                + "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n"
                + "2 errors, 0 warnings\n",

                lintProject(
                        "res/values/formatstrings5.xml",
                        "src/test/pkg/StringFormat3.java.txt=>src/test/pkg/StringFormat3.java"));
    }

    public void testIsLocaleSpecific() throws Exception {
        assertFalse(isLocaleSpecific(""));
        assertFalse(isLocaleSpecific("Hello World!"));
        assertFalse(isLocaleSpecific("%% %n"));
        assertFalse(isLocaleSpecific(" %%f"));
        assertFalse(isLocaleSpecific("%x %A %c %b %B %h %n %%"));
        assertTrue(isLocaleSpecific("%f"));
        assertTrue(isLocaleSpecific(" %1$f "));
        assertTrue(isLocaleSpecific(" %5$e "));
        assertTrue(isLocaleSpecific(" %E "));
        assertTrue(isLocaleSpecific(" %g "));
        assertTrue(isLocaleSpecific(" %1$tm %1$te,%1$tY "));
    }
}
