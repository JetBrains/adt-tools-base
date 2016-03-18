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

import static com.android.SdkConstants.FD_JAVA;
import static com.android.tools.lint.checks.StringAuthLeakDetector.AUTH_LEAK;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class StringAuthLeakDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new StringAuthLeakDetector();
    }

    public void testStringAuthLeak() throws Exception {
        String expected = "src/test/pkg/AuthDemo.java:2: Warning: Possible credential leak [AuthLeak]\n"
                + "  private static final String AUTH_IP = \"scheme://user:pwd@127.0.0.1:8000\";\n"
                + "                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(java("src/test/pkg/AuthDemo.java", ""
                + "public class AuthDemo {\n"
                + "  private static final String AUTH_IP = \"scheme://user:pwd@127.0.0.1:8000\";\n"
                + "  private static final String AUTH_NO_LEAK = \"scheme://user:%s@www.google.com\";\n"
                + "}\n"));
        assertEquals(expected, result);
    }
}
