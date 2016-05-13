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

public class ConstraintLayoutDetectorTest extends AbstractCheckTest {
    public void test1() throws Exception {
        assertEquals(""
                + "res/layout/layout1.xml:19: Error: This view is not constrained, it only has designtime positions, so it will jump to (0,0) unless you add constraints [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "res/layout/layout1.xml:43: Error: This view is not constrained vertically: at runtime it will jump to the left unless you add a vertical constraint [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "res/layout/layout1.xml:53: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]\n"
                + "    <TextView\n"
                + "    ^\n"
                + "3 errors, 0 warnings\n",
                lintProject(xml("res/layout/layout1.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    android:id=\"@+id/activity_main\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    app:layout_editor_absoluteX=\"0dp\"\n"
                        + "    app:layout_editor_absoluteY=\"81dp\"\n"
                        + "    tools:context=\"com.example.tnorbye.myapplication.MainActivity\"\n"
                        + "    tools:ignore=\"HardcodedText\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Not constrained and no designtime positions\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView2\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Not constrained\"\n"
                        + "        app:layout_editor_absoluteX=\"21dp\"\n"
                        + "        app:layout_editor_absoluteY=\"23dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView3\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained both\"\n"
                        + "        app:layout_constraintBottom_creator=\"2\"\n"
                        + "        app:layout_constraintBottom_toBottomOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintLeft_creator=\"2\"\n"
                        + "        app:layout_constraintLeft_toLeftOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintRight_creator=\"2\"\n"
                        + "        app:layout_constraintRight_toRightOf=\"@+id/activity_main\"\n"
                        + "        app:layout_constraintTop_creator=\"2\"\n"
                        + "        app:layout_constraintTop_toTopOf=\"@+id/activity_main\"\n"
                        + "        app:layout_editor_absoluteX=\"139dp\"\n"
                        + "        app:layout_editor_absoluteY=\"247dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView4\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained Horizontally\"\n"
                        + "        app:layout_constraintLeft_creator=\"0\"\n"
                        + "        app:layout_constraintLeft_toLeftOf=\"@+id/textView3\"\n"
                        + "        app:layout_editor_absoluteX=\"139dp\"\n"
                        + "        app:layout_editor_absoluteY=\"270dp\" />\n"
                        + "\n"
                        + "    <TextView\n"
                        + "        android:id=\"@+id/textView5\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Constrained Vertically\"\n"
                        + "        app:layout_constraintBaseline_creator=\"2\"\n"
                        + "        app:layout_constraintBaseline_toBaselineOf=\"@+id/textView4\"\n"
                        + "        app:layout_editor_absoluteX=\"306dp\"\n"
                        + "        app:layout_editor_absoluteY=\"270dp\" />\n"
                        + "</android.support.constraint.ConstraintLayout>\n")));
    }

    @Override
    protected Detector getDetector() {
        return new ConstraintLayoutDetector();
    }
}