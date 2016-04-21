/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class MathDetectorTest extends AbstractCheckTest {

    private TestFile mTestFile = java("src/test/bytecode/MathTest.java", ""
            + "package test.bytecode;\n"
            + "\n"
            + "import android.util.FloatMath;\n"
            + "import static android.util.FloatMath.sin;\n"
            + "\n"
            + "//Test data for the MathDetector\n"
            + "public class MathTest {\n"
            + "    public float floatResult;\n"
            + "    public double doubleResult;\n"
            + "\n"
            + "    public void floatToFloatTest(float x, double y, int z) {\n"
            + "        floatResult = FloatMath.cos(x);\n"
            + "        floatResult = FloatMath.sin((float) y);\n"
            + "        floatResult = android.util.FloatMath.ceil((float) y);\n"
            + "        System.out.println(FloatMath.floor(x));\n"
            + "        System.out.println(FloatMath.sqrt(z));\n"
            + "        floatResult = sin((float) y);\n"
            + "\n"
            + "        // No warnings for plain math\n"
            + "        floatResult = (float) Math.cos(x);\n"
            + "        floatResult = (float) java.lang.Math.sin(x);\n"
            + "    }\n"
            + "}\n");


    @Override
    protected boolean allowCompilationErrors() {
        return true;
    }

    @Override
    protected Detector getDetector() {
        return new MathDetector();
    }

    public int getHighestTargetSmallerThan(int max) {
        IAndroidTarget[] targets = createClient().getTargets();
        for (int i = targets.length - 1; i >= 0; i--) {
            IAndroidTarget target = targets[i];
            if (target.getVersion().getApiLevel() < max
                    && target.isPlatform() ) {
                return target.getVersion().getApiLevel();
            }
        }

        return -1;
    }

    public void test() throws Exception {
        int compileSdkVersion = getHighestTargetSmallerThan(22);
        if (compileSdkVersion < 9) {
            // Need to have a pre-M, post-Froyo target installed
            return;
        }
        assertEquals(""
                + "src/test/bytecode/MathTest.java:12: Warning: Use java.lang.Math#cos instead of android.util.FloatMath#cos() since it is faster as of API 8 [FloatMath]\n"
                + "        floatResult = FloatMath.cos(x);\n"
                + "                      ~~~~~~~~~~~~~\n"
                + "src/test/bytecode/MathTest.java:13: Warning: Use java.lang.Math#sin instead of android.util.FloatMath#sin() since it is faster as of API 8 [FloatMath]\n"
                + "        floatResult = FloatMath.sin((float) y);\n"
                + "                      ~~~~~~~~~~~~~\n"
                + "src/test/bytecode/MathTest.java:14: Warning: Use java.lang.Math#ceil instead of android.util.FloatMath#ceil() since it is faster as of API 8 [FloatMath]\n"
                + "        floatResult = android.util.FloatMath.ceil((float) y);\n"
                + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/bytecode/MathTest.java:15: Warning: Use java.lang.Math#floor instead of android.util.FloatMath#floor() since it is faster as of API 8 [FloatMath]\n"
                + "        System.out.println(FloatMath.floor(x));\n"
                + "                           ~~~~~~~~~~~~~~~\n"
                + "src/test/bytecode/MathTest.java:16: Warning: Use java.lang.Math#sqrt instead of android.util.FloatMath#sqrt() since it is faster as of API 8 [FloatMath]\n"
                + "        System.out.println(FloatMath.sqrt(z));\n"
                + "                           ~~~~~~~~~~~~~~\n"
                + "src/test/bytecode/MathTest.java:17: Warning: Use java.lang.Math#sin instead of android.util.FloatMath#sin() since it is faster as of API 8 [FloatMath]\n"
                + "        floatResult = sin((float) y);\n"
                + "                      ~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n",

            lintProject(
                    mTestFile,
                    projectProperties().compileSdk(compileSdkVersion),
                    copy("apicheck/minsdk14.xml", "AndroidManifest.xml")));
    }

    public void testNoWarningsPreFroyo() throws Exception {
        int compileSdkVersion = getHighestTargetSmallerThan(8);
        if (compileSdkVersion < 3) {
            return;
        }
        assertEquals(
            "No warnings.",

            lintProject(mTestFile,
                    projectProperties().compileSdk(compileSdkVersion),
                    copy("apicheck/minsdk2.xml", "AndroidManifest.xml")));
    }

}
