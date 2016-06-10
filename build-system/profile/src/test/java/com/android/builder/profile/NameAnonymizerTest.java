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

package com.android.builder.profile;

import static com.android.builder.profile.NameAnonymizer.NO_VARIANT_SPECIFIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class NameAnonymizerTest {

    @Test
    public void anonymizeProjectName() {
        NameAnonymizer nameAnonymizer = new NameAnonymizer();

        assertEquals(nameAnonymizer.anonymizeProjectName(":a"),
                nameAnonymizer.anonymizeProjectName(":a"));
        assertEquals(nameAnonymizer.anonymizeProjectName(":b"),
                nameAnonymizer.anonymizeProjectName(":b"));
        assertEquals(nameAnonymizer.anonymizeProjectName(":c"),
                nameAnonymizer.anonymizeProjectName(":c"));

        assertNotEquals(nameAnonymizer.anonymizeProjectName(":a"),
                nameAnonymizer.anonymizeProjectName(":b"));
        assertNotEquals(nameAnonymizer.anonymizeProjectName(":a"),
                nameAnonymizer.anonymizeProjectName(":c"));
        assertNotEquals(nameAnonymizer.anonymizeProjectName(":b"),
                nameAnonymizer.anonymizeProjectName(":c"));
    }

    @Test
    public void anonymizeVariant() {
        NameAnonymizer nameAnonymizer = new NameAnonymizer();

        assertEquals(NO_VARIANT_SPECIFIED, nameAnonymizer.anonymizeVariant(":a", null));
        assertNotEquals(NO_VARIANT_SPECIFIED, nameAnonymizer.anonymizeVariant(":a", "debug"));
        assertNotEquals(NO_VARIANT_SPECIFIED, nameAnonymizer.anonymizeVariant(":a", "release"));
        assertEquals(nameAnonymizer.anonymizeVariant(":a", "debug"),
                nameAnonymizer.anonymizeVariant(":a", "debug"));
        assertEquals(nameAnonymizer.anonymizeVariant(":a", "release"),
                nameAnonymizer.anonymizeVariant(":a", "release"));

        assertNotEquals(nameAnonymizer.anonymizeVariant(":a", "debug"),
                nameAnonymizer.anonymizeVariant(":a", "release"));

    }
}
