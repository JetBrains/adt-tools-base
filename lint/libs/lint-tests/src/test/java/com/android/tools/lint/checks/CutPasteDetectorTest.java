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

@SuppressWarnings({"javadoc", "ConstantConditions", "UnusedAssignment", "UnnecessaryLocalVariable",
        "ConstantIfStatement", "StatementWithEmptyBody"})
public class CutPasteDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CutPasteDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        assertEquals(
            "src/test/pkg/PasteError.java:15: Warning: The id R.id.textView1 has already been looked up in this method; possible cut & paste error? [CutPasteId]\n" +
            "        View view2 = findViewById(R.id.textView1);\n" +
            "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    src/test/pkg/PasteError.java:14: First usage here\n" +
            "src/test/pkg/PasteError.java:71: Warning: The id R.id.textView1 has already been looked up in this method; possible cut & paste error? [CutPasteId]\n" +
            "            view2 = findViewById(R.id.textView1);\n" +
            "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    src/test/pkg/PasteError.java:68: First usage here\n" +
            "src/test/pkg/PasteError.java:78: Warning: The id R.id.textView1 has already been looked up in this method; possible cut & paste error? [CutPasteId]\n" +
            "            view2 = findViewById(R.id.textView1);\n" +
            "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    src/test/pkg/PasteError.java:76: First usage here\n" +
            "src/test/pkg/PasteError.java:86: Warning: The id R.id.textView1 has already been looked up in this method; possible cut & paste error? [CutPasteId]\n" +
            "            view2 = findViewById(R.id.textView1);\n" +
            "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    src/test/pkg/PasteError.java:83: First usage here\n" +
            "src/test/pkg/PasteError.java:95: Warning: The id R.id.textView1 has already been looked up in this method; possible cut & paste error? [CutPasteId]\n" +
            "                view2 = findViewById(R.id.textView1);\n" +
            "                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    src/test/pkg/PasteError.java:91: First usage here\n" +
            "0 errors, 5 warnings\n",

            lintProject(java("src/test/pkg/PasteError.java", ""
                    + "package test.pkg;\n"
                    + "\n"
                    + "import android.app.Activity;\n"
                    + "import android.view.View;\n"
                    + "\n"
                    + "public class PasteError extends Activity {\n"
                    + "    protected void ok() {\n"
                    + "        Button button1 = (Button) findViewById(R.id.textView1);\n"
                    + "        mView2 = findViewById(R.id.textView2);\n"
                    + "        View view3 = findViewById(R.id.activity_main);\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void error() {\n"
                    + "        View view1 = findViewById(R.id.textView1);\n"
                    + "        View view2 = findViewById(R.id.textView1);\n"
                    + "        View view3 = findViewById(R.id.textView2);\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void ok2() {\n"
                    + "        View view1;\n"
                    + "        if (true) {\n"
                    + "            view1 = findViewById(R.id.textView1);\n"
                    + "        } else {\n"
                    + "            view1 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    @SuppressLint(\"CutPasteId\")\n"
                    + "    protected void suppressed() {\n"
                    + "        View view1 = findViewById(R.id.textView1);\n"
                    + "        View view2 = findViewById(R.id.textView1);\n"
                    + "    }\n"
                    + "\n"
                    + "    private void ok3() {\n"
                    + "        if (view == null || view.findViewById(R.id.city_name) == null) {\n"
                    + "            view = mInflater.inflate(R.layout.city_list_item, parent, false);\n"
                    + "        }\n"
                    + "        TextView name = (TextView) view.findViewById(R.id.city_name);\n"
                    + "    }\n"
                    + "\n"
                    + "    private void ok4() {\n"
                    + "        mPrevAlbumWrapper = mPrevTrackLayout.findViewById(R.id.album_wrapper);\n"
                    + "        mNextAlbumWrapper = mNextTrackLayout.findViewById(R.id.album_wrapper);\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public View getView(int position, View convertView, ViewGroup parent) {\n"
                    + "        View listItem = convertView;\n"
                    + "        if (getItemViewType(position) == VIEW_TYPE_HEADER) {\n"
                    + "            TextView header = (TextView) listItem.findViewById(R.id.name);\n"
                    + "        } else if (getItemViewType(position) == VIEW_TYPE_BOOLEAN) {\n"
                    + "            TextView filterName = (TextView) listItem.findViewById(R.id.name);\n"
                    + "        } else {\n"
                    + "            TextView filterName = (TextView) listItem.findViewById(R.id.name);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void ok_branch_1() {\n"
                    + "        if (true) {\n"
                    + "            view1 = findViewById(R.id.textView1);\n"
                    + "        } else {\n"
                    + "            view2 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void error_branch_1() {\n"
                    + "        if (true) {\n"
                    + "            view1 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "        if (true) {\n"
                    + "            view2 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void error_branch_2() {\n"
                    + "        view1 = findViewById(R.id.textView1);\n"
                    + "        if (true) {\n"
                    + "            view2 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void error_branch_3() {\n"
                    + "        view1 = findViewById(R.id.textView1);\n"
                    + "        if (true) {\n"
                    + "        } else {\n"
                    + "            view2 = findViewById(R.id.textView1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void error_branch_4() {\n"
                    + "        view1 = findViewById(R.id.textView1);\n"
                    + "        if (true) {\n"
                    + "        } else {\n"
                    + "            if (true) {\n"
                    + "                view2 = findViewById(R.id.textView1);\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    protected void ok_branch_2() {\n"
                    + "        if (true) {\n"
                    + "            view1 = findViewById(R.id.textView1);\n"
                    + "        } else {\n"
                    + "            if (true) {\n"
                    + "                view2 = findViewById(R.id.textView1);\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n")));
    }
}
