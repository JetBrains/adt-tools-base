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

public class ClickableViewAccessibilityDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new ClickableViewAccessibilityDetector();
    }

    public void testWarningWhenViewOverridesOnTouchEventButNotPerformClick() throws Exception {
        assertEquals(
            "src/test/pkg/ClickableViewAccessibilityTest.java:16: Warning: Custom view test/pkg/ClickableViewAccessibilityTest$ViewOverridesOnTouchEventButNotPerformClick overrides onTouchEvent but not performClick [ClickableViewAccessibility]\n"
                + "        public boolean onTouchEvent(MotionEvent event) {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$ViewOverridesOnTouchEventButNotPerformClick.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewOverridesOnTouchEventButNotPerformClick.class"
            ));
    }

    public void testWarningWhenOnTouchEventDoesNotCallPerformClick() throws Exception {
        assertEquals(
            "src/test/pkg/ClickableViewAccessibilityTest.java:28: Warning: test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick#onTouchEvent should call test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick#performClick [ClickableViewAccessibility]\n"
                    + "        public boolean onTouchEvent(MotionEvent event) {\n"
                    + "                       ~~~~~~~~~~~~\n"
                    + "0 errors, 1 warnings\n",
            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick.class"
            ));
    }

    public void testWarningWhenPerformClickDoesNotCallSuper() throws Exception {
        assertEquals(
            "src/test/pkg/ClickableViewAccessibilityTest.java:44: Warning: test/pkg/ClickableViewAccessibilityTest$PerformClickDoesNotCallSuper#performClick should call super#performClick [ClickableViewAccessibility]\n"
                + "        public boolean performClick() {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$PerformClickDoesNotCallSuper.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$PerformClickDoesNotCallSuper.class"
            ));
    }

    public void testNoWarningOnValidView() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$ValidView.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ValidView.class"
            ));
    }

    public void testNoWarningOnNonViewSubclass() throws Exception {
        assertEquals(
            "No warnings.",
        lintProject(
            "bytecode/.classpath=>.classpath",
            "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
            "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
            "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
            "bytecode/ClickableViewAccessibilityTest$NotAView.class.data=>"
                + "bin/classes/test/pkg/ClickableViewAccessibilityTest$NotAView.class"
        ));
    }

    public void testWarningOnViewSubclass() throws Exception {
        // ViewSubclass is actually a subclass of ValidView. This tests that we can detect
        // tests further down in the inheritance hierarchy than direct children of View.
        assertEquals(
                "src/test/pkg/ClickableViewAccessibilityTest.java:82: Warning: test/pkg/ClickableViewAccessibilityTest$ViewSubclass#performClick should call super#performClick [ClickableViewAccessibility]\n"
                    + "        public boolean performClick() {\n"
                    + "                       ~~~~~~~~~~~~\n"
                    + "0 errors, 1 warnings\n",
                lintProject(
                    "bytecode/.classpath=>.classpath",
                    "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                    "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                    "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                    "bytecode/ClickableViewAccessibilityTest$ValidView.class.data=>"
                        + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ValidView.class",
                    "bytecode/ClickableViewAccessibilityTest$ViewSubclass.class.data=>"
                        + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewSubclass.class"
                ));
    }

    public void testNoWarningOnOnTouchEventWithDifferentSignature() throws Exception {
        assertEquals(
            "No warnings.",
        lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$ViewWithDifferentOnTouchEvent.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewWithDifferentOnTouchEvent.class"
            ));
    }

    public void testNoWarningOnPerformClickWithDifferentSignature() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject(
                "bytecode/.classpath=>.classpath",
                "bytecode/AndroidManifest.xml=>AndroidManifest.xml",
                "bytecode/ClickableViewAccessibilityTest.java.txt=>src/test/pkg/ClickableViewAccessibilityTest.java",
                "bytecode/ClickableViewAccessibilityTest.class.data=>bin/classes/test/pkg/ClickableViewAccessibilityTest.class",
                "bytecode/ClickableViewAccessibilityTest$ViewWithDifferentPerformClick.class.data=>"
                    + "bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewWithDifferentPerformClick.class"
            ));
    }
}
