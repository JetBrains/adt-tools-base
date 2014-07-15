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

import static com.android.tools.lint.checks.GradleDetector.DEPENDENCY;
import static com.android.tools.lint.checks.GradleDetector.DEPRECATED;
import static com.android.tools.lint.checks.GradleDetector.STRING_INTEGER;
import static com.android.tools.lint.checks.GradleDetector.getNewValue;
import static com.android.tools.lint.checks.GradleDetector.getOldValue;

import junit.framework.TestCase;

/**
 * <b>NOTE</b>: Most GradleDetector unit tests are in the Studio plugin, as tests
 * for IntellijGradleDetector
 */
public class GradleDetectorTest extends TestCase {
    public void testGetOldValue() {
        assertEquals("11.0.2", getOldValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"));
        assertNull(getOldValue(DEPENDENCY, "Bogus"));
        assertNull(getOldValue(DEPENDENCY, "bogus"));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("20", getOldValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"));
        assertEquals("'19'", getOldValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)"));
        assertEquals("android", getOldValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead"));
        assertEquals("android-library", getOldValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead"));
        assertEquals("packageName", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'"));
        assertEquals("packageNameSuffix", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"));
        assertEquals("18.0.0", getOldValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later"));
    }

    public void testGetNewValue() {
        assertEquals("17.0.0", getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"));
        assertNull(getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available"));
        assertNull(getNewValue(DEPENDENCY, "bogus"));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("19", getNewValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"));
        assertEquals("19", getNewValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)"));
        assertEquals("com.android.application", getNewValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead"));
        assertEquals("com.android.library", getNewValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead"));
        assertEquals("applicationId", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'"));
        assertEquals("applicationIdSuffix", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"));
        assertEquals("19.1", getNewValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later"));
    }
}