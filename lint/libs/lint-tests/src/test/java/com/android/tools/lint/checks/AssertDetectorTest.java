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

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/Assert.java:7: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert false;                              // ERROR\n"
                + "        ~~~~~~~~~~~~\n"
                + "src/test/pkg/Assert.java:8: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert param > 5 : \"My description\";       // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/Assert.java:9: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert param2 == param3;                   // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/Assert.java:10: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                + "        assert param2 != null && param3 == param2; // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

            lintProject(java("src/test/pkg/Assert.java", ""
                    + "package test.pkg;\n"
                    + "\n"
                    + "import android.annotation.SuppressLint;\n"
                    + "\n"
                    + "public class Assert {\n"
                    + "    public Assert(int param, Object param2, Object param3) {\n"
                    + "        assert false;                              // ERROR\n"
                    + "        assert param > 5 : \"My description\";       // ERROR\n"
                    + "        assert param2 == param3;                   // ERROR\n"
                    + "        assert param2 != null && param3 == param2; // ERROR\n"
                    + "        assert true;                               // OK\n"
                    + "        assert param2 == null;                     // OK\n"
                    + "        assert param2 != null && param3 == null;   // OK\n"
                    + "        assert param2 == null && param3 != null;   // OK\n"
                    + "        assert param2 != null && param3 != null;   // OK\n"
                    + "        assert null != param2;                     // OK\n"
                    + "        assert param2 != null;                     // OK\n"
                    + "        assert param2 != null : \"My description\";  // OK\n"
                    + "        assert checkSuppressed(5) != null;         // OK\n"
                    + "    }\n"
                    + "\n"
                    + "    @SuppressLint(\"Assert\")\n"
                    + "    public static Object checkSuppressed(int param) {\n"
                    + "        assert param > 5 : \"My description\";\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "}\n")));
    }
}
