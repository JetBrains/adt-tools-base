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
public class RecycleDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CleanupDetector();
    }

    public void testRecycle() throws Exception {
        assertEquals(
            "src/test/pkg/RecycleTest.java:56: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]\n" +
            "  final TypedArray a = getContext().obtainStyledAttributes(attrs,\n" +
            "                                    ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:63: Warning: This TypedArray should be recycled after use with #recycle() [Recycle]\n" +
            "  final TypedArray a = getContext().obtainStyledAttributes(new int[0]);\n" +
            "                                    ~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:79: Warning: This VelocityTracker should be recycled after use with #recycle() [Recycle]\n" +
            "  VelocityTracker tracker = VelocityTracker.obtain();\n" +
            "                                            ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:85: Warning: This Message should be recycled after use with #recycle() [Recycle]\n" +
            "  Message message1 = getHandler().obtainMessage();\n" +
            "                                  ~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:86: Warning: This Message should be recycled after use with #recycle() [Recycle]\n" +
            "  Message message2 = Message.obtain();\n" +
            "                             ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:92: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event1 = MotionEvent.obtain(null);\n" +
            "                                   ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:93: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event2 = MotionEvent.obtainNoHistory(null);\n" +
            "                                   ~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:98: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event2 = MotionEvent.obtainNoHistory(null); // Not recycled\n" +
            "                                   ~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:103: Warning: This MotionEvent should be recycled after use with #recycle() [Recycle]\n" +
            "  MotionEvent event1 = MotionEvent.obtain(null);  // Not recycled\n" +
            "                                   ~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:113: Warning: This MotionEvent has already been recycled [Recycle]\n" +
            "  int contents2 = event1.describeContents(); // BAD, after recycle\n" +
            "                         ~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:117: Warning: This TypedArray has already been recycled [Recycle]\n" +
            "  example = a.getString(R.styleable.MyView_exampleString); // BAD, after recycle\n" +
            "              ~~~~~~~~~\n" +
            "src/test/pkg/RecycleTest.java:129: Warning: This Parcel should be recycled after use with #recycle() [Recycle]\n" +
            "  Parcel myparcel = Parcel.obtain();\n" +
            "                           ~~~~~~\n" +
            "0 errors, 12 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk4.xml=>AndroidManifest.xml",
                "project.properties1=>project.properties",
                "bytecode/RecycleTest.java.txt=>src/test/pkg/RecycleTest.java",
                "bytecode/RecycleTest.class.data=>bin/classes/test/pkg/RecycleTest.class"
            ));
    }

    public void testCommit() throws Exception {
        assertEquals(""
                + "src/test/pkg/CommitTest.java:24: Warning: This FragmentTransaction should be recycled after use with #recycle() [CommitTransaction]\n"
                + "        getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                             ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:29: Warning: This FragmentTransaction should be recycled after use with #recycle() [CommitTransaction]\n"
                + "        FragmentTransaction transaction2 = getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                                                                ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:38: Warning: This FragmentTransaction should be recycled after use with #recycle() [CommitTransaction]\n"
                + "        getFragmentManager().beginTransaction(); // Missing commit\n"
                + "                             ~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CommitTest.java:64: Warning: This FragmentTransaction should be recycled after use with #recycle() [Recycle]\n"
                + "        getSupportFragmentManager().beginTransaction();\n"
                + "                                    ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

            lintProject(
                    "apicheck/classpath=>.classpath",
                    "apicheck/minsdk4.xml=>AndroidManifest.xml",
                    "project.properties1=>project.properties",
                    "bytecode/CommitTest.java.txt=>src/test/pkg/CommitTest.java",
                    "bytecode/CommitTest.class.data=>bin/classes/test/pkg/CommitTest.class"
            ));
    }

    public void testCommit2() throws Exception {
        assertEquals(""
                + "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "bytecode/DialogFragment.class.data=>bin/classes/test/pkg/DialogFragment.class"
                ));
    }
}
