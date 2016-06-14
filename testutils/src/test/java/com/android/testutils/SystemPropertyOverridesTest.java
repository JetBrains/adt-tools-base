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

package com.android.testutils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link SystemPropertyOverrides}.
 */
public class SystemPropertyOverridesTest {
    private final String PROPERTY1_NAME = "override_test1";
    private final String PROPERTY2_NAME = "override_test2";
    private final String PROPERTY3_NAME = "override_test3";
    private final String INITIAL_VALUE = "initial_value";
    private final String TEST_VALUE1 = "test_value1";
    private final String TEST_VALUE2 = "test_value2";

    @Test
    public void systemPropertiesOverrideBasicsTest() throws Exception {
        // Set an initial value for property1
        System.setProperty(PROPERTY1_NAME, INITIAL_VALUE);
        assertEquals(INITIAL_VALUE, System.getProperty(PROPERTY1_NAME));

        // ensure that property2 has no initial value.
        assertNull(System.getProperty(PROPERTY2_NAME));

        // Set an initial value for property3
        System.setProperty(PROPERTY3_NAME, INITIAL_VALUE);
        assertEquals(INITIAL_VALUE, System.getProperty(PROPERTY3_NAME));

        try (SystemPropertyOverrides overrides = new SystemPropertyOverrides()) {
            // Override both property1 and property2 values a few times and check that the
            // getProperty result is as expected.
            // Property 3 has an initial value and we'll null it out in the test.
            overrides.setProperty(PROPERTY1_NAME, TEST_VALUE1);
            assertEquals(TEST_VALUE1, System.getProperty(PROPERTY1_NAME));
            overrides.setProperty(PROPERTY1_NAME, TEST_VALUE2);
            assertEquals(TEST_VALUE2, System.getProperty(PROPERTY1_NAME));
            overrides.setProperty(PROPERTY2_NAME, TEST_VALUE1);
            assertEquals(TEST_VALUE1, System.getProperty(PROPERTY2_NAME));
            overrides.setProperty(PROPERTY2_NAME, TEST_VALUE2);
            assertEquals(TEST_VALUE2, System.getProperty(PROPERTY2_NAME));
            overrides.setProperty(PROPERTY3_NAME, null);
            assertNull(System.getProperty(PROPERTY3_NAME));
        }

        // overridden properties should be back to initial values after overrides is closed.
        assertEquals(INITIAL_VALUE, System.getProperty(PROPERTY1_NAME));
        assertNull(System.getProperty(PROPERTY2_NAME));
        assertEquals(INITIAL_VALUE, System.getProperty(PROPERTY3_NAME));
    }
}
