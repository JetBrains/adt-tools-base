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

@SuppressWarnings("javadoc")
public class ToastDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ToastDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/ToastTest.java:31: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]\n"
                + "        Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ToastTest.java:32: Warning: Expected duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG, a custom duration value is not supported [ShowToast]\n"
                + "        Toast toast = Toast.makeText(context, R.string.app_name, 5000);\n"
                + "                                                                 ~~~~\n"
                + "src/test/pkg/ToastTest.java:32: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]\n"
                + "        Toast toast = Toast.makeText(context, R.string.app_name, 5000);\n"
                + "                      ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ToastTest.java:38: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]\n"
                + "        Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

            lintProject(java("src/test/pkg/ToastTest.java", ""
                    + "package foo.bar;\n"
                    + "\n"
                    + "import android.app.Activity;\n"
                    + "import android.content.Context;\n"
                    + "import android.os.Bundle;\n"
                    + "import android.widget.Toast;\n"
                    + "\n"
                    + "public abstract class ToastTest extends Context {\n"
                    + "    private Toast createToast(Context context) {\n"
                    + "        // Don't warn here\n"
                    + "        return Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                    + "    }\n"
                    + "\n"
                    + "    private void showToast(Context context) {\n"
                    + "        // Don't warn here\n"
                    + "        Toast toast = Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                    + "        System.out.println(\"Other intermediate code here\");\n"
                    + "        int temp = 5 + 2;\n"
                    + "        toast.show();\n"
                    + "    }\n"
                    + "\n"
                    + "    private void showToast2(Context context) {\n"
                    + "        // Don't warn here\n"
                    + "        int duration = Toast.LENGTH_LONG;\n"
                    + "        Toast.makeText(context, \"foo\", Toast.LENGTH_LONG).show();\n"
                    + "        Toast.makeText(context, R.string.app_name, duration).show();\n"
                    + "    }\n"
                    + "\n"
                    + "    private void broken(Context context) {\n"
                    + "        // Errors\n"
                    + "        Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                    + "        Toast toast = Toast.makeText(context, R.string.app_name, 5000);\n"
                    + "        toast.getDuration();\n"
                    + "    }\n"
                    + "\n"
                    + "    // Constructor test\n"
                    + "    public ToastTest(Context context) {\n"
                    + "        Toast.makeText(context, \"foo\", Toast.LENGTH_LONG);\n"
                    + "    }\n"
                    + "\n"
                    + "    @android.annotation.SuppressLint(\"ShowToast\")\n"
                    + "    private void checkSuppress1(Context context) {\n"
                    + "        Toast toast = Toast.makeText(this, \"MyToast\", Toast.LENGTH_LONG);\n"
                    + "    }\n"
                    + "\n"
                    + "    private void checkSuppress2(Context context) {\n"
                    + "        @android.annotation.SuppressLint(\"ShowToast\")\n"
                    + "        Toast toast = Toast.makeText(this, \"MyToast\", Toast.LENGTH_LONG);\n"
                    + "    }\n"
                    + "\n"
                    + "    public static final class R {\n"
                    + "        public static final class string {\n"
                    + "            public static final int app_name = 0x7f0a0000;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}")));
    }
}
