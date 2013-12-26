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
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

public class CheckPermissionDetectorTest extends AbstractCheckTest {
  @Override
  protected Detector getDetector() {
    return new CheckPermissionDetector();
  }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/CheckPermissions.java:7: Warning: The result of checkCallingOrSelfPermission is not used; did you mean to call enforceCallingOrSelfPermission? [UseCheckPermission]\n"
                + "      context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG\n"
                + "      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject("src/test/pkg/CheckPermissions.java.txt=>" +
                        "src/test/pkg/CheckPermissions.java"));
    }
}
