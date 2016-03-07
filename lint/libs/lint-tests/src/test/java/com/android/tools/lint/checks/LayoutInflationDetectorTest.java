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

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;

public class LayoutInflationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LayoutInflationDetector();
    }

    @SuppressWarnings("all")
    private TestFile mLayoutInflationTest = java("src/test/pkg/LayoutInflationTest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.content.Context;\n"
            + "import android.view.LayoutInflater;\n"
            + "import android.view.View;\n"
            + "import android.view.ViewGroup;\n"
            + "import android.widget.BaseAdapter;\n"
            + "import android.annotation.SuppressLint;\n"
            + "import java.util.ArrayList;\n"
            + "\n"
            + "public abstract class LayoutInflationTest extends BaseAdapter {\n"
            + "    public View getView(int position, View convertView, ViewGroup parent) {\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
            + "        //convertView = mInflater.inflate(R.layout.your_layout);\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, parent);\n"
            + "        convertView = WeirdInflater.inflate(convertView, null);\n"
            + "\n"
            + "        return convertView;\n"
            + "    }\n"
            + "\n"
            // Suppressed checks
            + "    @SuppressLint(\"InflateParams\")\n"
            + "    public View getView2(int position, View convertView, ViewGroup parent) {\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
            + "        convertView = mInflater.inflate(R.layout.your_layout, parent);\n"
            + "        convertView = WeirdInflater.inflate(convertView, null);\n"
            + "\n"
            + "        return convertView;\n"
            + "    }\n"
            // Test/Stub Setup
            + "    private LayoutInflater mInflater;\n"
            + "    private static class R {\n"
            + "        private static class layout {\n"
            + "            public static final int your_layout = 1;\n"
            + "        }\n"
            + "    }\n"
            + "    private static class WeirdInflater {\n"
            + "        public static View inflate(View view, Object params) { return null; }\n"
            + "    }\n"
            + "}\n");

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return false;
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/LayoutInflationTest.java:13: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                + "        convertView = mInflater.inflate(R.layout.your_layout, null);\n"
                + "                                                              ~~~~\n"
                + "src/test/pkg/LayoutInflationTest.java:14: Warning: Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element) [InflateParams]\n"
                + "        convertView = mInflater.inflate(R.layout.your_layout, null, true);\n"
                + "                                                              ~~~~\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                    mLayoutInflationTest,
                    copy("res/layout/textsize.xml", "res/layout/your_layout.xml"),
                    copy("res/layout/listseparator.xml", "res/layout-port/your_layout.xml")));
    }

    public void testNoLayoutParams() throws Exception {
        assertEquals("No warnings.",

                lintProject(
                        mLayoutInflationTest,
                        copy("res/layout/listseparator.xml", "res/layout/your_layout.xml")));
    }

    public void testHasLayoutParams() throws IOException, XmlPullParserException {
        assertFalse(LayoutInflationDetector.hasLayoutParams(new StringReader("")));
        assertFalse(LayoutInflationDetector.hasLayoutParams(new StringReader("<LinearLayout/>")));
        assertFalse(LayoutInflationDetector.hasLayoutParams(new StringReader(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:orientation=\"vertical\" >\n"
                + "\n"
                + "    <include\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        layout=\"@layout/layoutcycle1\" />\n"
                + "\n"
                + "</LinearLayout>")));


        assertTrue(LayoutInflationDetector.hasLayoutParams(new StringReader(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\" >\n"
                + "\n"
                + "    <include\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        layout=\"@layout/layoutcycle1\" />\n"
                + "\n"
                + "</LinearLayout>")));
    }
}

