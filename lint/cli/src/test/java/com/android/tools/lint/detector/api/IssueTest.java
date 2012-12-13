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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.lint.detector.api.Issue.convertMarkup;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class IssueTest extends TestCase {
    public void testConvertMarkup() throws Exception {
            assertEquals("", convertMarkup("", true));

            // Normal escapes
            assertEquals("foo bar", convertMarkup("foo bar", true));
            assertEquals("foo<br/>\nbar", convertMarkup("foo\nbar", true));
            assertEquals("foo<br/>\nbar", convertMarkup("foo\nbar", true));
            assertEquals("&lt;&amp;>'\"", convertMarkup("<&>'\"", true));

            // HTML Formatting
            assertEquals("<code>@TargetApi(11)</code>, ", convertMarkup("`@TargetApi(11)`, ",
                    true));
            assertEquals("with <code>getArguments()</code>.",
                    convertMarkup("with `getArguments()`.",
                    true));
            assertEquals("(<code>dip</code>)", convertMarkup("(`dip`)", true));
            assertEquals(" <code>0dp</code> ", convertMarkup(" `0dp` ", true));
            assertEquals(
                "resources under <code>$ANDROID_SK/platforms/android-$VERSION/data/res/.</code>",
                    convertMarkup(
                            "resources under `$ANDROID_SK/platforms/android-$VERSION/data/res/.`",
                            true));
            assertEquals("wrong format. Instead of <code>-keepclasseswithmembernames</code> use ",
                    convertMarkup("wrong format. Instead of `-keepclasseswithmembernames` use ",
                            true));
            assertEquals("<code>exported=false</code>)", convertMarkup("`exported=false`)",
                    true));
            assertEquals("by setting <code>inputType=\"text\"</code>.",
                    convertMarkup("by setting `inputType=\"text\"`.", true));
            assertEquals("* <code>View(Context context)</code><br/>\n",
                    convertMarkup("* `View(Context context)`\n", true));
            assertEquals("The <code>@+id/</code> syntax", convertMarkup("The `@+id/` syntax",
                    true));
            assertEquals("", convertMarkup("", true));
            assertEquals("", convertMarkup("", true));
            assertEquals("This is <b>bold</b>", convertMarkup("This is *bold*", true));
            assertEquals("Visit <a href=\"http://google.com\">http://google.com</a>.",
                    convertMarkup("Visit http://google.com.", true));
            assertEquals("This is <code>monospace</code>!", convertMarkup("This is `monospace`!",
                    true));
            assertEquals(
                    "See <a href=\"http://developer.android.com/reference/android/view/" +
                    "WindowManager.LayoutParams.html#FLAG_KEEP_SCREEN_ON\">http://developer." +
                    "android.com/reference/android/view/WindowManager.LayoutParams.html#" +
                    "FLAG_KEEP_SCREEN_ON</a>.",
                convertMarkup(
                  "See http://developer.android.com/reference/android/view/WindowManager.Layout" +
                  "Params.html#FLAG_KEEP_SCREEN_ON.", true));

            // Text formatting
            assertEquals("@TargetApi(11), ", convertMarkup("`@TargetApi(11)`, ", false));
            assertEquals("with getArguments().", convertMarkup("with `getArguments()`.", false));
            assertEquals("bold", convertMarkup("*bold*", false));
            assertEquals("Visit http://google.com.", convertMarkup("Visit http://google.com.",
                    false));

            // Corners (match at the beginning and end)
            assertEquals("<b>bold</b>", convertMarkup("*bold*", true));
            assertEquals("<code>monospace</code>!", convertMarkup("`monospace`!", true));

            // Not formatting
            assertEquals("a*b", convertMarkup("a*b", true));
            assertEquals("a* b*", convertMarkup("a* b*", true));
            assertEquals("*a *b", convertMarkup("*a *b", true));
            assertEquals("Prefix is http:// ", convertMarkup("Prefix is http:// ", true));
            assertEquals("", convertMarkup("", true));
            assertEquals("", convertMarkup("", true));
            assertEquals("", convertMarkup("", true));
            assertEquals("", convertMarkup("", true));
            assertEquals("This is * not * bold", convertMarkup("This is * not * bold", true));
            assertEquals("* List item 1<br/>\n* List Item 2",
                    convertMarkup("* List item 1\n* List Item 2", true));
            assertEquals("myhttp://foo.bar", convertMarkup("myhttp://foo.bar", true));
        }

    public void testConvertMarkup2() throws Exception {
        // http at the end:
        // Explanation from ManifestOrderDetector#TARGET_NEWER
        String explanation =
            "When your application runs on a version of Android that is more recent than your " +
            "targetSdkVersion specifies that it has been tested with, various compatibility " +
            "modes kick in. This ensures that your application continues to work, but it may " +
            "look out of place. For example, if the targetSdkVersion is less than 14, your " +
            "app may get an option button in the UI.\n" +
            "\n" +
            "To fix this issue, set the targetSdkVersion to the highest available value. Then " +
            "test your app to make sure everything works correctly. You may want to consult " +
            "the compatibility notes to see what changes apply to each version you are adding " +
            "support for: " +
            "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html";

        assertEquals(
            "When your application runs on a version of Android that is more recent than your " +
            "targetSdkVersion specifies that it has been tested with, various compatibility " +
            "modes kick in. This ensures that your application continues to work, but it may " +
            "look out of place. For example, if the targetSdkVersion is less than 14, your " +
            "app may get an option button in the UI.<br/>\n" +
            "<br/>\n" +
            "To fix this issue, set the targetSdkVersion to the highest available value. Then " +
            "test your app to make sure everything works correctly. You may want to consult " +
            "the compatibility notes to see what changes apply to each version you are adding " +
            "support for: " +
            "<a href=\"http://developer.android.com/reference/android/os/Build.VERSION_CODES." +
            "html\">http://developer.android.com/reference/android/os/Build.VERSION_CODES.html" +
            "</a>",
            convertMarkup(explanation, true));
    }

    public void testConvertMarkup3() throws Exception {
        // embedded http markup test
        // Explanation from NamespaceDetector#CUSTOMVIEW
        String explanation =
            "When using a custom view with custom attributes in a library project, the layout " +
            "must use the special namespace " + AUTO_URI + " instead of a URI which includes " +
            "the library project's own package. This will be used to automatically adjust the " +
            "namespace of the attributes when the library resources are merged into the " +
            "application project.";
        assertEquals(
            "When using a custom view with custom attributes in a library project, the layout " +
            "must use the special namespace " +
            "<a href=\"http://schemas.android.com/apk/res-auto\">" +
            "http://schemas.android.com/apk/res-auto</a> " +
            "instead of a URI which includes the library project's own package. " +
            "This will be used to automatically adjust the namespace of the attributes when " +
            "the library resources are merged into the application project.",
            convertMarkup(explanation, true));
    }

    public void testConvertMarkup4() throws Exception {
        // monospace test
        String explanation =
            "The manifest should contain a `<uses-sdk>` element which defines the " +
            "minimum minimum API Level required for the application to run, " +
            "as well as the target version (the highest API level you have tested " +
            "the version for.)";

        assertEquals(
            "The manifest should contain a <code>&lt;uses-sdk></code> element which defines the " +
            "minimum minimum API Level required for the application to run, " +
            "as well as the target version (the highest API level you have tested " +
            "the version for.)",
            convertMarkup(explanation, true));
    }

    public void testConvertMarkup5() throws Exception {
        // monospace and bold test
        // From ManifestOrderDetector#MULTIPLE_USES_SDK
        String explanation =
            "The `<uses-sdk>` element should appear just once; the tools will *not* merge the " +
            "contents of all the elements so if you split up the atttributes across multiple " +
            "elements, only one of them will take effect. To fix this, just merge all the " +
            "attributes from the various elements into a single <uses-sdk> element.";

        assertEquals(
            "The <code>&lt;uses-sdk></code> element should appear just once; the tools " +
            "will <b>not</b> merge the " +
            "contents of all the elements so if you split up the atttributes across multiple " +
            "elements, only one of them will take effect. To fix this, just merge all the " +
            "attributes from the various elements into a single &lt;uses-sdk> element.",
            convertMarkup(explanation, true));
        }

    public void testConvertMarkup6() throws Exception {
        // Embedded code next to attributes
        // From AlwaysShowActionDetector#ISSUE
        String explanation =
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "+
            "Java code is usually a deviation from the user interface style guide." +
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n" +
            "\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n" +
            "\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.";

        assertEquals(
            "Using <code>showAsAction=\"always\"</code> in menu XML, or " +
            "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> in Java code is usually a deviation " +
            "from the user interface style guide.Use <code>ifRoom</code> or the " +
            "corresponding <code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code> instead.<br/>\n" +
            "<br/>\n" +
            "If <code>always</code> is used sparingly there are usually no problems and " +
            "behavior is roughly equivalent to <code>ifRoom</code> but with preference over " +
            "other <code>ifRoom</code> items. Using it more than twice in the same menu " +
            "is a bad idea.<br/>\n" +
            "<br/>\n" +
            "This check looks for menu XML files that contain more than two <code>always</code> " +
            "actions, or some <code>always</code> actions and no <code>ifRoom</code> actions. " +
            "In Java code, it looks for projects that contain references to " +
            "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> and no references to " +
            "<code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code>.",
            convertMarkup(explanation, true));
    }
}
