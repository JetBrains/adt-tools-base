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
public class UnsafeBroadcastReceiverDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new UnsafeBroadcastReceiverDetector();
    }

    // TODO: Add something similar to PermissionRequirementTest#testDbUpToDate to ensure that
    // the list of protected-broadcast action strings in UnsafeBroadcastReceiverDetector
    // (PROTECTED_BROADCASTS) is up-to-date with the Android source code.

    public void testBroken() throws Exception {
        assertEquals(
                "src/test/pkg/TestReceiver.java:10: Warning: This broadcast receiver declares " +
                "an intent-filter for a protected broadcast action string, which can only be " +
                "sent by the system, not third-party applications. However, the receiver's " +
                "onReceive method does not appear to call getAction to ensure that the " +
                "received Intent's action string matches the expected value, potentially " +
                "making it possible for another actor to send a spoofed intent with no " +
                "action string or a different action string and cause undesired behavior. " +
                "[UnsafeProtectedBroadcastReceiver]\n" +
                "    public void onReceive(Context context, Intent intent) {\n" +
                "                ~~~~~~~~~\n" +
                "0 errors, 1 warnings\n",
            lintProject(
                    "unsafereceiver0.xml=>AndroidManifest.xml",
                    "res/values/strings.xml",
                    "bytecode/TestReceiver.java.txt=>src/test/pkg/TestReceiver.java",
                    "bytecode/TestReceiver.class.data=>bin/classes/test/pkg/TestReceiver.class"));
    }

    public void testBroken2() throws Exception {
        assertEquals(
                "AndroidManifest.xml:12: Warning: BroadcastReceivers that declare an " +
                "intent-filter for SMS_DELIVER or SMS_RECEIVED must ensure that the caller has " +
                "the BROADCAST_SMS permission, otherwise it is possible for malicious actors to " +
                "spoof intents. [UnprotectedSMSBroadcastReceiver]\n" +
                "        <receiver\n" +
                "        ^\n" +
                "0 errors, 1 warnings\n",
            lintProject(
                    "unsafereceiver1.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testCorrect() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        "safereceiver0.xml=>AndroidManifest.xml",
                        "res/values/strings.xml",
                        "bytecode/TestReceiver2.java.txt=>src/test/pkg/TestReceiver2.java",
                        "bytecode/TestReceiver2.class.data=>bin/classes/test/pkg/TestReceiver2.class"));
    }

    public void testCorrect2() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        "safereceiver1.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }
}
