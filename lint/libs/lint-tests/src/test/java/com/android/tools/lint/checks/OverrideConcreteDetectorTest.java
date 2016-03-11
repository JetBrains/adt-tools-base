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

public class OverrideConcreteDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new OverrideConcreteDetector();
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/OverrideConcreteTest.java:23: Error: Must override android.service.notification.NotificationListenerService.onNotificationPosted(android.service.notification.StatusBarNotification): Method was abstract until 21, and your minSdkVersion is 18 [OverrideAbstract]\n"
                + "    private static class MyNotificationListenerService2 extends NotificationListenerService {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/OverrideConcreteTest.java:30: Error: Must override android.service.notification.NotificationListenerService.onNotificationRemoved(android.service.notification.StatusBarNotification): Method was abstract until 21, and your minSdkVersion is 18 [OverrideAbstract]\n"
                + "    private static class MyNotificationListenerService3 extends NotificationListenerService {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/OverrideConcreteTest.java:37: Error: Must override android.service.notification.NotificationListenerService.onNotificationPosted(android.service.notification.StatusBarNotification): Method was abstract until 21, and your minSdkVersion is 18 [OverrideAbstract]\n"
                + "    private static class MyNotificationListenerService4 extends NotificationListenerService {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/OverrideConcreteTest.java:57: Error: Must override android.service.notification.NotificationListenerService.onNotificationRemoved(android.service.notification.StatusBarNotification): Method was abstract until 21, and your minSdkVersion is 18 [OverrideAbstract]\n"
                + "    private static class MyNotificationListenerService7 extends MyNotificationListenerService3 {\n"
                + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "4 errors, 0 warnings\n",

                lintProject(
                        java("src/test/pkg/OverrideConcreteTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.annotation.SuppressLint;\n"
                                + "import android.annotation.TargetApi;\n"
                                + "import android.os.Build;\n"
                                + "import android.service.notification.NotificationListenerService;\n"
                                + "import android.service.notification.StatusBarNotification;\n"
                                + "\n"
                                + "@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)\n"
                                + "public class OverrideConcreteTest {\n"
                                + "    // OK: This one specifies both methods\n"
                                + "    private static class MyNotificationListenerService1 extends NotificationListenerService {\n"
                                + "        @Override\n"
                                + "        public void onNotificationPosted(StatusBarNotification statusBarNotification) {\n"
                                + "        }\n"
                                + "\n"
                                + "        @Override\n"
                                + "        public void onNotificationRemoved(StatusBarNotification statusBarNotification) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    // Error: Misses onNotificationPosted\n"
                                + "    private static class MyNotificationListenerService2 extends NotificationListenerService {\n"
                                + "        @Override\n"
                                + "        public void onNotificationRemoved(StatusBarNotification statusBarNotification) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    // Error: Misses onNotificationRemoved\n"
                                + "    private static class MyNotificationListenerService3 extends NotificationListenerService {\n"
                                + "        @Override\n"
                                + "        public void onNotificationPosted(StatusBarNotification statusBarNotification) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    // Error: Missing both; wrong signatures (first has wrong arg count, second has wrong type)\n"
                                + "    private static class MyNotificationListenerService4 extends NotificationListenerService {\n"
                                + "        public void onNotificationPosted(StatusBarNotification statusBarNotification, int flags) {\n"
                                + "        }\n"
                                + "\n"
                                + "        public void onNotificationRemoved(int statusBarNotification) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    // OK: Inherits from a class which define both\n"
                                + "    private static class MyNotificationListenerService5 extends MyNotificationListenerService1 {\n"
                                + "    }\n"
                                + "\n"
                                + "    // OK: Inherits from a class which defines only one, but the other one is defined here\n"
                                + "    private static class MyNotificationListenerService6 extends MyNotificationListenerService3 {\n"
                                + "        @Override\n"
                                + "        public void onNotificationRemoved(StatusBarNotification statusBarNotification) {\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    // Error: Inheriting from a class which only defines one\n"
                                + "    private static class MyNotificationListenerService7 extends MyNotificationListenerService3 {\n"
                                + "    }\n"
                                + "\n"
                                + "    // OK: Has target api setting a local version that is high enough\n"
                                + "    @TargetApi(21)\n"
                                + "    private static class MyNotificationListenerService8 extends NotificationListenerService {\n"
                                + "    }\n"
                                + "\n"
                                + "    // OK: Suppressed\n"
                                + "    @SuppressLint(\"OverrideAbstract\")\n"
                                + "    private static class MyNotificationListenerService9 extends MyNotificationListenerService1 {\n"
                                + "    }\n"
                                + "}")));
    }
}
