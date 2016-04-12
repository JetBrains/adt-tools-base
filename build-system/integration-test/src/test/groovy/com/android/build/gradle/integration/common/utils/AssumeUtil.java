/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.repository.Revision;

import org.junit.Assume;

/**
 * Common assume for test.
 */
public class AssumeUtil {

    public static void assumeBuildToolsAtLeast(int major) {
        assumeBuildToolsAtLeast(new Revision(major));
    }

    public static void assumeBuildToolsAtLeast(int major, int minor, int micro) {
        assumeBuildToolsAtLeast(new Revision(major, minor, micro));
    }

    public static void assumeBuildToolsAtLeast(int major, int minor, int micro, int preview) {
        assumeBuildToolsAtLeast(new Revision(major, minor, micro, preview));
    }

    public static void assumeBuildToolsAtLeast(Revision revision) {
        Revision currentVersion = Revision.parseRevision(
                GradleTestProject.DEFAULT_BUILD_TOOL_VERSION);
        Assume.assumeTrue("Test is only applicable to build tools >= " + revision.toString(),
                currentVersion.compareTo(revision) >= 0);

    }
}
