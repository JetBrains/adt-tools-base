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

@SuppressWarnings("javadoc")
public class SslCertificateSocketFactoryDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SslCertificateSocketFactoryDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    public void test() throws Exception {
        assertEquals(
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:21: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet, 80);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:22: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet4, 80);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:23: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet6, 80);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:24: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet, 80, inet, 2000);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:25: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet4, 80, inet, 2000);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:26: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]\n" +
                "        sf.createSocket(inet6, 80, inet, 2000);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/SSLCertificateSocketFactoryTest.java:29: Warning: Use of SSLCertificateSocketFactory.getInsecure() can cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryGetInsecure]\n" +
                "                SSLCertificateSocketFactory.getInsecure(-1,null));\n" +
                "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 7 warnings\n",
                lintProject(java("src/test/pkg/SSLCertificateSocketFactoryTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.net.SSLCertificateSocketFactory;\n"
                        + "import java.net.InetAddress;\n"
                        + "import java.net.Inet4Address;\n"
                        + "import java.net.Inet6Address;\n"
                        + "import javax.net.ssl.HttpsURLConnection;\n"
                        + "\n"
                        + "public class SSLCertificateSocketFactoryTest {\n"
                        + "    public void foo() {\n"
                        + "        byte[] ipv4 = new byte[4];\n"
                        + "        byte[] ipv6 = new byte[16];\n"
                        + "        InetAddress inet = Inet4Address.getByAddress(ipv4);\n"
                        + "        Inet4Address inet4 = (Inet4Address) Inet4Address.getByAddress(ipv4);\n"
                        + "        Inet6Address inet6 = Inet6Address.getByAddress(null, ipv6, 0);\n"
                        + "\n"
                        + "        SSLCertificateSocketFactory sf = (SSLCertificateSocketFactory)\n"
                        + "        SSLCertificateSocketFactory.getDefault(0);\n"
                        + "        sf.createSocket(\"www.google.com\", 80); // ok\n"
                        + "        sf.createSocket(\"www.google.com\", 80, inet, 2000); // ok\n"
                        + "        sf.createSocket(inet, 80);\n"
                        + "        sf.createSocket(inet4, 80);\n"
                        + "        sf.createSocket(inet6, 80);\n"
                        + "        sf.createSocket(inet, 80, inet, 2000);\n"
                        + "        sf.createSocket(inet4, 80, inet, 2000);\n"
                        + "        sf.createSocket(inet6, 80, inet, 2000);\n"
                        + "\n"
                        + "        HttpsURLConnection.setDefaultSSLSocketFactory(\n"
                        + "                SSLCertificateSocketFactory.getInsecure(-1,null));\n"
                        + "    }\n"
                        + "}\n")));
    }
}
