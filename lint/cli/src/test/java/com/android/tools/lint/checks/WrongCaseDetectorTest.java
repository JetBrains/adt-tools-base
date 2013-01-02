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
public class WrongCaseDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WrongCaseDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "res/layout/case.xml:18: Warning: Invalid tag <Merge>; should be <merge> [WrongCase]\n"
                + "<Merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                + "^\n"
                + "res/layout/case.xml:20: Warning: Invalid tag <Fragment>; should be <fragment> [WrongCase]\n"
                + "    <Fragment android:name=\"foo.bar.Fragment\" />\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/case.xml:21: Warning: Invalid tag <Include>; should be <include> [WrongCase]\n"
                + "    <Include layout=\"@layout/foo\" />\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/case.xml:22: Warning: Invalid tag <RequestFocus>; should be <requestFocus> [WrongCase]\n"
                + "    <RequestFocus />\n"
                + "    ~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

                lintProject("res/layout/case.xml"));
    }
}
