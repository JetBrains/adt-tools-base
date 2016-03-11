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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
public class FragmentDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new FragmentDetector();
    }

    public void test() throws Exception {
        assertEquals(""
            + "src/test/pkg/FragmentTest.java:10: Error: This fragment class should be public (test.pkg.FragmentTest.Fragment1) [ValidFragment]\n"
            + "    private static class Fragment1 extends Fragment {\n"
            + "                         ~~~~~~~~~\n"
            + "src/test/pkg/FragmentTest.java:15: Error: This fragment inner class should be static (test.pkg.FragmentTest.Fragment2) [ValidFragment]\n"
            + "    public class Fragment2 extends Fragment {\n"
            + "                 ~~~~~~~~~\n"
            + "src/test/pkg/FragmentTest.java:21: Error: The default constructor must be public [ValidFragment]\n"
            + "        private Fragment3() {\n"
            + "                ~~~~~~~~~\n"
            + "src/test/pkg/FragmentTest.java:26: Error: This fragment should provide a default constructor (a public constructor with no arguments) (test.pkg.FragmentTest.Fragment4) [ValidFragment]\n"
            + "    public static class Fragment4 extends Fragment {\n"
            + "                        ~~~~~~~~~\n"
            + "src/test/pkg/FragmentTest.java:27: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]\n"
            + "        private Fragment4(int dummy) {\n"
            + "                ~~~~~~~~~\n"
            + "src/test/pkg/FragmentTest.java:36: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]\n"
            + "        public Fragment5(int dummy) {\n"
            + "               ~~~~~~~~~\n"
            + "6 errors, 0 warnings\n",

            lintProject(java("src/test/pkg/FragmentTest.java", ""
                    + "package test.pkg;\n"
                    + "\n"
                    + "import android.annotation.SuppressLint;\n"
                    + "import android.app.Fragment;\n"
                    + "\n"
                    + "@SuppressWarnings(\"unused\")\n"
                    + "public class FragmentTest {\n"
                    + "\n"
                    + "    // Should be public\n"
                    + "    private static class Fragment1 extends Fragment {\n"
                    + "\n"
                    + "    }\n"
                    + "\n"
                    + "    // Should be static\n"
                    + "    public class Fragment2 extends Fragment {\n"
                    + "\n"
                    + "    }\n"
                    + "\n"
                    + "    // Should have a public constructor\n"
                    + "    public static class Fragment3 extends Fragment {\n"
                    + "        private Fragment3() {\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    // Should have a public constructor with no arguments\n"
                    + "    public static class Fragment4 extends Fragment {\n"
                    + "        private Fragment4(int dummy) {\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    // Should *only* have the default constructor, not the\n"
                    + "    // multi-argument one\n"
                    + "    public static class Fragment5 extends Fragment {\n"
                    + "        public Fragment5() {\n"
                    + "        }\n"
                    + "        public Fragment5(int dummy) {\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    // Suppressed\n"
                    + "    @SuppressLint(\"ValidFragment\")\n"
                    + "    public static class Fragment6 extends Fragment {\n"
                    + "        private Fragment6() {\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    public static class ValidFragment1 extends Fragment {\n"
                    + "        public ValidFragment1() {\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    // (Not a fragment)\n"
                    + "    private class NotAFragment {\n"
                    + "    }\n"
                    + "\n"
                    + "    // Ok: Has implicit constructor\n"
                    + "    public static class Fragment7 extends Fragment {\n"
                    + "    }\n"
                    + "}\n"))

        );
    }

    public void testAnonymousInnerClass() throws Exception {
        assertEquals(""
                + "src/test/pkg/Parent.java:7: Error: Fragments should be static such that they can be re-instantiated by the system, and anonymous classes are not static [ValidFragment]\n"
                + "        return new Fragment() {\n"
                + "                   ~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(java("src/test/pkg/Parent.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.app.Fragment;\n"
                        + "\n"
                        + "public class Parent {\n"
                        + "    public Fragment method() {\n"
                        + "        return new Fragment() {\n"
                        + "        };\n"
                        + "    }\n"
                        + "}\n")));
    }
}
