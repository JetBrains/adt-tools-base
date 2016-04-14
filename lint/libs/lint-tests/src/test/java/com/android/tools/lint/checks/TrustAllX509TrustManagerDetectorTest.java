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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "ImplicitArrayToString"})
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
                    xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"test.pkg\"\n"
                            + "    android:versionCode=\"1\"\n"
                            + "    android:versionName=\"1.0\" >\n"
                            + "\n"
                            + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:icon=\"@drawable/ic_launcher\"\n"
                            + "        android:label=\"@string/app_name\" >\n"
                            + "        <service\n"
                            + "            android:name=\".InsecureTLSIntentService\" >\n"
                            + "        </service>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n"
                            + "\n"),
                    java("src/test/pkg/InsecureTLSIntentService.java", ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.IntentService;\n"
                            + "import android.content.Intent;\n"
                            + "\n"
                            + "import java.security.GeneralSecurityException;\n"
                            + "import java.security.cert.CertificateException;\n"
                            + "\n"
                            + "import javax.net.ssl.HttpsURLConnection;\n"
                            + "import javax.net.ssl.SSLContext;\n"
                            + "import javax.net.ssl.TrustManager;\n"
                            + "import javax.net.ssl.X509TrustManager;\n"
                            + "\n"
                            + "public class InsecureTLSIntentService extends IntentService {\n"
                            + "    TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {\n"
                            + "        @Override\n"
                            + "        public java.security.cert.X509Certificate[] getAcceptedIssuers() {\n"
                            + "            return null;\n"
                            + "        }\n"
                            + "\n"
                            + "        @Override\n"
                            + "        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {\n"
                            + "        }\n"
                            + "\n"
                            + "        @Override\n"
                            + "        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {\n"
                            + "        }\n"
                            + "    }};\n"
                            + "\n"
                            + "    public InsecureTLSIntentService() {\n"
                            + "        super(\"InsecureTLSIntentService\");\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    protected void onHandleIntent(Intent intent) {\n"
                            + "        try {\n"
                            + "            SSLContext sc = SSLContext.getInstance(\"TLSv1.2\");\n"
                            + "            sc.init(null, trustAllCerts, new java.security.SecureRandom());\n"
                            + "            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());\n"
                            + "        } catch (GeneralSecurityException e) {\n"
                            + "            System.out.println(e.getStackTrace());\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n")));

        // TODO: Test bytecode check via library jar?
                    //"bytecode/InsecureTLSIntentService.java.txt=>src/test/pkg/InsecureTLSIntentService.java",
                    //"bytecode/InsecureTLSIntentService.class.data=>bin/classes/test/pkg/InsecureTLSIntentService.class",
                    //"bytecode/InsecureTLSIntentService$1.class.data=>bin/classes/test/pkg/InsecureTLSIntentService$1.class"));
    }

    public void testCorrect() throws Exception {
        assertEquals(
            "No warnings.",

                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.pkg\"\n"
                                + "    android:versionCode=\"1\"\n"
                                + "    android:versionName=\"1.0\" >\n"
                                + "\n"
                                + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                + "\n"
                                + "    <application\n"
                                + "        android:icon=\"@drawable/ic_launcher\"\n"
                                + "        android:label=\"@string/app_name\" >\n"
                                + "        <service\n"
                                + "            android:name=\".ExampleTLSIntentService\" >\n"
                                + "        </service>\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>\n"),
                        java("src/test/pkg/ExampleTLSIntentService.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.app.IntentService;\n"
                                + "import android.content.Intent;\n"
                                + "\n"
                                + "import java.io.BufferedInputStream;\n"
                                + "import java.io.FileInputStream;\n"
                                + "import java.security.GeneralSecurityException;\n"
                                + "import java.security.cert.CertificateException;\n"
                                + "import java.security.cert.CertificateFactory;\n"
                                + "import java.security.cert.X509Certificate;\n"
                                + "\n"
                                + "import javax.net.ssl.HttpsURLConnection;\n"
                                + "import javax.net.ssl.SSLContext;\n"
                                + "import javax.net.ssl.TrustManager;\n"
                                + "import javax.net.ssl.X509TrustManager;\n"
                                + "\n"
                                + "public class ExampleTLSIntentService extends IntentService {\n"
                                + "    TrustManager[] trustManagerExample;\n"
                                + "\n"
                                + "    {\n"
                                + "        trustManagerExample = new TrustManager[]{new X509TrustManager() {\n"
                                + "            @Override\n"
                                + "            public X509Certificate[] getAcceptedIssuers() {\n"
                                + "                try {\n"
                                + "                    FileInputStream fis = new FileInputStream(\"testcert.pem\");\n"
                                + "                    BufferedInputStream bis = new BufferedInputStream(fis);\n"
                                + "                    CertificateFactory cf = CertificateFactory.getInstance(\"X.509\");\n"
                                + "                    X509Certificate cert = (X509Certificate) cf.generateCertificate(bis);\n"
                                + "                    return new X509Certificate[]{cert};\n"
                                + "                } catch (Exception e) {\n"
                                + "                    throw new RuntimeException(\"Could not load cert\");\n"
                                + "                }\n"
                                + "            }\n"
                                + "\n"
                                + "            @Override\n"
                                + "            public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {\n"
                                + "                throw new CertificateException(\"Not trusted\");\n"
                                + "            }\n"
                                + "\n"
                                + "            @Override\n"
                                + "            public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {\n"
                                + "                throw new CertificateException(\"Not trusted\");\n"
                                + "            }\n"
                                + "        }};\n"
                                + "    }\n"
                                + "\n"
                                + "    public ExampleTLSIntentService() {\n"
                                + "        super(\"ExampleTLSIntentService\");\n"
                                + "    }\n"
                                + "\n"
                                + "    @Override\n"
                                + "    protected void onHandleIntent(Intent intent) {\n"
                                + "        try {\n"
                                + "            SSLContext sc = SSLContext.getInstance(\"TLSv1.2\");\n"
                                + "            sc.init(null, trustManagerExample, new java.security.SecureRandom());\n"
                                + "            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());\n"
                                + "        } catch (GeneralSecurityException e) {\n"
                                + "            System.out.println(e.getStackTrace());\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n")));
    }
}
