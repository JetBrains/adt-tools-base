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
public class HandlerDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new HandlerDetector();
    }

    public void testRegistered() throws Exception {
        assertEquals(
            "src/test/pkg/HandlerTest.java:12: Warning: This Handler class should be static or leaks might occur (test.pkg.HandlerTest.Inner) [HandlerLeak]\n" +
            "    public class Inner extends Handler { // ERROR\n" +
            "                 ~~~~~\n" +
            "src/test/pkg/HandlerTest.java:18: Warning: This Handler class should be static or leaks might occur (test.pkg.HandlerTest.1) [HandlerLeak]\n" +
            "        Handler anonymous = new Handler() { // ERROR\n" +
            "                                ~~~~~~~\n" +
            "0 errors, 2 warnings\n" +
            "",

            lintProject(
                "bytecode/HandlerTest.java.txt=>src/test/pkg/HandlerTest.java",
                "bytecode/HandlerTest.class.data=>bin/classes/test/pkg/HandlerTest.class",
                "bytecode/HandlerTest$Inner.class.data=>bin/classes/test/pkg/HandlerTest$Inner.class",
                "bytecode/HandlerTest$StaticInner.class.data=>bin/classes/test/pkg/HandlerTest$StaticInner.class",
                "bytecode/HandlerTest$1.class.data=>bin/classes/test/pkg/HandlerTest$1.class"));
    }
}
