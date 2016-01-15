/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.tools.lint.checks.AnnotationDetector.SWITCH_TYPE_DEF;
import static com.android.tools.lint.checks.AnnotationDetector.getMissingCases;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "UnnecessaryLocalVariable",
        "ConstantConditionalExpression", "StatementWithEmptyBody", "RedundantCast",
        "MethodMayBeStatic"})
public class AnnotationDetectorTest extends AbstractCheckTest {
    public void test() throws Exception {
        assertEquals(
            "src/test/pkg/WrongAnnotation.java:9: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]\n" +
            "    public static void foobar(View view, @SuppressLint(\"NewApi\") int foo) { // Invalid: class-file check\n" +
            "                                         ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/WrongAnnotation.java:10: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]\n" +
            "        @SuppressLint(\"NewApi\") // Invalid\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/WrongAnnotation.java:12: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]\n" +
            "        @SuppressLint({\"SdCardPath\", \"NewApi\"}) // Invalid: class-file based check on local variable\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/WrongAnnotation.java:14: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]\n" +
            "        @android.annotation.SuppressLint({\"SdCardPath\", \"NewApi\"}) // Invalid (FQN)\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/WrongAnnotation.java:28: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]\n" +
            "        @SuppressLint(\"NewApi\")\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "5 errors, 0 warnings\n",

            lintProject(
                "src/test/pkg/WrongAnnotation.java.txt=>src/test/pkg/WrongAnnotation.java"
            ));
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testUniqueValues() throws Exception {
        assertEquals(""
                + "src/test/pkg/IntDefTest.java:9: Error: Constants STYLE_NO_INPUT and STYLE_NO_FRAME specify the same exact value (2); this is usually a cut & paste or merge error [UniqueConstants]\n"
                + "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n"
                + "                                                           ~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/IntDefTest.java:9: Previous same value\n"
                + "src/test/pkg/IntDefTest.java:28: Error: Constants FLAG3 and FLAG2 specify the same exact value (562949953421312); this is usually a cut & paste or merge error [UniqueConstants]\n"
                + "    @IntDef({FLAG2, FLAG3, FLAG1})\n"
                + "                    ~~~~~\n"
                + "    src/test/pkg/IntDefTest.java:28: Previous same value\n"
                + "2 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/IntDefTest.java", ""
                                + "package test.pkg;\n"
                                + "import android.support.annotation.IntDef;\n"
                                + "import android.annotation.SuppressLint;\n"
                                + "import java.lang.annotation.Retention;\n"
                                + "import java.lang.annotation.RetentionPolicy;\n"
                                + "\n"
                                + "@SuppressLint(\"UnusedDeclaration\")\n"
                                + "public class IntDefTest {\n"
                                + "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface DialogStyle {}\n"
                                + "\n"
                                + "    public static final int STYLE_NORMAL = 0;\n"
                                + "    public static final int STYLE_NO_TITLE = 1;\n"
                                + "    public static final int STYLE_NO_FRAME = 2;\n"
                                + "    public static final int STYLE_NO_INPUT = 2;\n"
                                + "\n"
                                + "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n"
                                + "    @SuppressWarnings(\"UniqueConstants\")\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface SuppressedDialogStyle {}\n"
                                + "\n"
                                + "\n"
                                + "    public static final long FLAG1 = 0x100000000000L;\n"
                                + "    public static final long FLAG2 = 0x0002000000000000L;\n"
                                + "    public static final long FLAG3 = 0x2000000000000L;\n"
                                + "\n"
                                + "    @IntDef({FLAG2, FLAG3, FLAG1})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags {}\n"
                                + "\n"

                                + ""
                                + "}"),
                        copy("src/android/support/annotation/IntDef.java.txt",
                                "src/android/support/annotation/IntDef.java")));
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testFlagStyle() throws Exception {
        assertEquals(""
                + "src/test/pkg/IntDefTest.java:13: Warning: Consider declaring this constant using 1 << 44 instead [ShiftFlags]\n"
                + "    public static final long FLAG5 = 0x100000000000L;\n"
                + "                                     ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:14: Warning: Consider declaring this constant using 1 << 49 instead [ShiftFlags]\n"
                + "    public static final long FLAG6 = 0x0002000000000000L;\n"
                + "                                     ~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:15: Warning: Consider declaring this constant using 1 << 3 instead [ShiftFlags]\n"
                + "    public static final long FLAG7 = 8L;\n"
                + "                                     ~~\n"
                + "0 errors, 3 warnings\n",
                lintProject(
                        java("src/test/pkg/IntDefTest.java", ""
                                + "package test.pkg;\n"
                                + "import android.support.annotation.IntDef;\n"
                                + "\n"
                                + "import java.lang.annotation.Retention;\n"
                                + "import java.lang.annotation.RetentionPolicy;\n"
                                + "\n"
                                + "@SuppressWarnings(\"unused\")\n"
                                + "public class IntDefTest {\n"
                                + "    public static final long FLAG1 = 1;\n"
                                + "    public static final long FLAG2 = 2;\n"
                                + "    public static final long FLAG3 = 1 << 2;\n"
                                + "    public static final long FLAG4 = 1 << 3;\n"
                                + "    public static final long FLAG5 = 0x100000000000L;\n"
                                + "    public static final long FLAG6 = 0x0002000000000000L;\n"
                                + "    public static final long FLAG7 = 8L;\n"
                                + "    public static final long FLAG8 = 9L;\n"
                                + "    public static final long FLAG9 = 0;\n"
                                + "    public static final long FLAG10 = 1;\n"
                                + "    public static final long FLAG11 = -1;\n"
                                + "\n"
                                // Not a flag (missing flag=true)
                                + "    @IntDef({FLAG1, FLAG2, FLAG3})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags1 {}\n"
                                + "\n"
                                // OK: Too few values
                                + "    @IntDef(flag = true, value={FLAG1, FLAG2})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags2 {}\n"
                                + "\n"
                                // OK: Allow 0, 1, -1
                                + "    @IntDef(flag = true, value={FLAG9, FLAG10, FLAG11})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags3 {}\n"
                                + "\n"
                                // OK: Already using shifts
                                + "    @IntDef(flag = true, value={FLAG1, FLAG3, FLAG4})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags4 {}\n"
                                + "\n"
                                // Wrong: should be flagged
                                + "    @IntDef(flag = true, value={FLAG5, FLAG6, FLAG7, FLAG8})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    private @interface Flags5 {}\n"
                                + "}"),
                        copy("src/android/support/annotation/IntDef.java.txt",
                                "src/android/support/annotation/IntDef.java")));
    }

