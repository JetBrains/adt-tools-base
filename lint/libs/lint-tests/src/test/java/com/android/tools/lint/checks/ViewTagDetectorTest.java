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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class ViewTagDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ViewTagDetector();
    }

    private final TestFile mViewTagTest = java("src/test/pkg/ViewTagTest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.annotation.SuppressLint;\n"
            + "import android.content.Context;\n"
            + "import android.database.Cursor;\n"
            + "import android.database.MatrixCursor;\n"
            + "import android.view.LayoutInflater;\n"
            + "import android.view.View;\n"
            + "import android.view.ViewGroup;\n"
            + "import android.widget.CursorAdapter;\n"
            + "import android.widget.ImageView;\n"
            + "import android.widget.TextView;\n"
            + "\n"
            + "@SuppressWarnings(\"unused\")\n"
            + "public abstract class ViewTagTest {\n"
            + "    public View newView(Context context, ViewGroup group, Cursor cursor1,\n"
            + "            MatrixCursor cursor2) {\n"
            + "        LayoutInflater inflater = LayoutInflater.from(context);\n"
            + "        View view = inflater.inflate(android.R.layout.activity_list_item, null);\n"
            + "        view.setTag(android.R.id.background, \"Some random tag\"); // OK\n"
            + "        view.setTag(android.R.id.button1, group); // ERROR\n"
            + "        view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon)); // ERROR\n"
            + "        view.setTag(android.R.id.icon1, cursor1); // ERROR\n"
            + "        view.setTag(android.R.id.icon2, cursor2); // ERROR\n"
            + "        view.setTag(android.R.id.copy, new MyViewHolder()); // ERROR\n"
            + "        return view;\n"
            + "    }\n"
            + "\n"
            + "    @SuppressLint(\"ViewTag\")\n"
            + "    public static void checkSuppress(Context context, View view) {\n"
            + "        view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon));\n"
            + "    }\n"
            + "\n"
            + "    private class MyViewHolder {\n"
            + "        View view;\n"
            + "    }\n"
            + "}\n");

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/ViewTagTest.java:21: Warning: Avoid setting views as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]\n"
                + "        view.setTag(android.R.id.button1, group); // ERROR\n"
                + "                                          ~~~~~\n"
                + "src/test/pkg/ViewTagTest.java:22: Warning: Avoid setting views as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]\n"
                + "        view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon)); // ERROR\n"
                + "                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/ViewTagTest.java:23: Warning: Avoid setting cursors as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]\n"
                + "        view.setTag(android.R.id.icon1, cursor1); // ERROR\n"
                + "                                        ~~~~~~~\n"
                + "src/test/pkg/ViewTagTest.java:24: Warning: Avoid setting cursors as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]\n"
                + "        view.setTag(android.R.id.icon2, cursor2); // ERROR\n"
                + "                                        ~~~~~~~\n"
                + "src/test/pkg/ViewTagTest.java:25: Warning: Avoid setting view holders as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]\n"
                + "        view.setTag(android.R.id.copy, new MyViewHolder()); // ERROR\n"
                + "                                       ~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 5 warnings\n",

            lintProject(mViewTagTest));
    }

    public void testICS() throws Exception {
        assertEquals(
            "No warnings.",

            lintProject(
                    mViewTagTest,
                    copy("apicheck/minsdk14.xml", "AndroidManifest.xml")));
    }
}
