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

import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.detector.api.Detector;

public class SupportAnnotationDetectorTest extends AbstractCheckTest {
    private static final boolean SDK_ANNOTATIONS_AVAILABLE =
            new SupportAnnotationDetectorTest().createClient().findResource(
            ExternalAnnotationRepository.SDK_ANNOTATIONS_PATH) != null;

    @Override
    protected Detector getDetector() {
        return new SupportAnnotationDetector();
    }

    public void testRange() throws Exception {
        assertEquals(""
                + "src/test/pkg/RangeTest.java:32: Error: Expected length 5 (was 4) [Range]\n"
                + "        printExact(\"1234\"); // ERROR\n"
                + "                   ~~~~~~\n"
                + "src/test/pkg/RangeTest.java:34: Error: Expected length 5 (was 6) [Range]\n"
                + "        printExact(\"123456\"); // ERROR\n"
                + "                   ~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:36: Error: Expected length ≥ 5 (was 4) [Range]\n"
                + "        printMin(\"1234\"); // ERROR\n"
                + "                 ~~~~~~\n"
                + "src/test/pkg/RangeTest.java:43: Error: Expected length ≤ 8 (was 9) [Range]\n"
                + "        printMax(\"123456789\"); // ERROR\n"
                + "                 ~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:45: Error: Expected length ≥ 4 (was 3) [Range]\n"
                + "        printRange(\"123\"); // ERROR\n"
                + "                   ~~~~~\n"
                + "src/test/pkg/RangeTest.java:49: Error: Expected length ≤ 6 (was 7) [Range]\n"
                + "        printRange(\"1234567\"); // ERROR\n"
                + "                   ~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:53: Error: Expected size 5 (was 4) [Range]\n"
                + "        printExact(new int[]{1, 2, 3, 4}); // ERROR\n"
                + "                   ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:55: Error: Expected size 5 (was 6) [Range]\n"
                + "        printExact(new int[]{1, 2, 3, 4, 5, 6}); // ERROR\n"
                + "                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:57: Error: Expected size ≥ 5 (was 4) [Range]\n"
                + "        printMin(new int[]{1, 2, 3, 4}); // ERROR\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:65: Error: Expected size ≤ 8 (was 9) [Range]\n"
                + "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}); // ERROR\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:67: Error: Expected size ≥ 4 (was 3) [Range]\n"
                + "        printRange(new int[] {1,2,3}); // ERROR\n"
                + "                   ~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:71: Error: Expected size ≤ 6 (was 7) [Range]\n"
                + "        printRange(new int[] {1,2,3,4,5,6,7}); // ERROR\n"
                + "                   ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:74: Error: Expected size to be a multiple of 3 (was 4 and should be either 3 or 6) [Range]\n"
                + "        printMultiple(new int[] {1,2,3,4}); // ERROR\n"
                + "                      ~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:75: Error: Expected size to be a multiple of 3 (was 5 and should be either 3 or 6) [Range]\n"
                + "        printMultiple(new int[] {1,2,3,4,5}); // ERROR\n"
                + "                      ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:77: Error: Expected size to be a multiple of 3 (was 7 and should be either 6 or 9) [Range]\n"
                + "        printMultiple(new int[] {1,2,3,4,5,6,7}); // ERROR\n"
                + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:80: Error: Expected size ≥ 4 (was 3) [Range]\n"
                + "        printMinMultiple(new int[]{1, 2, 3}); // ERROR\n"
                + "                         ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/RangeTest.java:84: Error: Value must be ≥ 4 (was 3) [Range]\n"
                + "        printAtLeast(3); // ERROR\n"
                + "                     ~\n"
                + "src/test/pkg/RangeTest.java:91: Error: Value must be ≤ 7 (was 8) [Range]\n"
                + "        printAtMost(8); // ERROR\n"
                + "                    ~\n"
                + "src/test/pkg/RangeTest.java:93: Error: Value must be ≥ 4 (was 3) [Range]\n"
                + "        printBetween(3); // ERROR\n"
                + "                     ~\n"
                + "src/test/pkg/RangeTest.java:98: Error: Value must be ≤ 7 (was 8) [Range]\n"
                + "        printBetween(8); // ERROR\n"
                + "                     ~\n"
                + "src/test/pkg/RangeTest.java:102: Error: Value must be ≥ 2.5 (was 2.49) [Range]\n"
                + "        printAtLeastInclusive(2.49f); // ERROR\n"
                + "                              ~~~~~\n"
                + "src/test/pkg/RangeTest.java:106: Error: Value must be > 2.5 (was 2.49) [Range]\n"
                + "        printAtLeastExclusive(2.49f); // ERROR\n"
                + "                              ~~~~~\n"
                + "src/test/pkg/RangeTest.java:107: Error: Value must be > 2.5 (was 2.5) [Range]\n"
                + "        printAtLeastExclusive(2.5f); // ERROR\n"
                + "                              ~~~~\n"
                + "src/test/pkg/RangeTest.java:113: Error: Value must be ≤ 7.0 (was 7.1) [Range]\n"
                + "        printAtMostInclusive(7.1f); // ERROR\n"
                + "                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:117: Error: Value must be < 7.0 (was 7.0) [Range]\n"
                + "        printAtMostExclusive(7.0f); // ERROR\n"
                + "                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:118: Error: Value must be < 7.0 (was 7.1) [Range]\n"
                + "        printAtMostExclusive(7.1f); // ERROR\n"
                + "                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:120: Error: Value must be ≥ 2.5 (was 2.4) [Range]\n"
                + "        printBetweenFromInclusiveToInclusive(2.4f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:124: Error: Value must be ≤ 5.0 (was 5.1) [Range]\n"
                + "        printBetweenFromInclusiveToInclusive(5.1f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:126: Error: Value must be > 2.5 (was 2.4) [Range]\n"
                + "        printBetweenFromExclusiveToInclusive(2.4f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:127: Error: Value must be > 2.5 (was 2.5) [Range]\n"
                + "        printBetweenFromExclusiveToInclusive(2.5f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:129: Error: Value must be ≤ 5.0 (was 5.1) [Range]\n"
                + "        printBetweenFromExclusiveToInclusive(5.1f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:131: Error: Value must be ≥ 2.5 (was 2.4) [Range]\n"
                + "        printBetweenFromInclusiveToExclusive(2.4f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:135: Error: Value must be < 5.0 (was 5.0) [Range]\n"
                + "        printBetweenFromInclusiveToExclusive(5.0f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:137: Error: Value must be > 2.5 (was 2.4) [Range]\n"
                + "        printBetweenFromExclusiveToExclusive(2.4f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:138: Error: Value must be > 2.5 (was 2.5) [Range]\n"
                + "        printBetweenFromExclusiveToExclusive(2.5f); // ERROR\n"
                + "                                             ~~~~\n"
                + "src/test/pkg/RangeTest.java:141: Error: Value must be < 5.0 (was 5.0) [Range]\n"
                + "        printBetweenFromExclusiveToExclusive(5.0f); // ERROR\n"
                + "                                             ~~~~\n"
                + "36 errors, 0 warnings\n",

                lintProject("src/test/pkg/RangeTest.java.txt=>src/test/pkg/RangeTest.java",
                        "src/android/support/annotation/Size.java.txt=>src/android/support/annotation/Size.java",
                        "src/android/support/annotation/IntRange.java.txt=>src/android/support/annotation/IntRange.java",
                        "src/android/support/annotation/FloatRange.java.txt=>src/android/support/annotation/FloatRange.java"
                ));
    }

