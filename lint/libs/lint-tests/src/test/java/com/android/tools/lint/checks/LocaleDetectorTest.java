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

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("ALL")
public class LocaleDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LocaleDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/LocaleTest.java:11: Warning: Implicitly using the default locale is a common source of bugs: Use toUpperCase(Locale) instead [DefaultLocale]\n"
                + "        System.out.println(\"WRONG\".toUpperCase());\n"
                + "                                   ~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:16: Warning: Implicitly using the default locale is a common source of bugs: Use toLowerCase(Locale) instead [DefaultLocale]\n"
                + "        System.out.println(\"WRONG\".toLowerCase());\n"
                + "                                   ~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:20: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %f\", 1.0f); // Implies locale\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:21: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %1$f\", 1.0f);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:22: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %e\", 1.0f);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:23: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %d\", 1.0f);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:24: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %g\", 1.0f);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:25: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %g\", 1.0f);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LocaleTest.java:26: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]\n"
                + "        String.format(\"WRONG: %1$tm %1$te,%1$tY\",\n"
                + "        ^\n"
                + "0 errors, 9 warnings\n"
,

            lintProject(
                    java("src/test/pkg/LocaleTest.java", ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.text.*;\n"
                            + "import java.util.*;\n"
                            + "\n"
                            + "public class LocaleTest {\n"
                            + "    public void testStrings() {\n"
                            + "        System.out.println(\"OK\".toUpperCase(Locale.getDefault()));\n"
                            + "        System.out.println(\"OK\".toUpperCase(Locale.US));\n"
                            + "        System.out.println(\"OK\".toUpperCase(Locale.CHINA));\n"
                            + "        System.out.println(\"WRONG\".toUpperCase());\n"
                            + "\n"
                            + "        System.out.println(\"OK\".toLowerCase(Locale.getDefault()));\n"
                            + "        System.out.println(\"OK\".toLowerCase(Locale.US));\n"
                            + "        System.out.println(\"OK\".toLowerCase(Locale.CHINA));\n"
                            + "        System.out.println(\"WRONG\".toLowerCase());\n"
                            + "\n"
                            + "        String.format(Locale.getDefault(), \"OK: %f\", 1.0f);\n"
                            + "        String.format(\"OK: %x %A %c %b %B %h %n %%\", 1, 2, 'c', true, false, 5);\n"
                            + "        String.format(\"WRONG: %f\", 1.0f); // Implies locale\n"
                            + "        String.format(\"WRONG: %1$f\", 1.0f);\n"
                            + "        String.format(\"WRONG: %e\", 1.0f);\n"
                            + "        String.format(\"WRONG: %d\", 1.0f);\n"
                            + "        String.format(\"WRONG: %g\", 1.0f);\n"
                            + "        String.format(\"WRONG: %g\", 1.0f);\n"
                            + "        String.format(\"WRONG: %1$tm %1$te,%1$tY\",\n"
                            + "                new GregorianCalendar(2012, GregorianCalendar.AUGUST, 27));\n"
                            + "    }\n"
                            + "\n"
                            + "    @android.annotation.SuppressLint(\"NewApi\") // DateFormatSymbols requires API 9\n"
                            + "    public void testSimpleDateFormat() {\n"
                            + "        new SimpleDateFormat(); // WRONG\n"
                            + "        new SimpleDateFormat(\"yyyy-MM-dd\"); // WRONG\n"
                            + "        new SimpleDateFormat(\"yyyy-MM-dd\", DateFormatSymbols.getInstance()); // WRONG\n"
                            + "        new SimpleDateFormat(\"yyyy-MM-dd\", Locale.US); // OK\n"
                            + "    }\n"
                            + "}\n")
                    ));
    }

    public void testIgnoreLoggingWithoutLocale() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        java("src/test/pkg/LogTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.util.Log;\n"
                                + "\n"
                                + "public class LogTest {\n"
                                + "    private static final String TAG = \"mytag\";\n"
                                + "\n"
                                + "    // Don't flag String.format inside logging calls\n"
                                + "    public void test(String dataItemName, int eventStatus) {\n"
                                + "        if (Log.isLoggable(TAG, Log.DEBUG)) {\n"
                                + "            Log.d(TAG, String.format(\"CQS:Event=%s, keeping status=%d\", dataItemName,\n"
                                + "                    eventStatus));\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n")
                ));
    }
}
