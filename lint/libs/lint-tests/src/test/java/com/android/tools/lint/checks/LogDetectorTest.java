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

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class LogDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LogDetector();
    }

    public void test() throws Exception {
        assertEquals(
            "src/test/pkg/LogTest.java:33: Error: Mismatched tags: the d() and isLoggable() calls typically should pass the same tag: TAG1 versus TAG2 [LogTagMismatch]\n" +
            "            Log.d(TAG2, \"message\"); // warn: mismatched tags!\n" +
            "                  ~~~~\n" +
            "    src/test/pkg/LogTest.java:32: Conflicting tag\n" +
            "src/test/pkg/LogTest.java:36: Error: Mismatched tags: the d() and isLoggable() calls typically should pass the same tag: \"my_tag\" versus \"other_tag\" [LogTagMismatch]\n" +
            "            Log.d(\"other_tag\", \"message\"); // warn: mismatched tags!\n" +
            "                  ~~~~~~~~~~~\n" +
            "    src/test/pkg/LogTest.java:35: Conflicting tag\n" +
            "src/test/pkg/LogTest.java:80: Error: Mismatched logging levels: when checking isLoggable level DEBUG, the corresponding log call should be Log.d, not Log.v [LogTagMismatch]\n" +
            "            Log.v(TAG1, \"message\"); // warn: wrong level\n" +
            "                ~\n" +
            "    src/test/pkg/LogTest.java:79: Conflicting tag\n" +
            "src/test/pkg/LogTest.java:83: Error: Mismatched logging levels: when checking isLoggable level DEBUG, the corresponding log call should be Log.d, not Log.v [LogTagMismatch]\n" +
            "            Log.v(TAG1, \"message\"); // warn: wrong level\n" +
            "                ~\n" +
            "    src/test/pkg/LogTest.java:82: Conflicting tag\n" +
            "src/test/pkg/LogTest.java:86: Error: Mismatched logging levels: when checking isLoggable level VERBOSE, the corresponding log call should be Log.v, not Log.d [LogTagMismatch]\n" +
            "            Log.d(TAG1, \"message\"); // warn? verbose is a lower logging level, which includes debug\n" +
            "                ~\n" +
            "    src/test/pkg/LogTest.java:85: Conflicting tag\n" +
            "src/test/pkg/LogTest.java:53: Error: The logging tag can be at most 23 characters, was 43 (really_really_really_really_really_long_tag) [LongLogTag]\n" +
            "            Log.d(\"really_really_really_really_really_long_tag\", \"message\"); // error: too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:59: Error: The logging tag can be at most 23 characters, was 24 (123456789012345678901234) [LongLogTag]\n" +
            "            Log.d(TAG24, \"message\"); // error: too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:60: Error: The logging tag can be at most 23 characters, was 39 (MyReallyReallyReallyReallyReallyLongTag) [LongLogTag]\n" +
            "            Log.d(LONG_TAG, \"message\"); // error: way too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:64: Error: The logging tag can be at most 23 characters, was 39 (MyReallyReallyReallyReallyReallyLongTag) [LongLogTag]\n" +
            "            Log.d(LOCAL_TAG, \"message\"); // error: too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:67: Error: The logging tag can be at most 23 characters, was 28 (1234567890123456789012MyTag1) [LongLogTag]\n" +
            "            Log.d(TAG22 + TAG1, \"message\"); // error: too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:68: Error: The logging tag can be at most 23 characters, was 27 (1234567890123456789012MyTag) [LongLogTag]\n" +
            "            Log.d(TAG22 + \"MyTag\", \"message\"); // error: too long\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:21: Warning: The log call Log.i(...) should be conditional: surround with if (Log.isLoggable(...)) or if (BuildConfig.DEBUG) { ... } [LogConditional]\n" +
            "        Log.i(TAG1, \"message\" + m); // error: unconditional w/ computation\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/LogTest.java:22: Warning: The log call Log.i(...) should be conditional: surround with if (Log.isLoggable(...)) or if (BuildConfig.DEBUG) { ... } [LogConditional]\n" +
            "        Log.i(TAG1, toString()); // error: unconditional w/ computation\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "11 errors, 2 warnings\n",

            lintProject(
                java("src/test/pkg/LogTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.util.Log;\n"
                        + "import static android.util.Log.DEBUG;\n"
                        + "\n"
                        + "@SuppressWarnings(\"UnusedDeclaration\")\n"
                        + "public class LogTest {\n"
                        + "    private static final String TAG1 = \"MyTag1\";\n"
                        + "    private static final String TAG2 = \"MyTag2\";\n"
                        + "    private static final String TAG22 = \"1234567890123456789012\";\n"
                        + "    private static final String TAG23 = \"12345678901234567890123\";\n"
                        + "    private static final String TAG24 = \"123456789012345678901234\";\n"
                        + "    private static final String LONG_TAG = \"MyReallyReallyReallyReallyReallyLongTag\";\n"
                        + "\n"
                        + "    public void checkConditional(String m) {\n"
                        + "        Log.d(TAG1, \"message\"); // ok: unconditional, but not performing computation\n"
                        + "        Log.d(TAG1, m); // ok: unconditional, but not performing computation\n"
                        + "        Log.d(TAG1, \"a\" + \"b\"); // ok: unconditional, but not performing non-constant computation\n"
                        + "        Log.d(TAG1, Constants.MY_MESSAGE); // ok: unconditional, but constant string\n"
                        + "        Log.i(TAG1, \"message\" + m); // error: unconditional w/ computation\n"
                        + "        Log.i(TAG1, toString()); // error: unconditional w/ computation\n"
                        + "        Log.e(TAG1, toString()); // ok: only flagging debug/info messages\n"
                        + "        Log.w(TAG1, toString()); // ok: only flagging debug/info messages\n"
                        + "        Log.wtf(TAG1, toString()); // ok: only flagging debug/info messages\n"
                        + "        if (Log.isLoggable(TAG1, 0)) {\n"
                        + "            Log.d(TAG1, toString()); // ok: conditional\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public void checkWrongTag(String tag) {\n"
                        + "        if (Log.isLoggable(TAG1, Log.DEBUG)) {\n"
                        + "            Log.d(TAG2, \"message\"); // warn: mismatched tags!\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(\"my_tag\", Log.DEBUG)) {\n"
                        + "            Log.d(\"other_tag\", \"message\"); // warn: mismatched tags!\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(\"my_tag\", Log.DEBUG)) {\n"
                        + "            Log.d(\"my_tag\", \"message\"); // ok: strings equal\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, Log.DEBUG)) {\n"
                        + "            Log.d(LogTest.TAG1, \"message\"); // OK: same tag; different access syntax\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(tag, Log.DEBUG)) {\n"
                        + "            Log.d(tag, \"message\"); // ok: same variable\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public void checkLongTag(boolean shouldLog) {\n"
                        + "        if (shouldLog) {\n"
                        + "            // String literal tags\n"
                        + "            Log.d(\"short_tag\", \"message\"); // ok: short\n"
                        + "            Log.d(\"really_really_really_really_really_long_tag\", \"message\"); // error: too long\n"
                        + "\n"
                        + "            // Resolved field tags\n"
                        + "            Log.d(TAG1, \"message\"); // ok: short\n"
                        + "            Log.d(TAG22, \"message\"); // ok: short\n"
                        + "            Log.d(TAG23, \"message\"); // ok: threshold\n"
                        + "            Log.d(TAG24, \"message\"); // error: too long\n"
                        + "            Log.d(LONG_TAG, \"message\"); // error: way too long\n"
                        + "\n"
                        + "            // Locally defined variable tags\n"
                        + "            final String LOCAL_TAG = \"MyReallyReallyReallyReallyReallyLongTag\";\n"
                        + "            Log.d(LOCAL_TAG, \"message\"); // error: too long\n"
                        + "\n"
                        + "            // Concatenated tags\n"
                        + "            Log.d(TAG22 + TAG1, \"message\"); // error: too long\n"
                        + "            Log.d(TAG22 + \"MyTag\", \"message\"); // error: too long\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public void checkWrongLevel(String tag) {\n"
                        + "        if (Log.isLoggable(TAG1, Log.DEBUG)) {\n"
                        + "            Log.d(TAG1, \"message\"); // ok: right level\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, Log.INFO)) {\n"
                        + "            Log.i(TAG1, \"message\"); // ok: right level\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, Log.DEBUG)) {\n"
                        + "            Log.v(TAG1, \"message\"); // warn: wrong level\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, DEBUG)) { // static import of level\n"
                        + "            Log.v(TAG1, \"message\"); // warn: wrong level\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, Log.VERBOSE)) {\n"
                        + "            Log.d(TAG1, \"message\"); // warn? verbose is a lower logging level, which includes debug\n"
                        + "        }\n"
                        + "        if (Log.isLoggable(TAG1, Constants.MY_LEVEL)) {\n"
                        + "            Log.d(TAG1, \"message\"); // ok: unknown level alias\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    @SuppressLint(\"all\")\n"
                        + "    public void suppressed1() {\n"
                        + "        Log.d(TAG1, \"message\"); // ok: suppressed\n"
                        + "    }\n"
                        + "\n"
                        + "    @SuppressLint(\"LogConditional\")\n"
                        + "    public void suppressed2() {\n"
                        + "        Log.d(TAG1, \"message\"); // ok: suppressed\n"
                        + "    }\n"
                        + "\n"
                        + "    private static class Constants {\n"
                        + "        public static final String MY_MESSAGE = \"My Message\";\n"
                        + "        public static final int MY_LEVEL = 5;\n"
                        + "    }\n"
                        + "}")
            ));
    }
}
