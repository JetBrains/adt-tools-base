/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MultiProjectHtmlReporterTest extends AbstractCheckTest {
    public void test() throws Exception {
        File dir = new File(getTargetDir(), "report");
        try {
            LintCliClient client = new LintCliClient() {
                @Override
                IssueRegistry getRegistry() {
                    if (mRegistry == null) {
                        mRegistry = new IssueRegistry()  {
                            @NonNull
                            @Override
                            public List<Issue> getIssues() {
                                return Arrays.asList(
                                        ManifestDetector.USES_SDK,
                                        HardcodedValuesDetector.ISSUE,
                                        // Not reported, but for the disabled-list
                                        ManifestDetector.MOCK_LOCATION);
                            }
                        };
                    }
                    return mRegistry;
                }
            };

            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            MultiProjectHtmlReporter reporter = new MultiProjectHtmlReporter(client, dir);
            Project project = Project.create(client, new File("/foo/bar/Foo"),
                    new File("/foo/bar/Foo"));

            Warning warning1 = new Warning(ManifestDetector.USES_SDK,
                    "<uses-sdk> tag should specify a target API level (the highest verified " +
                            "version; when running on later versions, compatibility behaviors may " +
                            "be enabled) with android:targetSdkVersion=\"?\"",
                    Severity.WARNING, project);
            warning1.line = 6;
            warning1.file = new File("/foo/bar/Foo/AndroidManifest.xml");
            warning1.errorLine = "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n";
            warning1.path = "AndroidManifest.xml";
            warning1.location = Location.create(warning1.file,
                    new DefaultPosition(6, 4, 198), new DefaultPosition(6, 42, 236));

            Warning warning2 = new Warning(HardcodedValuesDetector.ISSUE,
                    "[I18N] Hardcoded string \"Fooo\", should use @string resource",
                    Severity.WARNING, project);
            warning2.line = 11;
            warning2.file = new File("/foo/bar/Foo/res/layout/main.xml");
            warning2.errorLine = " (java.lang.String)         android:text=\"Fooo\" />\n" +
                    "        ~~~~~~~~~~~~~~~~~~~\n";
            warning2.path = "res/layout/main.xml";
            warning2.location = Location.create(warning2.file,
                    new DefaultPosition(11, 8, 377), new DefaultPosition(11, 27, 396));

            List<Warning> warnings = new ArrayList<Warning>();
            warnings.add(warning1);
            warnings.add(warning2);

            reporter.write(0, 2, warnings);

            String report = Files.toString(new File(dir, "index.html"), Charsets.UTF_8);

            // Replace the timestamp to make golden file comparison work
            String timestampPrefix = "Check performed at ";
            int begin = report.indexOf(timestampPrefix);
            assertTrue(begin != -1);
            begin += timestampPrefix.length();
            int end = report.indexOf(".<br/>", begin);
            assertTrue(end != -1);
            report = report.substring(0, begin) + "$DATE" + report.substring(end);

            // Not intended to be user configurable; we'll remove the old support soon
            assertTrue("This test is hardcoded for inline resource mode",
                    HtmlReporter.INLINE_RESOURCES);

            // NOTE: If you change the output, please validate it manually in
            //  http://validator.w3.org/#validate_by_input
            // before updating the following
            assertEquals(""
                    + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                    + "<head>\n"
                    + "<title>Lint Report</title>\n"
                    + "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://fonts.googleapis.com/css?family=Roboto\" />\n"
                    + "<style>\n"
                    + "body {\n"
                    + "    max-width: 800px;\n"
                    + "    background-color: #000000;\n"
                    + "    background: -webkit-gradient(linear, left top, right bottom, from(#000000), to(#272d33));\n"
                    + "    background: -moz-linear-gradient(left top, #000000, #272d33);\n"
                    + "    color: #f3f3f3;\n"
                    + "    font-family: 'Roboto', Sans-Serif;\n"
                    + "}\n"
                    + ".issue {\n"
                    + "    margin-top: 10px;\n"
                    + "    margin-bottom: 10px;\n"
                    + "    padding: 5px 0px 5px 5px;\n"
                    + "}\n"
                    + ".id {\n"
                    + "    font-size: 14pt;\n"
                    + "    color: #bebebe;\n"
                    + "    margin: 5px 0px 5px 0px;\n"
                    + "}\n"
                    + ".category {\n"
                    + "    font-size: 18pt;\n"
                    + "    color: #bebebe;\n"
                    + "    margin: 10px 0px 5px 0px;\n"
                    + "}\n"
                    + ".explanation {\n"
                    + "    margin-top: 10px;\n"
                    + "}\n"
                    + ".explanation b {\n"
                    + "    color: #ffbbbb;\n"
                    + "}\n"
                    + ".explanation code {\n"
                    + "    color: #bebebe;\n"
                    + "    font-family: 'Roboto', Sans-Serif;\n"
                    + "}\n"
                    + "pre {\n"
                    + "    background-color: #282828;\n"
                    + "    margin: 5px 0px 5px 5px;\n"
                    + "    padding: 5px 5px 5px 0px;\n"
                    + "    overflow: hidden;\n"
                    + "}\n"
                    + ".lineno {\n"
                    + "    color: #4f4f4f;\n"
                    + "}\n"
                    + ".embedimage {\n"
                    + "    max-width: 200px;\n"
                    + "    max-height: 200px;\n"
                    + "}\n"
                    + "th { font-weight: normal; }\n"
                    + "table { border: none; }\n"
                    + ".metadata {\n"
                    + "}\n"
                    + ".location {\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".message { }\n"
                    + ".errorspan { color: #33b5e5; }\n"
                    + ".errorline { color: #33b5e5; }\n"
                    + ".warningslist { margin-bottom: 20px; }\n"
                    + ".overview {\n"
                    + "    padding: 10pt;\n"
                    + "    width: 100%;\n"
                    + "    overflow: auto;\n"
                    + "    border-collapse:collapse;\n"
                    + "}\n"
                    + ".overview tr {\n"
                    + "    border-top: solid 1px #39393a;\n"
                    + "    border-bottom: solid 1px #39393a;\n"
                    + "}\n"
                    + ".countColumn {\n"
                    + "    text-align: right;\n"
                    + "    padding-right: 20px;\n"
                    + "}\n"
                    + ".issueColumn {\n"
                    + "   padding-left: 16px;\n"
                    + "}\n"
                    + ".categoryColumn {\n"
                    + "   position: relative;\n"
                    + "   left: -50px;\n"
                    + "   padding-top: 20px;\n"
                    + "   padding-bottom: 5px;\n"
                    + "}\n"
                    + ".titleSeparator {\n"
                    + "    background-color: #33b5e5;\n"
                    + "    height: 3px;\n"
                    + "    margin-bottom: 10px;\n"
                    + "}\n"
                    + ".categorySeparator {\n"
                    + "    background-color: #33b5e5;\n"
                    + "    height: 3px;\n"
                    + "    margin-bottom: 10px;\n"
                    + "}\n"
                    + ".issueSeparator {\n"
                    + "    background-color: #39393a;\n"
                    + "    height: 2px;\n"
                    + "    margin-bottom: 10px;\n"
                    + "}\n"
                    + ".location a:link {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".location a:hover {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + "a:link {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + "a:visited {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + "a:hover {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + "a:active {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + ".moreinfo a:link {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #33b5e5;\n"
                    + "}\n"
                    + ".moreinfo a:visited {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #33b5e5;\n"
                    + "}\n"
                    + ".issue a:link {\n"
                    + "    text-decoration: underline;\n"
                    + "}\n"
                    + ".issue a:visited {\n"
                    + "    text-decoration: underline;\n"
                    + "}\n"
                    + ".id a:link {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".id a:visited {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".id a:hover {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + ".id a:active {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".category a:link {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".category a:visited {\n"
                    + "    text-decoration: none;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + ".category a:hover {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #f3f3f3;\n"
                    + "}\n"
                    + ".category a:active {\n"
                    + "    text-decoration: underline;\n"
                    + "    color: #bebebe;\n"
                    + "}\n"
                    + "button {\n"
                    + "    color: #ffffff;\n"
                    + "    background-color: #353535;\n"
                    + "    border-left: none;\n"
                    + "    border-right: none;\n"
                    + "    border-bottom: none;\n"
                    + "    border-top: solid 1px #5b5b5b;\n"
                    + "    font-family: 'Roboto', Sans-Serif;\n"
                    + "    font-size: 12pt;\n"
                    + "}\n"
                    + "</style>\n"
                    + "</head>\n"
                    + "<body>\n"
                    + "<h1>Lint Report</h1>\n"
                    + "<div class=\"titleSeparator\"></div>\n"
                    + "Check performed at $DATE.<br/>\n"
                    + "0 errors and 2 warnings found:\n"
                    + "<br/><br/>\n"
                    + "<table class=\"overview\">\n"
                    + "<tr><th>Project</th><th class=\"countColumn\"><img border=\"0\" align=\"top\" width=\"15\" height=\"15\" alt=\"Error\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA8AAAAPCAYAAAA71pVKAAAB00lEQVR42nWTS0hbQRiF587MzkUXooi6UHAjhNKNSkGhCDXxkZhUIwWhBLoRsQpi3bXmIboSV2aliI+WKqLUtqsuSxclrhRBUMnVmIpa2oIkQon+zhlr9F7jwOEw/znfLO6dYcy2Arys6AUv6x7klTNh4ViFY485u2+N8Uc8yB1DH0Vt6ki2UkZ20LkS/Eh6CXPk6FnAKVHNJ3nViind9E/6tTKto3TxaU379Qw5euhn4QXxOGzKFjqT7Vmlwx8IC357jh76GvzC64pj4mn6VLbRbf0Nvdcw3J6hr7gS9o3XDxwIN/0RPot+h95pGG7P0AfH1oVz6UR4ya5foXkNw3Pl4Ngub/p6yD1k13FoTsPwXDk4ti89SwnuJrtigYiGY4FhypWDY2aeb0CJ4rzZou9GPc0Y1drtGfrgWLzweUm8uPNsx2ikrHgjHT6LUOrzD/rpDpIlU0JfcaX6d8UfdoW38/20ZbiuxF10MHL1tRNvp2/mSuihn70kZl2/MJ+8Xtkq8NOm4VRqoIUKLy0Hx2mx3PN/5iTk6KFvuaJmyxux3zE8tFPTm9p84KMNdcAGa9COvZqnkaN37wNJvpooSvZFexIvx2b3OkdX4dgne6N3XtUl5wqoyBY2uZQAAAAASUVORK5CYII=\" />\n"
                    + "Errors</th><th class=\"countColumn\"><img border=\"0\" align=\"top\" width=\"16\" height=\"15\" alt=\"Warning\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAPCAQAAABHeoekAAAA3klEQVR42nWPsWoCQRCGVyJiF9tAsNImbcDKR/ABBEurYCsBsfQRQiAPYGPyAAnYWQULS9MErNU2Vsr/ObMX7g6O+xd2/5n5dmY3hFQEBVVpuCsVT/yoUl6u4XotBz4E4qR2YYyH6ugEWY8comR/t+tvPPJtSLPYvhvvTswtbdCmCOwjMHXAzjP9kB/ByB7nejbgy43WVPF3WNG+p9+kzkozdhGAQdZh7BlHdGTL3z98pp6Um7okKdvHNuIzWk+9xN+yINOcHps0OnAfuOOoHJH3pmHghhYP2VJcaXx7BaKz9YB2HVrDAAAAAElFTkSuQmCC\"/>\n"
                    + "Warnings</th></tr>\n"
                    + "<tr><td><a href=\"Foo.html\">Foo</a></td><td class=\"countColumn\">0</td><td class=\"countColumn\">2</td></tr>\n"
                    + "</table>\n"
                    + "</body>\n"
                    + "</html>\n",
                    report);

            if (!HtmlReporter.INLINE_RESOURCES) {
                assertTrue(new File(dir, "index_files" + File.separator + "hololike.css").exists());
                assertTrue(new File(dir, "index_files" + File.separator + "lint-warning.png").exists());
                assertTrue(new File(dir, "index_files" + File.separator + "lint-error.png").exists());
                assertTrue(new File(dir, "Foo.html").exists());
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        }
    }

    @Override
    protected Detector getDetector() {
        fail("Not used in this test");
        return null;
    }
}
