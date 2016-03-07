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

@SuppressWarnings("ClassNameDiffersFromFileName")
public class GetSignaturesDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new GetSignaturesDetector();
    }

    public void testLintWarningOnSingleGetSignaturesFlag() throws Exception {
        assertEquals(
                "src/test/pkg/GetSignaturesSingleFlagTest.java:9: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                        + "            .getPackageInfo(\"some.pkg\", PackageManager.GET_SIGNATURES);\n"
                        + "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/GetSignaturesSingleFlagTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "\n"
                                + "public class GetSignaturesSingleFlagTest extends Activity {\n"
                                + "    public void failLintCheck() throws Exception {\n"
                                + "        getPackageManager()\n"
                                + "            .getPackageInfo(\"some.pkg\", PackageManager.GET_SIGNATURES);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testLintWarningOnGetSignaturesFlagInBitwiseOrExpression() throws Exception {
        assertEquals(
            "src/test/pkg/GetSignaturesBitwiseOrTest.java:11: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                    + "            .getPackageInfo(\"some.pkg\", GET_GIDS | GET_SIGNATURES | GET_PROVIDERS);\n"
                    + "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                    + "0 errors, 1 warnings\n",
            lintProject(
                java("src/test/pkg/GetSignaturesBitwiseOrTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import static android.content.pm.PackageManager.*;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.content.pm.PackageManager;\n"
                        + "\n"
                        + "public class GetSignaturesBitwiseOrTest extends Activity {\n"
                        + "    public void failLintCheck() throws Exception {\n"
                        + "        getPackageManager()\n"
                        + "            .getPackageInfo(\"some.pkg\", GET_GIDS | GET_SIGNATURES | GET_PROVIDERS);\n"
                        + "    }\n"
                        + "}")
            ));
    }

    public void testLintWarningOnGetSignaturesFlagInBitwiseXorExpression() throws Exception {
        assertEquals(
                "src/test/pkg/GetSignaturesBitwiseXorTest.java:8: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                        + "        getPackageManager().getPackageInfo(\"some.pkg\", PackageManager.GET_SIGNATURES ^ 0x0);\n"
                        + "                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/GetSignaturesBitwiseXorTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "\n"
                                + "public class GetSignaturesBitwiseXorTest extends Activity {\n"
                                + "    public void failLintCheck() throws Exception {\n"
                                + "        getPackageManager().getPackageInfo(\"some.pkg\", PackageManager.GET_SIGNATURES ^ 0x0);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testLintWarningOnGetSignaturesFlagInBitwiseAndExpression() throws Exception {
        assertEquals(
                "src/test/pkg/GetSignaturesBitwiseAndTest.java:9: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                        + "            Integer.MAX_VALUE & PackageManager.GET_SIGNATURES);\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/GetSignaturesBitwiseAndTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "\n"
                                + "public class GetSignaturesBitwiseAndTest extends Activity {\n"
                                + "    public void failLintCheck() throws Exception {\n"
                                + "        getPackageManager().getPackageInfo(\"some.pkg\",\n"
                                + "            Integer.MAX_VALUE & PackageManager.GET_SIGNATURES);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testLintWarningOnFlagsInStaticField() throws Exception {
        assertEquals(
                "src/test/pkg/GetSignaturesStaticFieldTest.java:9: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                        + "        getPackageManager().getPackageInfo(\"some.pkg\", FLAGS);\n"
                        + "                                                       ~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/GetSignaturesStaticFieldTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "\n"
                                + "public class GetSignaturesStaticFieldTest extends Activity {\n"
                                + "    private static final int FLAGS = PackageManager.GET_SIGNATURES;\n"
                                + "    public void failLintCheck() throws Exception {\n"
                                + "        getPackageManager().getPackageInfo(\"some.pkg\", FLAGS);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testNoLintWarningOnFlagsInLocalVariable() throws Exception {
        assertEquals(""
                        + "src/test/pkg/GetSignaturesLocalVariableTest.java:9: Information: Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details. [PackageManagerGetSignatures]\n"
                        + "        getPackageManager().getPackageInfo(\"some.pkg\", flags);\n"
                        + "                                                       ~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        java("src/test/pkg/GetSignaturesLocalVariableTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "\n"
                                + "public class GetSignaturesLocalVariableTest extends Activity {\n"
                                + "    public void passLintCheck() throws Exception {\n"
                                + "        int flags = PackageManager.GET_SIGNATURES;\n"
                                + "        getPackageManager().getPackageInfo(\"some.pkg\", flags);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testNoLintWarningOnGetSignaturesWithNoFlag() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        java("src/test/pkg/GetSignaturesNoFlagTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import static android.content.pm.PackageManager.*;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "\n"
                                + "public class GetSignaturesNoFlagTest extends Activity {\n"
                                + "    public void passLintCheck() throws Exception {\n"
                                + "        getPackageManager()\n"
                                + "            .getPackageInfo(\"some.pkg\",\n"
                                + "                GET_ACTIVITIES |\n"
                                + "                GET_GIDS |\n"
                                + "                GET_CONFIGURATIONS |\n"
                                + "                GET_INSTRUMENTATION |\n"
                                + "                GET_PERMISSIONS |\n"
                                + "                GET_PROVIDERS |\n"
                                + "                GET_RECEIVERS |\n"
                                + "                GET_SERVICES |\n"
                                + "                GET_UNINSTALLED_PACKAGES);\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testNoLintWarningOnGetPackageInfoOnNonPackageManagerClass() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        java("src/test/pkg/GetSignaturesNotPackageManagerTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.pm.PackageManager;\n"
                                + "import android.content.pm.PackageInfo;\n"
                                + "\n"
                                + "public class GetSignaturesNotPackageManagerTest extends Activity {\n"
                                + "    public void passLintCheck(Mock mock) throws Exception {\n"
                                + "        mock.getPackageInfo(\"some.pkg\", PackageManager.GET_SIGNATURES);\n"
                                + "    }\n"
                                + "    public interface Mock {\n"
                                + "        PackageInfo getPackageInfo(String pkg, int flags);\n"
                                + "    }\n"
                                + "}")
                ));
    }
}
