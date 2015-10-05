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
public class StringFormatDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new StringFormatDetector();
    }

    public void testAll() throws Exception {
        assertEquals(
            "src/test/pkg/StringFormatActivity.java:13: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String (argument #2 in method call) [StringFormatMatches]\n" +
            "        String output1 = String.format(hello, target);\n" +
            "                                              ~~~~~~\n" +
            "    res/values-es/formatstrings.xml:3: Conflicting argument declaration here\n" +
            "src/test/pkg/StringFormatActivity.java:15: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        String output2 = String.format(hello2, target, \"How are you\");\n" +
            "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:21: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) [StringFormatMatches]\n" +
            "        String output4 = String.format(score, true);  // wrong\n" +
            "                                              ~~~~\n" +
            "    res/values/formatstrings.xml:6: Conflicting argument declaration here\n" +
            "src/test/pkg/StringFormatActivity.java:22: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) [StringFormatMatches]\n" +
            "        String output  = String.format(score, won);   // wrong\n" +
            "                                              ~~~\n" +
            "    res/values/formatstrings.xml:6: Conflicting argument declaration here\n" +
            "src/test/pkg/StringFormatActivity.java:24: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        String.format(getResources().getString(R.string.hello2), target, \"How are you\");\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:26: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
            "        getResources().getString(R.string.hello2, target, \"How are you\");\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/formatstrings.xml:4: This definition requires 3 arguments\n" +
            "src/test/pkg/StringFormatActivity.java:33: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String (argument #2 in method call) [StringFormatMatches]\n" +
            "        String output1 = String.format(hello, target);\n" +
            "                                              ~~~~~~\n" +
            "    res/values-es/formatstrings.xml:3: Conflicting argument declaration here\n" +
            "res/values-es/formatstrings.xml:3: Error: Inconsistent formatting types for argument #1 in format string hello ('%1$d'): Found both 's' and 'd' (in values/formatstrings.xml) [StringFormatMatches]\n" +
            "    <string name=\"hello\">%1$d</string>\n" +
            "                         ~~~~\n" +
            "    res/values/formatstrings.xml:3: Conflicting argument type here\n" +
            "res/values-es/formatstrings.xml:4: Warning: Inconsistent number of arguments in formatting string hello2; found both 2 and 3 [StringFormatCount]\n" +
            "    <string name=\"hello2\">%3$d: %1$s, %2$s?</string>\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values/formatstrings.xml:4: Conflicting number of arguments here\n" +
            "res/values/formatstrings.xml:5: Warning: Formatting string 'missing' is not referencing numbered arguments [1, 2] [StringFormatCount]\n" +
            "    <string name=\"missing\">Hello %3$s World</string>\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "8 errors, 2 warnings\n",

            lintProject(
                    "res/values/formatstrings.xml",
                    "res/values-es/formatstrings.xml",
                    // Java files must be renamed in source tree
                    "src/test/pkg/StringFormatActivity.java.txt=>src/test/pkg/StringFormatActivity.java"
                ));
    }

    public void testArgCount() {
        assertEquals(0, StringFormatDetector.getFormatArgumentCount(
                "%n%% ", null));
        assertEquals(1, StringFormatDetector.getFormatArgumentCount(
                "%n%% %s", null));
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
                "First: %n%% %1$s, Second %2$s, Third %3$s", 1));
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
                    copy("res/values/formatstrings_ignore.xml", "res/values/formatstrings.xml"),
                    copy("res/values-es/formatstrings_ignore.xml", "res/values-es/formatstrings.xml"),
                    java("src/test/pkg/StringFormatActivity_ignore.java.txt", ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.annotation.SuppressLint;\n"
                            + "import android.app.Activity;\n"
                            + "import android.os.Bundle;\n"
                            + "\n"
                            + "public class StringFormatActivity extends Activity {\n"
                            + "    /** Called when the activity is first created. */\n"
                            + "    @SuppressLint(\"all\")\n"
                            + "    @Override\n"
                            + "    public void onCreate(Bundle savedInstanceState) {\n"
                            + "        super.onCreate(savedInstanceState);\n"
                            + "        String target = \"World\";\n"
                            + "        String hello = getResources().getString(R.string.hello);\n"
                            + "        String output1 = String.format(hello, target);\n"
                            + "        String hello2 = getResources().getString(R.string.hello2);\n"
                            + "        String output2 = String.format(hello2, target, \"How are you\");\n"
                            + "        setContentView(R.layout.main);\n"
                            + "        String score = getResources().getString(R.string.score);\n"
                            + "        int points = 50;\n"
                            + "        boolean won = true;\n"
                            + "        String output3 = String.format(score, points);\n"
                            + "        String output4 = String.format(score, true);  // wrong\n"
                            + "        String output  = String.format(score, won);   // wrong\n"
                            + "        String output5 = String.format(score, 75);\n"
                            + "    }\n"
                            + "\n"
                            + "    private static class R {\n"
                            + "        private static class string {\n"
                            + "            public static final int hello = 1;\n"
                            + "            public static final int hello2 = 2;\n"
                            + "            public static final int score = 3;\n"
                            + "        }\n"
                            + "        private static class layout {\n"
                            + "            public static final int main = 4;\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n")
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
        assertEquals(
                "src/test/pkg/StringFormat3.java:12: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String (argument #3 in method call) [StringFormatMatches]\n" +
                "                context.getString(R.string.gridview_views_count), article.playsCount);\n" +
                "                                                                  ~~~~~~~~~~~~~~~~~~\n" +
                "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormat3.java:16: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String (argument #3 in method call) [StringFormatMatches]\n" +
                "                context.getString(R.string.gridview_views_count), \"wrong\");\n" +
                "                                                                  ~~~~~~~\n" +
                "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormat3.java:17: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String (argument #2 in method call) [StringFormatMatches]\n" +
                "        String s4 = String.format(context.getString(R.string.gridview_views_count), \"wrong\");\n" +
                "                                                                                    ~~~~~~~\n" +
                "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormat3.java:22: Error: Wrong argument type for formatting argument '#1' in gridview_views_count: conversion is 'd', received String (argument #3 in method call) [StringFormatMatches]\n" +
                "                context.getString(R.string.gridview_views_count), \"string\");\n" +
                "                                                                  ~~~~~~~~\n" +
                "    res/values/formatstrings5.xml:3: Conflicting argument declaration here\n" +
                "res/values/formatstrings5.xml:3: Warning: Formatting %d followed by words (\"vues\"): This should probably be a plural rather than a string [PluralsCandidate]\n" +
                "    <string name=\"gridview_views_count\">%d vues</string>\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "4 errors, 1 warnings\n",

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

    public void testGetStringAsParameter() throws Exception {
        assertEquals(""
                + "src/test/pkg/StringFormat4.java:11: Error: Wrong argument count, format string error_and_source requires 2 but format call supplies 1 [StringFormatMatches]\n"
                + "        getString(R.string.error_and_source, getString(R.string.data_source)); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/formatstrings6.xml:24: This definition requires 2 arguments\n"
                + "src/test/pkg/StringFormat4.java:13: Error: Wrong argument count, format string error_and_source requires 2 but format call supplies 1 [StringFormatMatches]\n"
                + "        getString(R.string.error_and_source, \"data source\"); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/formatstrings6.xml:24: This definition requires 2 arguments\n"
                + "2 errors, 0 warnings\n",

                lintProject(
                        "res/values/formatstrings6.xml",
                        "src/test/pkg/StringFormat4.java.txt=>src/test/pkg/StringFormat4.java"));
    }

    public void testNotLocaleMethod() throws Exception {
        // https://code.google.com/p/android/issues/detail?id=53238
        assertEquals(""
                + "No warnings.",

                lintProject(
                        "res/values/formatstrings7.xml",
                        "src/test/pkg/StringFormat5.java.txt=>src/test/pkg/StringFormat5.java"));
    }

    public void testNewlineChar() throws Exception {
        // https://code.google.com/p/android/issues/detail?id=65692
        assertEquals(""
                + "src/test/pkg/StringFormat8.java:12: Error: Wrong argument count, format string amount_string requires 1 but format call supplies 0 [StringFormatMatches]\n"
                + "        String amount4 = String.format(getResources().getString(R.string.amount_string));  // ERROR\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/formatstrings8.xml:2: This definition requires 1 arguments\n"
                + "src/test/pkg/StringFormat8.java:13: Error: Wrong argument count, format string amount_string requires 1 but format call supplies 2 [StringFormatMatches]\n"
                + "        String amount5 = getResources().getString(R.string.amount_string, amount, amount); // ERROR\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/formatstrings8.xml:2: This definition requires 1 arguments\n"
                + "2 errors, 0 warnings\n",

                lintProject(
                        "res/values/formatstrings8.xml",
                        "src/test/pkg/StringFormat8.java.txt=>src/test/pkg/StringFormat8.java"));
    }

    public void testIncremental() throws Exception {
        assertEquals(
                "src/test/pkg/StringFormatActivity.java:13: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String (argument #2 in method call) [StringFormatMatches]\n" +
                "        String output1 = String.format(hello, target);\n" +
                "                                              ~~~~~~\n" +
                "    res/values-es/formatstrings.xml: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormatActivity.java:15: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
                "        String output2 = String.format(hello2, target, \"How are you\");\n" +
                "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    res/values-es/formatstrings.xml: This definition requires 3 arguments\n" +
                "src/test/pkg/StringFormatActivity.java:21: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) [StringFormatMatches]\n" +
                "        String output4 = String.format(score, true);  // wrong\n" +
                "                                              ~~~~\n" +
                "    res/values/formatstrings.xml: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormatActivity.java:22: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) [StringFormatMatches]\n" +
                "        String output  = String.format(score, won);   // wrong\n" +
                "                                              ~~~\n" +
                "    res/values/formatstrings.xml: Conflicting argument declaration here\n" +
                "src/test/pkg/StringFormatActivity.java:24: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
                "        String.format(getResources().getString(R.string.hello2), target, \"How are you\");\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    res/values-es/formatstrings.xml: This definition requires 3 arguments\n" +
                "src/test/pkg/StringFormatActivity.java:26: Error: Wrong argument count, format string hello2 requires 3 but format call supplies 2 [StringFormatMatches]\n" +
                "        getResources().getString(R.string.hello2, target, \"How are you\");\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    res/values-es/formatstrings.xml: This definition requires 3 arguments\n" +
                "src/test/pkg/StringFormatActivity.java:33: Error: Wrong argument type for formatting argument '#1' in hello: conversion is 'd', received String (argument #2 in method call) [StringFormatMatches]\n" +
                "        String output1 = String.format(hello, target);\n" +
                "                                              ~~~~~~\n" +
                "    res/values-es/formatstrings.xml: Conflicting argument declaration here\n" +
                "res/values/formatstrings.xml: Error: Inconsistent formatting types for argument #1 in format string hello ('%1$s'): Found both 'd' and 's' (in values-es/formatstrings.xml) [StringFormatMatches]\n" +
                "    res/values-es/formatstrings.xml: Conflicting argument type here\n" +
                "res/values/formatstrings.xml: Warning: Inconsistent number of arguments in formatting string hello2; found both 3 and 2 [StringFormatCount]\n" +
                "    res/values-es/formatstrings.xml: Conflicting number of arguments here\n" +
                "8 errors, 1 warnings\n",

        lintProjectIncrementally(
                "src/test/pkg/StringFormatActivity.java",
                "res/values/formatstrings.xml",
                "res/values-es/formatstrings.xml",
                // Java files must be renamed in source tree
                "src/test/pkg/StringFormatActivity.java.txt=>src/test/pkg/StringFormatActivity.java"
        ));
    }

    public void testNotStringFormat() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=67597
        assertEquals("No warnings.",

                lintProject(
                        "res/values/formatstrings3.xml",//"res/values/formatstrings.xml",
                        "res/values/shared_prefs_keys.xml",
                        "src/test/pkg/SharedPrefsTest6.java.txt=>src/test/pkg/SharedPrefsFormat.java"));
    }

    public void testNotStringFormatIncrementally() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=67597
        assertEquals("No warnings.",

                lintProjectIncrementally(
                        "src/test/pkg/SharedPrefsFormat.java",

                        "res/values/formatstrings3.xml",//"res/values/formatstrings.xml",
                        "res/values/shared_prefs_keys.xml",
                        "src/test/pkg/SharedPrefsTest6.java.txt=>src/test/pkg/SharedPrefsFormat.java"));
    }

    public void testIncrementalNonMatch() throws Exception {
        // Regression test for scenario where the below source files would crash during
        // a string format check with
        //   java.lang.IllegalStateException: No match found
        //       at java.util.regex.Matcher.group(Matcher.java:468)
        //       at com.android.tools.lint.checks.StringFormatDetector.checkStringFormatCall(StringFormatDetector.java:1028)
        // ...
        assertEquals("No warnings.",

                lintProjectIncrementally(
                        "src/test/pkg/StringFormatActivity3.java",
                        "res/values/formatstrings11.xml",
                        "res/values/formatstrings11.xml=>res/values-de/formatstrings11de.xml",
                        "src/test/pkg/StringFormatActivity3.java.txt=>src/test/pkg/StringFormatActivity3.java"));
    }

    public void testXliff() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/formatstrings9.xml",
                        "src/test/pkg/StringFormat9.java.txt=>src/test/pkg/StringFormat9.java"
                ));
    }

    public void testXliffIncremental() throws Exception {
        assertEquals(
                "No warnings.",

                lintProjectIncrementally(
                        "src/test/pkg/StringFormat9.java",
                        "res/values/formatstrings9.xml",
                        "src/test/pkg/StringFormat9.java.txt=>src/test/pkg/StringFormat9.java"
                ));
    }

    public void testBigDecimal() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=69527
        assertEquals("No warnings.",

                lintProject(
                        "res/values/formatstrings10.xml",
                        "src/test/pkg/StringFormat10.java.txt=>src/test/pkg/StringFormat10.java"
                ));

    }

    public void testWrapperClasses() throws Exception {
        assertEquals("No warnings.",

                lintProject(
                        "res/values/formatstrings10.xml",
                        "src/test/pkg/StringFormat11.java.txt=>src/test/pkg/StringFormat11.java"
                ));
    }

    public void testPluralsCandidates() throws Exception {
        assertEquals(""
                + "res/values/plurals_candidates.xml:4: Warning: Formatting %d followed by words (\"times\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"lockscreen_too_many_failed_attempts_dialog_message1\">\n"
                + "    ^\n"
                + "res/values/plurals_candidates.xml:10: Warning: Formatting %d followed by words (\"times\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"lockscreen_too_many_failed_attempts_dialog_message2\">\n"
                + "    ^\n"
                + "res/values/plurals_candidates.xml:14: Warning: Formatting %d followed by words (\"moves\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"win_dialog\">You won in %1$s and %2$d moves!</string>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/plurals_candidates.xml:15: Warning: Formatting %d followed by words (\"times\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"countdown_complete_sub\">Timer was paused %d times</string>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/plurals_candidates.xml:16: Warning: Formatting %d followed by words (\"satellites\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"service_gpsstatus\">Logging: %s (%s with %d satellites)</string>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/plurals_candidates.xml:17: Warning: Formatting %d followed by words (\"seconds\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"sync_log_clocks_unsynchronized\">The clock on your device is incorrect by %1$d seconds%2$s;</string>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/plurals_candidates.xml:18: Warning: Formatting %d followed by words (\"tasks\"): This should probably be a plural rather than a string [PluralsCandidate]\n"
                + "    <string name=\"EPr_manage_purge_deleted_status\">Purged %d tasks!</string>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 7 warnings\n",

                lintProject(
                        "res/values/plurals_candidates.xml",
                        // Should not flag on anything but English strings
                        "res/values/plurals_candidates.xml=>res/values-de/plurals_candidates.xml"

                        ));
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testAdditionalGetStringMethods() throws Exception {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=183643
        //   183643: Lint format detector should apply to Context#getString
        // It also checks that we handle Object[] properly
        assertEquals(""
                + "src/test/pkg/FormatCheck.java:17: Error: Wrong argument count, format string one_arg requires 1 but format call supplies 2 [StringFormatMatches]\n"
                + "        context.getString(R.string.one_arg, \"too\", \"many\"); // ERROR: too many arguments\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:5: This definition requires 1 arguments\n"
                + "src/test/pkg/FormatCheck.java:18: Error: Wrong argument count, format string one_arg requires 1 but format call supplies 0 [StringFormatMatches]\n"
                + "        context.getString(R.string.one_arg, new Object[0]); // ERROR: not enough arguments\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:5: This definition requires 1 arguments\n"
                + "src/test/pkg/FormatCheck.java:20: Error: Wrong argument count, format string one_arg requires 1 but format call supplies 2 [StringFormatMatches]\n"
                + "        context.getString(R.string.one_arg, new Object[] { \"first\", \"second\" }); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:5: This definition requires 1 arguments\n"
                + "src/test/pkg/FormatCheck.java:22: Error: Wrong argument count, format string two_args requires 2 but format call supplies 1 [StringFormatMatches]\n"
                + "        context.getString(R.string.two_args, \"first\"); // ERROR: too few\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:6: This definition requires 2 arguments\n"
                + "src/test/pkg/FormatCheck.java:24: Error: Wrong argument count, format string two_args requires 2 but format call supplies 0 [StringFormatMatches]\n"
                + "        context.getString(R.string.two_args, new Object[0]); // ERROR: not enough arguments\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:6: This definition requires 2 arguments\n"
                + "src/test/pkg/FormatCheck.java:26: Error: Wrong argument count, format string two_args requires 2 but format call supplies 3 [StringFormatMatches]\n"
                + "        context.getString(R.string.two_args, new Object[] { \"first\", \"second\", \"third\" }); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:6: This definition requires 2 arguments\n"
                + "src/test/pkg/FormatCheck.java:36: Error: Wrong argument count, format string one_arg requires 1 but format call supplies 3 [StringFormatMatches]\n"
                + "        fragment.getString(R.string.one_arg, \"too\", \"many\", \"args\"); // ERROR: not enough arguments\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values/strings.xml:5: This definition requires 1 arguments\n"
                + "7 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/FormatCheck.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Fragment;\n"
                                + "import android.content.Context;\n"
                                + "\n"
                                + "\n"
                                + "\n"
                                + "public class FormatCheck {\n"
                                + "    public static void testContext(Context context) {\n"
                                + "        context.getString(R.string.zero_args); // OK: Just looking up the string (includes %1$s)\n"
                                + "        context.getString(R.string.zero_args, \"first\"); // ERROR\n"
                                + "        context.getString(R.string.zero_args, new Object[0]); // OK\n"
                                + "        context.getString(R.string.zero_args, new Object[] { \"first\" }); // ERROR\n"
                                + "\n"
                                + "        context.getString(R.string.one_arg); // OK: Just looking up the string (includes %1$s)\n"
                                + "        context.getString(R.string.one_arg, \"first\"); // OK\n"
                                + "        context.getString(R.string.one_arg, \"too\", \"many\"); // ERROR: too many arguments\n"
                                + "        context.getString(R.string.one_arg, new Object[0]); // ERROR: not enough arguments\n"
                                + "        context.getString(R.string.one_arg, new Object[] { \"first\" }); // OK\n"
                                + "        context.getString(R.string.one_arg, new Object[] { \"first\", \"second\" }); // ERROR\n"
                                + "        \n"
                                + "        context.getString(R.string.two_args, \"first\"); // ERROR: too few\n"
                                + "        context.getString(R.string.two_args, \"first\", \"second\"); // OK\n"
                                + "        context.getString(R.string.two_args, new Object[0]); // ERROR: not enough arguments\n"
                                + "        context.getString(R.string.two_args, new Object[] { \"first\", \"second\" }); // OK\n"
                                + "        context.getString(R.string.two_args, new Object[] { \"first\", \"second\", \"third\" }); // ERROR\n"
                                + "        String[] args2 = new String[] { \"first\", \"second\" };\n"
                                + "        context.getString(R.string.two_args, args2); // OK\n"
                                + "        String[] args3 = new String[] { \"first\", \"second\", \"third\" };\n"
                                + "        context.getString(R.string.two_args, args3); // ERROR\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testFragment(Fragment fragment) {\n"
                                + "        fragment.getString(R.string.one_arg); // OK: Just looking up the string\n"
                                + "        fragment.getString(R.string.one_arg, \"\"); // OK: Not checking non-varargs version\n"
                                + "        fragment.getString(R.string.one_arg, \"too\", \"many\", \"args\"); // ERROR: not enough arguments\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testArrayTypeConversions(Context context) {\n"
                                + "        context.getString(R.string.one_arg, new Object[] { 5 }); // ERROR: Wrong type\n"
                                + "        context.getString(R.string.two_args, new Object[] { 5, 5.0f }); // ERROR: Wrong type\n"
                                + "    }\n"
                                + "\n"
                                + "    public static final class R {\n"
                                + "        public static final class string {\n"
                                + "            public static final int hello = 0x7f0a0000;\n"
                                + "            public static final int zero_args = 0x7f0a0001;\n"
                                + "            public static final int one_arg = 0x7f0a0002;\n"
                                + "            public static final int two_args = 0x7f0a0003;\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"),
                        xml("res/values/strings.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<resources>\n"
                                + "    <string name=\"hello\">Hello %1$s</string>\n"
                                + "    <string name=\"zero_args\">Hello</string>\n"
                                + "    <string name=\"one_arg\">Hello %1$s</string>\n"
                                + "    <string name=\"two_args\">Hello %1$s %2$s</string>\n"
                                + "</resources>\n"
                                + "\n")
                ));
    }
}
