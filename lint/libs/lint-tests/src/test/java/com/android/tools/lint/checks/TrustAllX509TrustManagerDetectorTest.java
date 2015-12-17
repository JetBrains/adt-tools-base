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
public class TrustAllX509TrustManagerDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TrustAllX509TrustManagerDetector();
    }

    public void testBroken() throws Exception {
        assertEquals(
            "src/test/pkg/InsecureTLSIntentService.java:22: Warning: checkClientTrusted is empty, which could cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [TrustAllX509TrustManager]\n" +
            "        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {\n" +
            "                    ~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/InsecureTLSIntentService.java:26: Warning: checkServerTrusted is empty, which could cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [TrustAllX509TrustManager]\n" +
            "        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {\n" +
            "                    ~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 2 warnings\n" +
            "",
            lintProject(
                    "tls.xml=>AndroidManifest.xml",
                    "res/values/strings.xml",
                    "bytecode/InsecureTLSIntentService.java.txt=>src/test/pkg/InsecureTLSIntentService.java",
                    "bytecode/InsecureTLSIntentService.class.data=>bin/classes/test/pkg/InsecureTLSIntentService.class",
                    "bytecode/InsecureTLSIntentService$1.class.data=>bin/classes/test/pkg/InsecureTLSIntentService$1.class"));
    }

    public void testCorrect() throws Exception {
        assertEquals(
            "No warnings.",
            lintProject(
                    "tls1.xml=>AndroidManifest.xml",
                    "res/values/strings.xml",
                    "bytecode/ExampleTLSIntentService.java.txt=>src/test/pkg/ExmapleTLSIntentService.java",
                    "bytecode/ExampleTLSIntentService.class.data=>bin/classes/test/pkg/ExampleTLSIntentService.class",
                    "bytecode/ExampleTLSIntentService$1.class.data=>bin/classes/test/pkg/ExampleTLSIntentService$1.class"));
    }
}
