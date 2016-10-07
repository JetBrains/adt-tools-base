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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "ImplicitArrayToString",
        "ConstantConditions", "ConstantIfStatement"})
public class BadHostnameVerifierDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new BadHostnameVerifierDetector();
    }

    public void testBroken() throws Exception {
        assertEquals(""
                + "src/test/pkg/InsecureHostnameVerifier.java:9: Warning: verify always returns true, which could cause insecure network traffic due to trusting TLS/SSL server certificates for wrong hostnames [BadHostnameVerifier]\n"
                + "        public boolean verify(String hostname, SSLSession session) {\n"
                + "                       ~~~~~~\n"
                + "0 errors, 1 warnings\n",
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
                        copy("res/values/strings.xml"),
                        java("src/test/pkg/InsecureHostnameVerifier.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import javax.net.ssl.HostnameVerifier;\n"
                                + "import javax.net.ssl.SSLSession;\n"
                                + "\n"
                                + "public abstract class InsecureHostnameVerifier  {\n"
                                + "    HostnameVerifier allowAll = new HostnameVerifier() {\n"
                                + "        @Override\n"
                                + "        public boolean verify(String hostname, SSLSession session) {\n"
                                + "            return true;\n"
                                + "        }\n"
                                + "    };\n"
                                + "\n"
                                + "    HostnameVerifier allowAll2 = new HostnameVerifier() {\n"
                                + "        @Override\n"
                                + "        public boolean verify(String hostname, SSLSession session) {\n"
                                + "            boolean returnValue = true;\n"
                                + "            if (true) {\n"
                                + "                int irrelevant = 5;\n"
                                + "                if (irrelevant > 6) {\n"
                                + "                    return returnValue;\n"
                                + "                }\n"
                                + "            }\n"
                                + "            return returnValue;\n"
                                + "        }\n"
                                + "    };\n"
                                + "\n"
                                + "    HostnameVerifier unknown = new HostnameVerifier() {\n"
                                + "        @Override\n"
                                + "        public boolean verify(String hostname, SSLSession session) {\n"
                                + "            boolean returnValue = true;\n"
                                + "            if (hostname.contains(\"something\")) {\n"
                                + "                returnValue = false;\n"
                                + "            }\n"
                                + "            return returnValue;\n"
                                + "        }\n"
                                + "    };\n"
                                + "}\n"
                                + "\n")
                ));
    }

    public void testBrokenWithSuppressLint() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        java("src/android/annotation/SuppressLint.java", ""
                                + "package android.annotation;\n"
                                + "import static java.lang.annotation.ElementType.CONSTRUCTOR;\n"
                                + "import static java.lang.annotation.ElementType.FIELD;\n"
                                + "import static java.lang.annotation.ElementType.LOCAL_VARIABLE;\n"
                                + "import static java.lang.annotation.ElementType.METHOD;\n"
                                + "import static java.lang.annotation.ElementType.PARAMETER;\n"
                                + "import static java.lang.annotation.ElementType.TYPE;\n"
                                + "import java.lang.annotation.Retention;\n"
                                + "import java.lang.annotation.RetentionPolicy;\n"
                                + "import java.lang.annotation.Target;\n"
                                + "\n"
                                + "@Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})\n"
                                + "@Retention(RetentionPolicy.CLASS)\n"
                                + "public @interface SuppressLint {\n"
                                + "    String[] value();\n"
                                + "}"),
                        java("src/test/pkg/DebugHostnameVerifier.java", ""
                                + "package test.pkg;"
                                + "\n"
                                + "import javax.net.ssl.HostnameVerifier;\n"
                                + "import javax.net.ssl.SSLSession;\n"
                                + "\n"
                                + "@android.annotation.SuppressLint(\"BadHostnameVerifier\")\n"
                                + "public abstract class DebugHostnameVerifier  {\n"
                                + "    HostnameVerifier allowAll = new HostnameVerifier() {\n"
                                + "        @Override\n"
                                + "        public boolean verify(String hostname, SSLSession session) {\n"
                                + "            return true;\n"
                                + "        }\n"
                                + "    };\n"
                                + "}\n")));
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
                        copy("res/values/strings.xml"),
                        java("src/test/pkg/ExampleHostnameVerifier.java", ""
                                + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.app.IntentService;\n"
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
                                        + "public class ExampleHostnameVerifier extends IntentService {\n"
                                        + "    HostnameVerifier denyAll = new HostnameVerifier() {\n"
                                        + "        @Override\n"
                                        + "        public boolean verify(String hostname, SSLSession session) {\n"
                                        + "            return false;\n"
                                        + "        }\n"
                                        + "    };\n"
                                        + "\n"
                                        + "    public ExampleHostnameVerifier() {\n"
                                        + "        super(\"ExampleHostnameVerifier\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onHandleIntent(Intent intent) {\n"
                                        + "        try {\n"
                                        + "            URL url = new URL(\"https://www.google.com\");\n"
                                        + "            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();\n"
                                        + "            connection.setHostnameVerifier(denyAll);\n"
                                        + "        } catch (IOException e) {\n"
                                        + "            System.out.println(e.getStackTrace());\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n")
                ));
    }
}
