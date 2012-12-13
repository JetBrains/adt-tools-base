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

package com.android.sdkstats;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class SdkStatsServiceTest extends TestCase {

    private static class MockSdkStatsService extends SdkStatsService {

        private final String mOsName;
        private final String mOsVersion;
        private final String mOsArch;
        private final String mJavaVersion;
        private final Map<String, String> mEnvVars = new HashMap<String, String>();
        private URL mPingUrlResult;

        public MockSdkStatsService(String osName,
                String osVersion,
                String osArch,
                String javaVersion) {
                    mOsName = osName;
                    mOsVersion = osVersion;
                    mOsArch = osArch;
                    mJavaVersion = javaVersion;
        }

        public URL getPingUrlResult() {
            return mPingUrlResult;
        }

        public void setSystemEnv(String varName, String value) {
            mEnvVars.put(varName, value);
        }

        @Override
        protected String getSystemProperty(String name) {
            if (SdkStatsService.SYS_PROP_OS_NAME.equals(name)) {
                return mOsName;
            } else if (SdkStatsService.SYS_PROP_OS_VERSION.equals(name)) {
                return mOsVersion;
            } else if (SdkStatsService.SYS_PROP_OS_ARCH.equals(name)) {
                return mOsArch;
            } else if (SdkStatsService.SYS_PROP_JAVA_VERSION.equals(name)) {
                return mJavaVersion;
            }
            // Don't use current properties values, we don't want the tests to be flaky
            fail("SdkStatsServiceTest doesn't define a system.property for " + name);
            return null;
        }

        @Override
        protected String getSystemEnv(String name) {
            if (mEnvVars.containsKey(name)) {
                return mEnvVars.get(name);
            }
            // Don't use current env vars, we don't want the tests to be flaky
            fail("SdkStatsServiceTest doesn't define a system.getenv for " + name);
            return null;
        }

        @Override
        protected void doPing(String app, String version,
                Map<String, String> extras) {
            // The super.doPing() does:
            // 1- normalize input,
            // 2- check the ping time,
            // 3- check/create the pind id,
            // 4- create the ping URL
            // 5- and send the network ping in a thread.
            // In this mock version we just do steps 1 and 4 and record the URL;
            // obvious we don't check the ping time in the prefs nor send the actual ping.

            // Validate the application and version input.
            final String nApp = normalizeAppName(app);
            final String nVersion = normalizeVersion(version);

            long id = 0x42;
            try {
                mPingUrlResult = createPingUrl(nApp, nVersion, id, extras);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSdkStatsService_getJvmArch() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i386", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i486", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Linux",   "4.0", "i486-linux", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i586", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i686", "1.7");
        assertEquals("x86", m.getJvmArch());

        m = new MockSdkStatsService("Mac OS",  "10.0", "x86_64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Mac OS",  "8.0", "PowerPC", "1.7");
        assertEquals("ppc", m.getJvmArch());

        m = new MockSdkStatsService("Mac OS",  "4.0", "x86_64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "ia64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "amd64", "1.7");
        assertEquals("x86_64", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", "atom", "1.7");
        assertEquals("atom", m.getJvmArch());

        // 32 chars max
        m = new MockSdkStatsService("Windows", "4.0",
                "one3456789ten3456789twenty6789thirty6789", "1.7");
        assertEquals("one3456789ten3456789twenty6789th", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", "", "1.7");
        assertEquals("unknown", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", null, "1.7");
        assertEquals("unknown", m.getJvmArch());
    }

    public void testSdkStatsService_getJvmVersion() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("1.7", m.getJvmVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", "");
        assertEquals("unknown", m.getJvmVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", null);
        assertEquals("unknown", m.getJvmVersion());

        // 8 chars max
        m = new MockSdkStatsService("Windows", "4.0", "x86",
                "one3456789ten3456789twenty6789thirty6789");
        assertEquals("one34567", m.getJvmVersion());
    }

    public void testSdkStatsService_getJvmInfo() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("1.7-x86", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", "amd64", "1.7.8_09");
        assertEquals("1.7-x86_64", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", "", "");
        assertEquals("unknown-unknown", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", null, null);
        assertEquals("unknown-unknown", m.getJvmInfo());

        // 8+32 chars max
        m = new MockSdkStatsService("Windows", "4.0",
                "one3456789ten3456789twenty6789thirty6789",
                "one3456789ten3456789twenty6789thirty6789");
        assertEquals("one34567-one3456789ten3456789twenty6789th", m.getJvmInfo());
    }

    public void testSdkStatsService_getOsVersion() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0.32", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4", "x86", "1.7.8_09");
        assertEquals(null, m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4.0;extrainfo", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("10.8", m.getOsVersion());

        m = new MockSdkStatsService("Mac OS", "10.8", "x86_64", "1.7.8_09");
        assertEquals("10.8", m.getOsVersion());

        m = new MockSdkStatsService("Other", "", "x86_64", "1.7.8_09");
        assertEquals(null, m.getOsVersion());

        m = new MockSdkStatsService("Other", null, "x86_64", "1.7.8_09");
        assertEquals(null, m.getOsVersion());
    }

    public void testSdkStatsService_getOsArch() {
        MockSdkStatsService m;

        // 64 bit jvm
        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        // 32 bit jvm with 32 vs 64 bit os
        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", null);
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", "AMD64");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", "IA64");
        assertEquals("x86_64", m.getOsArch());

        // 32 bit jvm with 32 vs 64 bit os
        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", null);
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "i686-linux");
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "AMD64");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "x86_64");
        assertEquals("x86_64", m.getOsArch());
    }

    public void testSdkStatsService_getOsName() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("mac-10.8", m.getOsName());

        m = new MockSdkStatsService("mac", "10", "x86", "1.7.8_09");
        assertEquals("mac", m.getOsName());

        m = new MockSdkStatsService("Windows", "6.2", "x86_64", "1.7.8_09");
        assertEquals("win-6.2", m.getOsName());

        m = new MockSdkStatsService("win", "6.2", "x86", "1.7.8_09");
        assertEquals("win-6.2", m.getOsName());

        m = new MockSdkStatsService("win", "6", "x86_64", "1.7.8_09");
        assertEquals("win", m.getOsName());

        m = new MockSdkStatsService("Linux", "foobuntu-32", "x86", "1.7.8_09");
        assertEquals("linux", m.getOsName());

        m = new MockSdkStatsService("linux", "1", "x86_64", "1.7.8_09");
        assertEquals("linux", m.getOsName());

        m = new MockSdkStatsService("PowerPC", "32", "ppc", "1.7.8_09");
        assertEquals("PowerPC", m.getOsName());

        m = new MockSdkStatsService("freebsd", "42", "x86_64", "1.7.8_09");
        assertEquals("freebsd", m.getOsName());

        m = new MockSdkStatsService("openbsd", "43", "x86_64", "1.7.8_09");
        assertEquals("openbsd", m.getOsName());

        // 32 chars max
        m = new MockSdkStatsService("one3456789ten3456789twenty6789thirty6789",
                "42", "x86_64", "1.7.8_09");
        assertEquals("one3456789ten3456789twenty6789th", m.getOsName());
    }

    public void testSdkStatsService_parseVersion() {
        // Tests that the version parses supports the new "major.minor.micro rcPreview" format
        // as well as "x.y.z.t" formats as well as Eclipse's "x.y.z.v2012somedate" formats.

        MockSdkStatsService m;
        m = new MockSdkStatsService("Windows", "6.2", "x86_64", "1.7.8_09");

        m.ping("monitor", "21");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.1");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.1.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.2.03");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.2.3.4");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.4&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // More than 4 parts or extra stuff that is not an "rc" preview are ignored.
        m.ping("monitor", "21.2.3.4.5.6.7.8");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.4&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.2.3.4.v20120101 the rest is ignored");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.4&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // If the "rc" preview integer is present, it's equivalent to a 4th number.
        m.ping("monitor", "21 rc4");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.0.0.4&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.01 rc5");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.1.0.5&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        m.ping("monitor", "21.02.03 rc6");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.6&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // If there's a 4-part version number, the rc preview number isn't used.
        m.ping("monitor", "21.2.3.4 rc7");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_monitor&" +
                "id=42&" +
                "version=21.2.3.4&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // For Eclipse plugins, the 4th part might be a date. It is ignored.
        m.ping("eclipse", "21.2.3.v20120102235958");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_eclipse&" +
                "id=42&" +
                "version=21.2.3.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());
    }

    public void testSdkStatsService_glPing() {
        MockSdkStatsService m;
        m = new MockSdkStatsService("Windows", "6.2", "x86_64", "1.7.8_09");

        // Send emulator ping with just emulator version, no GL stuff
        m.ping("emulator", "12");
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // Send emulator ping with just emulator version, no GL stuff.
        // This is the same request but using the variable string list API, arg 0 is the "ping" app.
        m.ping(new String[] { "ping", "emulator", "12" });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // Send a ping for a non-emulator app with extra parameters, no GL stuff
        m.ping(new String[] { "ping", "not-emulator", "12", "arg1", "arg2", "arg3" });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_notemulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64",
                m.getPingUrlResult().toString());

        // Send a ping for the emulator app with extra parameters, GL stuff is added, 3 parameters
        m.ping(new String[] { "ping", "emulator", "12", "Vendor Inc.", "Some cool_GPU!!! (fast one!)", "1.2.3.4_preview" });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64&" +
                "glm=Vendor+Inc.&" +
                "glr=Some+cool_GPU+%28fast+one+%29&" +
                "glv=1.2.3.4_preview",
                m.getPingUrlResult().toString());

        // Send a ping for the emulator app with extra parameters, GL stuff is added, 2 parameters
        m.ping(new String[] { "ping", "emulator", "12", "Vendor Inc.", "Some cool_GPU!!! (fast one!)" });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64&" +
                "glm=Vendor+Inc.&" +
                "glr=Some+cool_GPU+%28fast+one+%29",
                m.getPingUrlResult().toString());

        // Send a ping for the emulator app with extra parameters, GL stuff is added, 1 parameter
        m.ping(new String[] { "ping", "emulator", "12", "Vendor Inc." });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64&" +
                "glm=Vendor+Inc.",
                m.getPingUrlResult().toString());

        // Parameters that are more than 128 chars are cut short.
        m.ping(new String[] { "ping", "emulator", "12",
                // 130 chars each
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789",
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789",
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" });
        assertEquals(
                "http://tools.google.com/service/update?" +
                "as=androidsdk_emulator&" +
                "id=42&" +
                "version=12.0.0.0&" +
                "os=win-6.2&" +
                "osa=x86_64&" +
                "vma=1.7-x86_64&" +
                "glm=01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567&" +
                "glr=01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567&" +
                "glv=01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567",
                m.getPingUrlResult().toString());
    }
}
