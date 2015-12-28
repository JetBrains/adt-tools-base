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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "ImplicitArrayToString",
        "MethodMayBeStatic"})
public class AllowAllHostnameVerifierDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AllowAllHostnameVerifierDetector();
    }

    public void testBroken() throws Exception {
        assertEquals(""
                + "src/test/pkg/InsecureHostnameVerifier.java:22: Warning: Using the AllowAllHostnameVerifier HostnameVerifier is unsafe because it always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [AllowAllHostnameVerifier]\n"
                + "            connection.setHostnameVerifier(new AllowAllHostnameVerifier());\n"
                + "                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/InsecureHostnameVerifier.java:23: Warning: Using the ALLOW_ALL_HOSTNAME_VERIFIER HostnameVerifier is unsafe because it always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [AllowAllHostnameVerifier]\n"
                + "            connection.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);\n"
                + "                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",
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
                                + "            android:name=\".InsecureHostnameVerifier\" >\n"
                                + "        </service>\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>\n"),
                        java("src/test/pkg/InsecureHostnameVerifier.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Intent;\n"
                                + "\n"
                                + "import java.io.IOException;\n"
                                + "import java.net.URL;\n"
                                + "import javax.net.ssl.HostnameVerifier;\n"
                                + "import javax.net.ssl.HttpsURLConnection;\n"
                                + "import javax.net.ssl.SSLContext;\n"
                                + "import javax.net.ssl.SSLSession;\n"
                                + "import javax.net.ssl.TrustManager;\n"
                                + "import javax.net.ssl.X509TrustManager;\n"
                                + "\n"
                                + "import org.apache.http.conn.ssl.SSLSocketFactory;\n"
                                + "import org.apache.http.conn.ssl.AllowAllHostnameVerifier;\n"
                                + "\n"
                                + "public class InsecureHostnameVerifier {\n"
                                + "    protected void onHandleIntent(Intent intent) {\n"
                                + "        try {\n"
                                + "            URL url = new URL(\"https://www.google.com\");\n"
                                + "            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();\n"
                                + "            connection.setHostnameVerifier(new AllowAllHostnameVerifier());\n"
                                + "            connection.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);\n"
                                + "        } catch (IOException e) {\n"
                                + "            System.out.println(e.getStackTrace());\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n")
                ));
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
                                + "            android:name=\".ExampleHostnameVerifier\" >\n"
                                + "        </service>\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>\n"),
                        java("src/test/pkg/ExampleHostnameVerifier.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Intent;\n"
                                + "\n"
                                + "import java.io.IOException;\n"
                                + "import java.net.URL;\n"
                                + "import javax.net.ssl.HostnameVerifier;\n"
                                + "import javax.net.ssl.HttpsURLConnection;\n"
                                + "import javax.net.ssl.SSLContext;\n"
                                + "import javax.net.ssl.SSLSession;\n"
                                + "import javax.net.ssl.TrustManager;\n"
                                + "import javax.net.ssl.X509TrustManager;\n"
                                + "\n"
                                + "import org.apache.http.conn.ssl.SSLSocketFactory;\n"
                                + "import org.apache.http.conn.ssl.StrictHostnameVerifier;\n"
                                + "\n"
                                + "public class ExampleHostnameVerifier {\n"
                                + "    protected void onHandleIntent(Intent intent) {\n"
                                + "        try {\n"
                                + "            URL url = new URL(\"https://www.google.com\");\n"
                                + "            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();\n"
                                + "            connection.setHostnameVerifier(new StrictHostnameVerifier());\n"
                                + "            connection.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);\n"
                                + "        } catch (IOException e) {\n"
                                + "            System.out.println(e.getStackTrace());\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n")
                ));
    }
}
