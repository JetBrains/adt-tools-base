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

public class CallSuperDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CallSuperDetector();
    }

    public void testCallSuper() throws Exception {
        assertEquals(""
                + "src/test/pkg/CallSuperTest.java:11: Warning: Overriding method should call super.test1 [MissingSuperCall]\n"
                + "        protected void test1() { // ERROR\n"
                + "                       ~~~~~~~\n"
                + "src/test/pkg/CallSuperTest.java:14: Warning: Overriding method should call super.test2 [MissingSuperCall]\n"
                + "        protected void test2() { // ERROR\n"
                + "                       ~~~~~~~\n"
                + "src/test/pkg/CallSuperTest.java:17: Warning: Overriding method should call super.test3 [MissingSuperCall]\n"
                + "        protected void test3() { // ERROR\n"
                + "                       ~~~~~~~\n"
                + "src/test/pkg/CallSuperTest.java:20: Warning: Overriding method should call super.test4 [MissingSuperCall]\n"
                + "        protected void test4(int arg) { // ERROR\n"
                + "                       ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CallSuperTest.java:26: Warning: Overriding method should call super.test5 [MissingSuperCall]\n"
                + "        protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,  // ERROR\n"
                + "                       ^\n"
                + "src/test/pkg/CallSuperTest.java:30: Warning: Overriding method should call super.test5 [MissingSuperCall]\n"
                + "        protected void test5() { // ERROR\n"
                + "                       ~~~~~~~\n"
                + "0 errors, 6 warnings\n",

                lintProject("src/test/pkg/CallSuperTest.java.txt=>src/test/pkg/CallSuperTest.java",
                        "src/android/support/annotation/CallSuper.java.txt=>src/android/support/annotation/CallSuper.java"));
    }

    public void testDetachFromWindow() throws Exception {
        assertEquals(""
                + "src/test/pkg/DetachedFromWindow.java:7: Warning: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]\n"
                + "        protected void onDetachedFromWindow() {\n"
                + "                       ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/DetachedFromWindow.java:26: Warning: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]\n"
                + "        protected void onDetachedFromWindow() {\n"
                + "                       ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                lintProject("src/test/pkg/DetachedFromWindow.java.txt=>" +
                        "src/test/pkg/DetachedFromWindow.java"));
    }

    public void testWatchFaceVisibility() throws Exception {
        assertEquals(""
                + "src/test/pkg/WatchFaceTest.java:9: Warning: Overriding method should call super.onVisibilityChanged [MissingSuperCall]\n"
                + "        public void onVisibilityChanged(boolean visible) { // ERROR: Missing super call\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        "src/test/pkg/WatchFaceTest.java.txt=>src/test/pkg/WatchFaceTest.java",
                        "stubs/WatchFaceService.java.txt=>src/android/support/wearable/watchface/WatchFaceService.java",
                        "stubs/CanvasWatchFaceService.java.txt=>src/android/support/wearable/watchface/CanvasWatchFaceService.java"
                ));
    }
}