    public void testMissingIntDefSwitchConstants() throws Exception {
        assertEquals(""
                + "src/test/pkg/X.java:40: Warning: Don't use a constant here; expected one of: LENGTH_INDEFINITE, LENGTH_LONG, LENGTH_SHORT [SwitchIntDef]\n"
                + "            case 5:\n"
                + "                 ~\n"
                + "src/test/pkg/X.java:47: Warning: Switch statement on an int with known associated constant missing case LENGTH_LONG [SwitchIntDef]\n"
                + "        switch (duration) {\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/X.java:56: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE, LENGTH_LONG, LENGTH_SHORT [SwitchIntDef]\n"
                + "        switch (duration) {\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/X.java:66: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]\n"
                + "        switch (duration) {\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/X.java:75: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]\n"
                + "        switch ((int)getDuration()) {\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/X.java:85: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]\n"
                + "        switch (true ? getDuration() : 0) {\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/X.java:95: Warning: Switch statement on an int with known associated constant missing case X.LENGTH_SHORT [SwitchIntDef]\n"
                + "            switch (X.getDuration()) {\n"
                + "            ~~~~~~\n"
                + "src/test/pkg/X.java:104: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE [SwitchIntDef]\n"
                + "        switch (duration) {\n"
                + "        ~~~~~~\n"
                + "0 errors, 8 warnings\n",

                lintProject(
                        java("src/test/pkg/X.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.annotation.SuppressLint;\n"
                                + "import android.support.annotation.IntDef;\n"
                                + "\n"
                                + "import java.lang.annotation.Retention;\n"
                                + "import java.lang.annotation.RetentionPolicy;\n"
                                + "\n"
                                + "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\", \"RedundantCast\"})\n"
                                + "public class X {\n"
                                + "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n"
                                + "    @Retention(RetentionPolicy.SOURCE)\n"
                                + "    public @interface Duration {\n"
                                + "    }\n"
                                + "\n"
                                + "    public static final int LENGTH_INDEFINITE = -2;\n"
                                + "    public static final int LENGTH_SHORT = -1;\n"
                                + "    public static final int LENGTH_LONG = 0;\n"
                                + "\n"
                                + "    public void setDuration(@Duration int duration) {\n"
                                + "    }\n"
                                + "\n"
                                + "    @Duration\n"
                                + "    public static int getDuration() {\n"
                                + "        return LENGTH_INDEFINITE;\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testOk(@Duration int duration) {\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case LENGTH_LONG:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testLiteral(@Duration int duration) {\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case 5:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testParameter(@Duration int duration) {\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testMissingAll(@Duration int duration) {\n"
                                + "        // We don't flag these; let the IDE's normal \"empty switch\" check flag it\n"
                                + "        switch (duration) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    @SuppressWarnings(\"UnnecessaryLocalVariable\")\n"
                                + "    public static void testLocalVariableFlow() {\n"
                                + "        int intermediate = getDuration();\n"
                                + "        int duration = intermediate;\n"
                                + "\n"
                                + "        // Missing LENGTH_SHORT\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_LONG:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testMethodCall() {\n"
                                + "        // Missing LENGTH_SHORT\n"
                                + "        switch ((int)getDuration()) {\n"
                                + "            case LENGTH_LONG:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    @SuppressWarnings(\"ConstantConditionalExpression\")\n"
                                + "    public static void testInline() {\n"
                                + "        // Missing LENGTH_SHORT\n"
                                + "        switch (true ? getDuration() : 0) {\n"
                                + "            case LENGTH_LONG:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class SomeOtherClass {\n"
                                + "        private void method() {\n"
                                + "            // Missing LENGTH_SHORT\n"
                                + "            switch (X.getDuration()) {\n"
                                + "                case LENGTH_LONG:\n"
                                + "                case LENGTH_INDEFINITE:\n"
                                + "                    break;\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testMissingWithDefault(@Duration int duration) {\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case LENGTH_LONG:\n"
                                + "            default:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    @SuppressLint(\"SwitchIntDef\")\n"
                                + "    public static void testSuppressAnnotation(@Duration int duration) {\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static void testSuppressComment(@Duration int duration) {\n"
                                + "        //noinspection AndroidLintSwitchIntDef\n"
                                + "        switch (duration) {\n"
                                + "            case LENGTH_SHORT:\n"
                                + "            case LENGTH_INDEFINITE:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"),
                        copy("src/android/support/annotation/IntDef.java.txt",
                                "src/android/support/annotation/IntDef.java")
        ));
    }