    public void testTypeDef() throws Exception {
        assertEquals(""
                + "src/test/pkg/IntDefTest.java:31: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setStyle(0, 0); // ERROR\n"
                + "                 ~\n"
                + "src/test/pkg/IntDefTest.java:32: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setStyle(-1, 0); // ERROR\n"
                + "                 ~~\n"
                + "src/test/pkg/IntDefTest.java:33: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setStyle(UNRELATED, 0); // ERROR\n"
                + "                 ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:34: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setStyle(IntDefTest.UNRELATED, 0); // ERROR\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:35: Error: Flag not allowed here [WrongConstant]\n"
                + "        setStyle(IntDefTest.STYLE_NORMAL|STYLE_NO_FRAME, 0); // ERROR: Not a flag\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:36: Error: Flag not allowed here [WrongConstant]\n"
                + "        setStyle(~STYLE_NO_FRAME, 0); // ERROR: Not a flag\n"
                + "                 ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:55: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", UNRELATED); // ERROR\n"
                + "                     ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:56: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", UNRELATED|STYLE_NO_TITLE); // ERROR\n"
                + "                     ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:57: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|UNRELATED); // ERROR\n"
                + "                                                 ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:58: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", 1); // ERROR\n"
                + "                     ~\n"
                + "src/test/pkg/IntDefTest.java:59: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : UNRELATED); // ERROR\n"
                + "                                              ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:60: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", arg < 0 ? UNRELATED : STYLE_NORMAL); // ERROR\n"
                + "                               ~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:79: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n"
                + "        setTitle(\"\", UNRELATED_TYPE); // ERROR\n"
                + "                     ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:80: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n"
                + "        setTitle(\"\", \"type2\"); // ERROR\n"
                + "                     ~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:87: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n"
                + "        setTitle(\"\", type); // ERROR\n"
                + "                     ~~~~\n"
                + "src/test/pkg/IntDefTest.java:92: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n"
                + "        setFlags(\"\", flag); // ERROR\n"
                + "                     ~~~~\n"
                + (SDK_ANNOTATIONS_AVAILABLE ?
                "src/test/pkg/IntDefTest.java:99: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCAL [WrongConstant]\n"
                + "        view.setLayoutDirection(View.TEXT_DIRECTION_LTR); // ERROR\n"
                + "                                ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:100: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCAL [WrongConstant]\n"
                + "        view.setLayoutDirection(0); // ERROR\n"
                + "                                ~\n"
                + "src/test/pkg/IntDefTest.java:101: Error: Flag not allowed here [WrongConstant]\n"
                + "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR|View.LAYOUT_DIRECTION_RTL); // ERROR\n"
                + "                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/IntDefTest.java:102: Error: Must be one of: Context.POWER_SERVICE, Context.WINDOW_SERVICE, Context.LAYOUT_INFLATER_SERVICE, Context.ACCOUNT_SERVICE, Context.ACTIVITY_SERVICE, Context.ALARM_SERVICE, Context.NOTIFICATION_SERVICE, Context.ACCESSIBILITY_SERVICE, Context.CAPTIONING_SERVICE, Context.KEYGUARD_SERVICE, Context.LOCATION_SERVICE, Context.SEARCH_SERVICE, Context.SENSOR_SERVICE, Context.STORAGE_SERVICE, Context.WALLPAPER_SERVICE, Context.VIBRATOR_SERVICE, Context.CONNECTIVITY_SERVICE, Context.WIFI_SERVICE, Context.WIFI_P2P_SERVICE, Context.NSD_SERVICE, Context.AUDIO_SERVICE, Context.MEDIA_ROUTER_SERVICE, Context.TELEPHONY_SERVICE, Context.TELECOM_SERVICE, Context.CLIPBOARD_SERVICE, Context.INPUT_METHOD_SERVICE, Context.TEXT_SERVICES_MANAGER_SERVICE, Context.APPWIDGET_SERVICE, Context.DROPBOX_SERVICE, Context.DEVICE_POLICY_SERVICE, Context.UI_MODE_SERVICE, Context.DOWNLOAD_SERVICE, Context.NFC_SERVICE, Context.BLUETOOTH_SERVICE, Context.USB_SERVICE, Context.LAUNCHER_APPS_SERVICE, Context.INPUT_SERVICE, Context.DISPLAY_SERVICE, Context.USER_SERVICE, Context.RESTRICTIONS_SERVICE, Context.APP_OPS_SERVICE, Context.CAMERA_SERVICE, Context.PRINT_SERVICE, Context.CONSUMER_IR_SERVICE, Context.TV_INPUT_SERVICE, Context.MEDIA_SESSION_SERVICE, Context.BATTERY_SERVICE, Context.JOB_SCHEDULER_SERVICE, Context.MEDIA_PROJECTION_SERVIC [WrongConstant]\n"
                + "        context.getSystemService(TYPE_1); // ERROR\n"
                + "                                 ~~~~~~\n"
                + "20 errors, 0 warnings\n" :
                "16 errors, 0 warnings\n"),

                lintProject("src/test/pkg/IntDefTest.java.txt=>src/test/pkg/IntDefTest.java",
                        "src/android/support/annotation/IntDef.java.txt=>src/android/support/annotation/IntDef.java",
                        "src/android/support/annotation/StringDef.java.txt=>src/android/support/annotation/StringDef.java"
                ));
    }

    public void testColorInt() throws Exception {
        // Needs updated annotations!
        assertEquals((SDK_ANNOTATIONS_AVAILABLE ? ""
                + "src/test/pkg/WrongColor.java:9: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]\n"
                + "        paint2.setColor(R.color.blue);\n"
                + "                        ~~~~~~~~~~~~\n"
                + "src/test/pkg/WrongColor.java:11: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.red) [ResourceAsColor]\n"
                + "        textView.setTextColor(R.color.red);\n"
                + "                              ~~~~~~~~~~~\n"
                + "src/test/pkg/WrongColor.java:12: Error: Should pass resolved color instead of resource id here: getResources().getColor(android.R.color.black) [ResourceAsColor]\n"
                + "        textView.setTextColor(android.R.color.black);\n"
                + "                              ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/WrongColor.java:13: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]\n"
                + "        textView.setTextColor(foo > 0 ? R.color.green : R.color.blue);\n"
                + "                                                        ~~~~~~~~~~~~\n"
                + "src/test/pkg/WrongColor.java:13: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.green) [ResourceAsColor]\n"
                + "        textView.setTextColor(foo > 0 ? R.color.green : R.color.blue);\n"
                + "                                        ~~~~~~~~~~~~~\n" : "")
                + "src/test/pkg/WrongColor.java:21: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]\n"
                + "        foo2(R.color.blue);\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/WrongColor.java:20: Error: Expected resource of type color [ResourceType]\n"
                + "        foo1(0xffff0000);\n"
                + "             ~~~~~~~~~~\n"
                + (SDK_ANNOTATIONS_AVAILABLE ? "7 errors, 0 warnings\n" : "2 errors, 0 warnings\n"),

                lintProject(
                        "src/test/pkg/WrongColor.java.txt=>src/test/pkg/WrongColor.java",
                        "src/android/support/annotation/ColorInt.java.txt=>src/android/support/annotation/ColorInt.java"
                ));
    }

    public void testResourceType() throws Exception {
        assertEquals((SDK_ANNOTATIONS_AVAILABLE ? ""
                + "src/p1/p2/Flow.java:13: Error: Expected resource of type drawable [ResourceType]\n"
                + "        resources.getDrawable(10); // ERROR\n"
                + "                              ~~\n"
                + "src/p1/p2/Flow.java:18: Error: Expected resource of type drawable [ResourceType]\n"
                + "        resources.getDrawable(R.string.my_string); // ERROR\n"
                + "                              ~~~~~~~~~~~~~~~~~~\n" : "")
                + "src/p1/p2/Flow.java:22: Error: Expected resource of type drawable [ResourceType]\n"
                + "        myMethod(R.string.my_string); // ERROR\n"
                + "                 ~~~~~~~~~~~~~~~~~~\n"
                + "src/p1/p2/Flow.java:32: Error: Expected resource identifier (R.type.name) [ResourceType]\n"
                + "        myAnyResMethod(50); // ERROR\n"
                + "                       ~~\n"
                + "src/p1/p2/Flow.java:68: Error: Expected resource of type drawable [ResourceType]\n"
                + "        myMethod(z); // ERROR\n"
                + "                 ~\n"
                + (SDK_ANNOTATIONS_AVAILABLE ? "5 errors, 0 warnings\n" : "3 errors, 0 warnings\n"),

                lintProject("src/p1/p2/Flow.java.txt=>src/p1/p2/Flow.java",
                        "src/android/support/annotation/DrawableRes.java.txt=>src/android/support/annotation/DrawableRes.java"));
    }

    public void testColorAsDrawable() throws Exception {
        assertEquals(
                "No warnings.",

                lintProject("src/p1/p2/ColorAsDrawable.java.txt=>src/p1/p2/ColorAsDrawable.java"));
    }

    public void testCheckResult() throws Exception {
        if (!SDK_ANNOTATIONS_AVAILABLE) {
            // Currently only tests @CheckResult on SDK annotations
            return;
        }
        assertEquals(""
                + "src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]\n"
                + "        bitmap.extractAlpha(); // WARNING\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CheckPermissions.java:10: Warning: The result of checkCallingOrSelfPermission is not used; did you mean to call #enforceCallingOrSelfPermission(String,String? [UseCheckPermission]\n"
                + "        context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/CheckPermissions.java:11: Warning: The result of checkPermission is not used; did you mean to call #enforcePermission(String,int,int,String? [UseCheckPermission]\n"
                + "        context.checkPermission(Manifest.permission.INTERNET, 1, 1);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 3 warnings\n",

                lintProject("src/test/pkg/CheckPermissions.java.txt=>src/test/pkg/CheckPermissions.java"));
    }
}
