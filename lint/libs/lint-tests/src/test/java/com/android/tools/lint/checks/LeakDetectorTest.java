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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class LeakDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LeakDetector();
    }

    @SuppressWarnings("ALL") // sample code
    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/LeakTest.java:18: Warning: Do not place Android context classes in static fields; this is a memory leak (and also breaks Instant Run) [StaticFieldLeak]\n"
                + "    private static Activity sField7; // LEAK!\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LeakTest.java:19: Warning: Do not place Android context classes in static fields; this is a memory leak (and also breaks Instant Run) [StaticFieldLeak]\n"
                + "    private static Fragment sField8; // LEAK!\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LeakTest.java:20: Warning: Do not place Android context classes in static fields; this is a memory leak (and also breaks Instant Run) [StaticFieldLeak]\n"
                + "    private static Button sField9; // LEAK!\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/LeakTest.java:21: Warning: Do not place Android context classes in static fields (static reference to MyObject which has field mActivity pointing to Activity); this is a memory leak (and also breaks Instant Run) [StaticFieldLeak]\n"
                + "    private static MyObject sField10;\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

        lintProject(
                java("src/test/pkg/LeakTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.app.Activity;\n"
                        + "import android.app.Fragment;\n"
                        + "import android.widget.Button;\n"
                        + "\n"
                        + "import java.util.List;\n"
                        + "\n"
                        + "@SuppressWarnings(\"unused\")\n"
                        + "public class LeakTest {\n"
                        + "    private static int sField1;\n"
                        + "    private static Object sField2;\n"
                        + "    private static String sField3;\n"
                        + "    private static List sField4;\n"
                        + "    private int mField5;\n"
                        + "    private Activity mField6;\n"
                        + "    private static Activity sField7; // LEAK!\n"
                        + "    private static Fragment sField8; // LEAK!\n"
                        + "    private static Button sField9; // LEAK!\n"
                        + "    private static MyObject sField10;\n"
                        + "    private MyObject mField11;\n"
                        + "    @SuppressLint(\"StaticFieldLeak\")\n"
                        + "    private static Activity sField12;\n"
                        + "\n"
                        + "    private static class MyObject {\n"
                        + "        private int mKey;\n"
                        + "        private Activity mActivity;\n"
                        + "    }\n"
                        + "}\n")));
    }
}
