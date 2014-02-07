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
public class AssertDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AssertDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/Assert.java:7: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert false;\n"
                + "        ~~~~~~~~~~~~\n"
                + "src/test/pkg/Assert.java:8: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert param > 5 : \"My description\";\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

            lintProject("src/test/pkg/Assert.java.txt=>src/test/pkg/Assert.java"));
    }
}
