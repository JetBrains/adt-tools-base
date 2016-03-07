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

@SuppressWarnings("MethodMayBeStatic")
public class CustomViewDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CustomViewDetector();
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        assertEquals(""
            + "src/test/pkg/CustomView1.java:18: Warning: By convention, the custom view (CustomView1) and the declare-styleable (MyDeclareStyleable) should have the same name (various editor features rely on this convention) [CustomViewStyleable]\n"
            + "        context.obtainStyledAttributes(R.styleable.MyDeclareStyleable);\n"
            + "                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/CustomView1.java:19: Warning: By convention, the custom view (CustomView1) and the declare-styleable (MyDeclareStyleable) should have the same name (various editor features rely on this convention) [CustomViewStyleable]\n"
            + "        context.obtainStyledAttributes(defStyleRes, R.styleable.MyDeclareStyleable);\n"
            + "                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/CustomView1.java:20: Warning: By convention, the custom view (CustomView1) and the declare-styleable (MyDeclareStyleable) should have the same name (various editor features rely on this convention) [CustomViewStyleable]\n"
            + "        context.obtainStyledAttributes(attrs, R.styleable.MyDeclareStyleable);\n"
            + "                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/CustomView1.java:21: Warning: By convention, the custom view (CustomView1) and the declare-styleable (MyDeclareStyleable) should have the same name (various editor features rely on this convention) [CustomViewStyleable]\n"
            + "        context.obtainStyledAttributes(attrs, R.styleable.MyDeclareStyleable, defStyleAttr,\n"
            + "                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/CustomView1.java:46: Warning: By convention, the declare-styleable (MyLayout) for a layout parameter class (MyLayoutParams) is expected to be the surrounding class (MyLayout) plus \"_Layout\", e.g. MyLayout_Layout. (Various editor features rely on this convention.) [CustomViewStyleable]\n"
            + "                context.obtainStyledAttributes(R.styleable.MyLayout); // Wrong\n"
            + "                                               ~~~~~~~~~~~~~~~~~~~~\n"
            + "src/test/pkg/CustomView1.java:47: Warning: By convention, the declare-styleable (MyDeclareStyleable) for a layout parameter class (MyLayoutParams) is expected to be the surrounding class (MyLayout) plus \"_Layout\", e.g. MyLayout_Layout. (Various editor features rely on this convention.) [CustomViewStyleable]\n"
            + "                context.obtainStyledAttributes(R.styleable.MyDeclareStyleable); // Wrong\n"
            + "                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "0 errors, 6 warnings\n",

            lintProject(java("src/test/pkg/CustomView1.java", ""
                    + "package test.pkg;\n"
                    + "\n"
                    + "import android.content.Context;\n"
                    + "import android.util.AttributeSet;\n"
                    + "import android.widget.Button;\n"
                    + "import android.widget.LinearLayout;\n"
                    + "\n"
                    + "public class CustomView1 extends Button {\n"
                    + "    public CustomView1(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                    + "        super(context, attrs, defStyleAttr);\n"
                    + "        // OK\n"
                    + "        context.obtainStyledAttributes(R.styleable.CustomView1);\n"
                    + "        context.obtainStyledAttributes(defStyleRes, R.styleable.CustomView1);\n"
                    + "        context.obtainStyledAttributes(attrs, R.styleable.CustomView1);\n"
                    + "        context.obtainStyledAttributes(attrs, R.styleable.CustomView1, defStyleAttr, defStyleRes);\n"
                    + "\n"
                    + "        // Wrong:\n"
                    + "        context.obtainStyledAttributes(R.styleable.MyDeclareStyleable);\n"
                    + "        context.obtainStyledAttributes(defStyleRes, R.styleable.MyDeclareStyleable);\n"
                    + "        context.obtainStyledAttributes(attrs, R.styleable.MyDeclareStyleable);\n"
                    + "        context.obtainStyledAttributes(attrs, R.styleable.MyDeclareStyleable, defStyleAttr,\n"
                    + "                defStyleRes);\n"
                    + "\n"
                    + "        // Unknown: Not flagged\n"
                    + "        int[] dynamic = getStyleable();\n"
                    + "        context.obtainStyledAttributes(dynamic);\n"
                    + "        context.obtainStyledAttributes(defStyleRes, dynamic);\n"
                    + "        context.obtainStyledAttributes(attrs, dynamic);\n"
                    + "        context.obtainStyledAttributes(attrs, dynamic, defStyleAttr, defStyleRes);\n"
                    + "    }\n"
                    + "\n"
                    + "    private int[] getStyleable() {\n"
                    + "        return new int[0];\n"
                    + "    }\n"
                    + "\n"
                    + "    public static class MyLayout extends LinearLayout {\n"
                    + "        public MyLayout(Context context, AttributeSet attrs, int defStyle) {\n"
                    + "            super(context, attrs, defStyle);\n"
                    + "            context.obtainStyledAttributes(R.styleable.MyLayout);\n"
                    + "        }\n"
                    + "\n"
                    + "        public static class MyLayoutParams extends LinearLayout.LayoutParams {\n"
                    + "            public MyLayoutParams(Context context, AttributeSet attrs) {\n"
                    + "                super(context, attrs);\n"
                    + "                context.obtainStyledAttributes(R.styleable.MyLayout_Layout); // OK\n"
                    + "                context.obtainStyledAttributes(R.styleable.MyLayout); // Wrong\n"
                    + "                context.obtainStyledAttributes(R.styleable.MyDeclareStyleable); // Wrong\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    public static final class R {\n"
                    + "        public static final class attr {\n"
                    + "            public static final int layout_myWeight=0x7f010001;\n"
                    + "            public static final int myParam=0x7f010000;\n"
                    + "        }\n"
                    + "        public static final class dimen {\n"
                    + "            public static final int activity_horizontal_margin=0x7f040000;\n"
                    + "            public static final int activity_vertical_margin=0x7f040001;\n"
                    + "        }\n"
                    + "        public static final class drawable {\n"
                    + "            public static final int ic_launcher=0x7f020000;\n"
                    + "        }\n"
                    + "        public static final class id {\n"
                    + "            public static final int action_settings=0x7f080000;\n"
                    + "        }\n"
                    + "        public static final class layout {\n"
                    + "            public static final int activity_my=0x7f030000;\n"
                    + "        }\n"
                    + "        public static final class menu {\n"
                    + "            public static final int my=0x7f070000;\n"
                    + "        }\n"
                    + "        public static final class string {\n"
                    + "            public static final int action_settings=0x7f050000;\n"
                    + "            public static final int app_name=0x7f050001;\n"
                    + "            public static final int hello_world=0x7f050002;\n"
                    + "        }\n"
                    + "        public static final class style {\n"
                    + "            public static final int AppTheme=0x7f060000;\n"
                    + "        }\n"
                    + "        public static final class styleable {\n"
                    + "            public static final int[] CustomView1 = {\n"
                    + "\n"
                    + "            };\n"
                    + "            public static final int[] MyDeclareStyleable = {\n"
                    + "\n"
                    + "            };\n"
                    + "            public static final int[] MyLayout = {\n"
                    + "                    0x010100c4, 0x7f010000\n"
                    + "            };\n"
                    + "            public static final int MyLayout_android_orientation = 0;\n"
                    + "            public static final int MyLayout_myParam = 1;\n"
                    + "            public static final int[] MyLayout_Layout = {\n"
                    + "                    0x010100f4, 0x010100f5, 0x7f010001\n"
                    + "            };\n"
                    + "            public static final int MyLayout_Layout_android_layout_height = 1;\n"
                    + "            public static final int MyLayout_Layout_android_layout_width = 0;\n"
                    + "            public static final int MyLayout_Layout_layout_myWeight = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n")));
    }
}