    public void testMissingSwitchFailingIntDef() throws Exception {
        assertEquals(""
                + "src/test/pkg/X.java:8: Warning: Switch statement on an int with known associated constant missing case EXACTLY, UNSPECIFIED [SwitchIntDef]\n"
                + "        switch (val) {\n"
                + "        ~~~~~~\n"
                + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/X.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.view.View;"
                                + "\n"
                                + "public class X {\n"
                                + "\n"
                                + "    public void measure(int mode) {\n"
                                + "        int val = View.MeasureSpec.getMode(mode);\n"
                                + "        switch (val) {\n"
                                + "            case View.MeasureSpec.AT_MOST:\n"
                                + "                break;\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"),
                        copy("bytecode/.classpath", ".classpath")));
    }

    public void testGetEnumCases() {
        assertEquals(
                Arrays.asList("LENGTH_INDEFINITE", "LENGTH_SHORT", "LENGTH_LONG"),
                getMissingCases("Don't use a constant here; expected one of: LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG",
                        TextFormat.TEXT));
        assertEquals(
                Collections.singletonList("LENGTH_SHORT"),
                getMissingCases("Switch statement on an int with known associated constant missing case LENGTH_SHORT",
                                TextFormat.TEXT));
    }

    public void testMatchEcjAndExternalFieldNames() throws Exception {
        assertEquals("No warnings.",
                lintProject(java("src/test/pkg/MissingEnum.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.net.wifi.WifiManager;\n"
                        + "\n"
                        + "public class MissingEnum {\n"
                        + "    private WifiManager mWifiManager;\n"
                        + "\n"
                        + "    private void updateAccessPoints() {\n"
                        + "        final int wifiState = mWifiManager.getWifiState();\n"
                        + "        switch (wifiState) {\n"
                        + "            case WifiManager.WIFI_STATE_ENABLING:\n"
                        + "                break;\n"
                        + "            case WifiManager.WIFI_STATE_ENABLED:\n"
                        + "                break;\n"
                        + "            case WifiManager.WIFI_STATE_DISABLING:\n"
                        + "                break;\n"
                        + "            case WifiManager.WIFI_STATE_DISABLED:\n"
                        + "                break;\n"
                        + "            case WifiManager.WIFI_STATE_UNKNOWN:\n"
                        + "                break;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n")));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @Nullable Location location, @NonNull String message) {
        if (issue == SWITCH_TYPE_DEF) {
            assertNotNull("Could not extract message tokens from " + message,
                    getMissingCases(message, TEXT));
        }
    }

    @Override
    protected Detector getDetector() {
        return new AnnotationDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        List<Issue> issues = super.getIssues();

        // Need these issues on to be found by the registry as well to look up scope
        // in id references (these ids are referenced in the unit test java file below)
        issues.add(ApiDetector.UNSUPPORTED);
        issues.add(SdCardDetector.ISSUE);

        return issues;
    }
}
