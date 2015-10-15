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
public class SetTextDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SetTextDetector();
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        assertEquals(
            "src/test/pkg/CustomScreen.java:13: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]\n" +
            "    view.setText(\"Hardcoded\");\n" +
            "                 ~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:17: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]\n" +
            "    view.setText(Integer.toString(50) + \"%\");\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:17: Warning: Number formatting does not take into account locale settings. Consider using String.format instead. [SetTextI18n]\n" +
            "    view.setText(Integer.toString(50) + \"%\");\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:18: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]\n" +
            "    view.setText(Double.toString(12.5) + \" miles\");\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:18: Warning: Number formatting does not take into account locale settings. Consider using String.format instead. [SetTextI18n]\n" +
            "    view.setText(Double.toString(12.5) + \" miles\");\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:18: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]\n" +
            "    view.setText(Double.toString(12.5) + \" miles\");\n" +
            "                                         ~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:21: Warning: Do not concatenate text displayed with setText. Use resource string with placeholders. [SetTextI18n]\n" +
            "    btn.setText(\"User \" + getUserName());\n" +
            "                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CustomScreen.java:21: Warning: String literal in setText can not be translated. Use Android resources instead. [SetTextI18n]\n" +
            "    btn.setText(\"User \" + getUserName());\n" +
            "                ~~~~~~~\n" +
            "0 errors, 8 warnings\n",

            lintProject(
                java("src/test/pkg/CustomScreen.java", ""
                + "package test.pkg;\n"
                + "\n"
                + "import android.content.Context;\n"
                + "import android.widget.Button;\n"
                + "import android.widget.TextView;\n"
                + "\n"
                + "class CustomScreen {\n"
                + "\n"
                + "  public CustomScreen(Context context) {\n"
                + "    TextView view = new TextView(context);\n"
                + "\n"
                + "    // Should fail - hardcoded string\n"
                + "    view.setText(\"Hardcoded\");\n"
                + "    // Should pass - no letters\n"
                + "    view.setText(\"-\");\n"
                + "    // Should fail - concatenation and toString for numbers.\n"
                + "    view.setText(Integer.toString(50) + \"%\");\n"
                + "    view.setText(Double.toString(12.5) + \" miles\");\n"
                + "\n"
                + "    Button btn = new Button(context);\n"
                + "    btn.setText(\"User \" + getUserName());\n"
                + "    btn.setText(String.format(\"%s of %s users\", Integer.toString(5), Integer.toString(10)));\n"
                + "  }\n"
                + "\n"
                + "  private static String getUserName() {\n"
                + "    return \"stub\";\n"
                + "  }\n"
                + "}\n")));
    }
}
