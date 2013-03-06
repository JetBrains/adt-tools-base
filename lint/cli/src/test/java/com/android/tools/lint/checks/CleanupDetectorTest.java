/*
 * Copyright (C) 2013 The Android Open Source Project
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
public class CleanupDetectorTest extends AbstractCheckTest {
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
            "0 errors, 10 warnings\n",

            lintProject(
                "apicheck/classpath=>.classpath",
                "apicheck/minsdk4.xml=>AndroidManifest.xml",
                "project.properties1=>project.properties",
                "bytecode/RecycleTest.java.txt=>src/test/pkg/RecycleTest.java",
                "bytecode/RecycleTest.class.data=>bin/classes/test/pkg/RecycleTest.class"
            ));
    }

    public void testCommit() throws Exception {
        assertEquals("" +
            "src/test/pkg/CommitTest.java:25: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n" +
            "        getFragmentManager().beginTransaction(); // Missing commit\n" +
            "                             ~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CommitTest.java:30: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n" +
            "        FragmentTransaction transaction2 = getFragmentManager().beginTransaction(); // Missing commit\n" +
            "                                                                ~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CommitTest.java:39: Warning: This transaction should be completed with a commit() call [CommitTransaction]\n" +
            "        getFragmentManager().beginTransaction(); // Missing commit\n" +
            "                             ~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/CommitTest.java:65: Warning: This transaction should be completed with a commit() call [Recycle]\n" +
            "        getSupportFragmentManager().beginTransaction();\n" +
            "                                    ~~~~~~~~~~~~~~~~\n" +
            "0 errors, 4 warnings\n",

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

    public void testHasReturnType() throws Exception {
        assertTrue(CleanupDetector.hasReturnType("android/app/FragmentTransaction",
                "(Landroid/app/Fragment;)Landroid/app/FragmentTransaction;"));
        assertTrue(CleanupDetector.hasReturnType("android/app/FragmentTransaction",
                "()Landroid/app/FragmentTransaction;"));
        assertFalse(CleanupDetector.hasReturnType("android/app/FragmentTransaction",
                "()Landroid/app/FragmentTransactions;"));
        assertFalse(CleanupDetector.hasReturnType("android/app/FragmentTransaction",
                "()Landroid/app/FragmentTransactions"));
        assertFalse(CleanupDetector.hasReturnType("android/app/FragmentTransaction",
                "()android/app/FragmentTransaction;"));
    }

    public void testCommit3() throws Exception {
        assertEquals("" +
                "No warnings.",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "bytecode/CommitTest2.java.txt=>src/test/pkg/CommitTest2.java",
                        "bytecode/CommitTest2$MyDialogFragment.class.data=>bin/classes/test/pkg/CommitTest2$MyDialogFragment.class",
                        "bytecode/CommitTest2.class.data=>bin/classes/test/pkg/CommitTest2.class"
                ));
    }

    public void testCommit4() throws Exception {
        assertEquals("" +
                "src/test/pkg/CommitTest3.java:35: Warning: This transaction should be completed with a commit() call [Recycle]\n"
                + "    getCompatFragmentManager().beginTransaction();\n"
                + "                               ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        "apicheck/classpath=>.classpath",
                        "apicheck/minsdk4.xml=>AndroidManifest.xml",
                        "project.properties1=>project.properties",
                        "bytecode/CommitTest3.java.txt=>src/test/pkg/CommitTest3.java",
                        "bytecode/CommitTest3.class.data=>bin/classes/test/pkg/CommitTest3.class",
                        "bytecode/CommitTest3$MyDialogFragment.class.data=>bin/classes/test/pkg/CommitTest3$MyDialogFragment.class",
                        "bytecode/CommitTest3$MyCompatDialogFragment.class.data=>bin/classes/test/pkg/CommitTest3$MyCompatDialogFragment.class"
                ));
    }
}
