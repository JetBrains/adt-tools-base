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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "MethodMayBeStatic", "RedundantCast"})
public class SecureRandomDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SecureRandomDetector();
    }

    public void test0() throws Exception {
        assertEquals(""
                + "src/test/pkg/SecureRandomTest.java:12: Warning: It is dangerous to seed SecureRandom with the current time because that value is more predictable to an attacker than the default seed. [SecureRandom]\n"
                + "        random1.setSeed(System.currentTimeMillis()); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:13: Warning: It is dangerous to seed SecureRandom with the current time because that value is more predictable to an attacker than the default seed. [SecureRandom]\n"
                + "        random1.setSeed(System.nanoTime()); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:15: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random1.setSeed(0); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:16: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random1.setSeed(1); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:17: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random1.setSeed((int)1023); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:18: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random1.setSeed(1023L); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:19: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random1.setSeed(FIXED_SEED); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:29: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random3.setSeed(0); // Wrong: owner is java/util/Random, but applied to SecureRandom object\n"
                + "        ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:41: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random2.setSeed(seed); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SecureRandomTest.java:47: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]\n"
                + "        random2.setSeed(seedBytes); // Wrong\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 10 warnings\n",
                // Error on line 55 is only found in the IDE where we can map from ResolvedNodes to AST nodes

                lintProject(java("src/test/pkg/SecureRandomTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.security.SecureRandom;\n"
                        + "import java.util.Random;\n"
                        + "\n"
                        + "public class SecureRandomTest {\n"
                        + "    private static final long FIXED_SEED = 1000L;\n"
                        + "    protected int getDynamicSeed() {  return 1; }\n"
                        + "\n"
                        + "    public void testLiterals() {\n"
                        + "        SecureRandom random1 = new SecureRandom();\n"
                        + "        random1.setSeed(System.currentTimeMillis()); // Wrong\n"
                        + "        random1.setSeed(System.nanoTime()); // Wrong\n"
                        + "        random1.setSeed(getDynamicSeed()); // OK\n"
                        + "        random1.setSeed(0); // Wrong\n"
                        + "        random1.setSeed(1); // Wrong\n"
                        + "        random1.setSeed((int)1023); // Wrong\n"
                        + "        random1.setSeed(1023L); // Wrong\n"
                        + "        random1.setSeed(FIXED_SEED); // Wrong\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void testRandomTypeOk() {\n"
                        + "        Random random2 = new Random();\n"
                        + "        random2.setSeed(0); // OK\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void testRandomTypeWrong() {\n"
                        + "        Random random3 = new SecureRandom();\n"
                        + "        random3.setSeed(0); // Wrong: owner is java/util/Random, but applied to SecureRandom object\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void testBytesOk() {\n"
                        + "        SecureRandom random1 = new SecureRandom();\n"
                        + "        byte[] seed = random1.generateSeed(4);\n"
                        + "        random1.setSeed(seed); // OK\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void testBytesWrong() {\n"
                        + "        SecureRandom random2 = new SecureRandom();\n"
                        + "        byte[] seed = new byte[3];\n"
                        + "        random2.setSeed(seed); // Wrong\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void testFixedSeedBytes(byte something) {\n"
                        + "        SecureRandom random2 = new SecureRandom();\n"
                        + "        byte[] seedBytes = new byte[] { 1, 2, 3 };\n"
                        + "        random2.setSeed(seedBytes); // Wrong\n"
                        + "        byte[] seedBytes2 = new byte[] { 1, something, 3 };\n"
                        + "        random2.setSeed(seedBytes2); // OK\n"
                        + "    }\n"
                        + "\n"
                        + "    private static final byte[] fixedSeed = new byte[] { 1, 2, 3 };\n"
                        + "    public void testFixedSeedBytesField() {\n"
                        + "        SecureRandom random2 = new SecureRandom();\n"
                        + "        random2.setSeed(fixedSeed); // Wrong\n"
                        + "    }\n"
                        + "\n"
                        + "}\n")));
    }
}
