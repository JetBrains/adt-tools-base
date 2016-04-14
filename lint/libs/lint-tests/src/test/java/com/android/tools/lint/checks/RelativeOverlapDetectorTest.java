/*
 * Copyright (C) 2014 The Android Open Source Project
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
public class RelativeOverlapDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new RelativeOverlapDetector();
    }

    public void testOneOverlap() throws Exception {
        assertEquals(
            "res/layout/relative_overlap.xml:17: Warning: @id/label2 can overlap @id/label1 if @string/label1_text, @string/label2_text grow due to localized text expansion [RelativeOverlap]\n" +
            "        <TextView\n" +
            "        ^\n" +
            "0 errors, 1 warnings\n",
            lintFiles("res/layout/relative_overlap.xml"));
    }

    public void testOneOverlapPercent() throws Exception {
        assertEquals(""
                + "res/layout/relative_percent_overlap.xml:17: Warning: @id/label2 can overlap @id/label1 if @string/label1_text, @string/label2_text grow due to localized text expansion [RelativeOverlap]\n"
                + "        <TextView\n"
                + "        ^\n"
                + "0 errors, 1 warnings\n",
                lintProject(xml("res/layout/relative_percent_overlap.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:id=\"@+id/container\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"wrap_content\"\n"
                        + "    android:orientation=\"vertical\">\n"
                        + "    <android.support.percent.PercentRelativeLayout\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"wrap_content\">\n"
                        + "        <TextView\n"
                        + "            android:id=\"@+id/label1\"\n"
                        + "            android:layout_alignParentLeft=\"true\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/label1_text\"\n"
                        + "            android:ellipsize=\"end\" />\n"
                        + "        <TextView\n"
                        + "            android:id=\"@+id/label2\"\n"
                        + "            android:layout_alignParentRight=\"true\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/label2_text\"\n"
                        + "            android:ellipsize=\"end\" />\n"
                        + "        <TextView\n"
                        + "            android:id=\"@+id/circular1\"\n"
                        + "            android:layout_alignParentBottom=\"true\"\n"
                        + "            android:layout_toRightOf=\"@+id/circular2\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/label1_text\"\n"
                        + "            android:ellipsize=\"end\" />\n"
                        + "        <TextView\n"
                        + "            android:id=\"@id/circular2\"\n"
                        + "            android:layout_alignParentBottom=\"true\"\n"
                        + "            android:layout_toRightOf=\"@id/circular1\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/label2_text\"\n"
                        + "            android:ellipsize=\"end\" />\n"
                        + "        <TextView\n"
                        + "            android:id=\"@id/circular3\"\n"
                        + "            android:layout_alignParentBottom=\"true\"\n"
                        + "            android:layout_toRightOf=\"@id/circular1\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"@string/label2_text\"\n"
                        + "            android:ellipsize=\"end\" />\n"
                        + "    </android.support.percent.PercentRelativeLayout>\n"
                        + "</LinearLayout>\n")));
    }
}
