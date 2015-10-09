/*
 * Copyright (C) 2015 The Android Open Source Project
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("javadoc")
public class AllowAllHostnameVerifierDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AllowAllHostnameVerifierDetector();
    }

    public void testBroken() throws Exception {
        assertEquals(
            "src/test/pkg/InsecureHostnameVerifier.java:21: Warning: verify always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [AllowAllHostnameVerifier]\n" +
            "        public boolean verify(String hostname, SSLSession session) {\n" +
            "                       ~~~~~~\n" +
            "src/test/pkg/InsecureHostnameVerifier.java:36: Warning: Using the AllowAllHostnameVerifier HostnameVerifier is unsafe because it always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [AllowAllHostnameVerifier]\n" +
            "            connection.setHostnameVerifier(new AllowAllHostnameVerifier());\n" +
            "                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/InsecureHostnameVerifier.java:37: Warning: Using the ALLOW_ALL_HOSTNAME_VERIFIER HostnameVerifier is unsafe because it always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [AllowAllHostnameVerifier]\n" +
            "            connection.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);\n" +
            "                                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 3 warnings\n",
            lintProject(
                    "hostnameverifier.xml=>AndroidManifest.xml",
                    "res/values/strings.xml",
                    "bytecode/InsecureHostnameVerifier.java.txt=>src/test/pkg/InsecureHostnameVerifier.java",
                    "bytecode/InsecureHostnameVerifier.class.data=>bin/classes/test/pkg/InsecureHostnameVerifier.class",
                    "bytecode/InsecureHostnameVerifier$1.class.data=>bin/classes/test/pkg/InsecureHostnameVerifier$1.class"));
    }

    public void testCorrect() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject(
                    "hostnameverifier1.xml=>AndroidManifest.xml",
                    "res/values/strings.xml",
                    "bytecode/ExampleHostnameVerifier.java.txt=>src/test/pkg/ExampleHostnameVerifier.java",
                    "bytecode/ExampleHostnameVerifier.class.data=>bin/classes/test/pkg/ExampleHostnameVerifier.class",
                    "bytecode/ExampleHostnameVerifier$1.class.data=>bin/classes/test/pkg/ExampleHostnameVerifier$1.class"));
    }
}
