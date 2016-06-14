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

import com.android.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to make it easier for tests to override system properties (instead of directly using
 * {@link System#setProperty(String, String)}). By using this class within try-with-resources,
 * the test can be assured that any property set will be restored to its initial state after the
 * test completes (success & failure).
 */
public class SystemPropertyOverrides implements AutoCloseable {
    private final Map<String, String> mOriginals = new HashMap<>();

    /**
     * Sets a system property (by calling {@link System#setProperty(String, String)}) while
     * backing up the original value and replacing it once this class is closed.
     */
    public void setProperty(@NonNull String key, String value) {
        if (!mOriginals.containsKey(key)) {
            String originalValue = System.getProperty(key);
            mOriginals.put(key, originalValue);
        }
        if (value == null) {
            // setProperty doesn't support null values so use clearProperty instead in that case.
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Override
    public void close() throws Exception {
        for (Map.Entry<String, String> original : mOriginals.entrySet()) {
            // setProperty doesn't support null values so use clearProperty instead in that case.
            if (original.getValue() == null) {
                System.clearProperty(original.getKey());
            } else {
                System.setProperty(original.getKey(), original.getValue());
            }
        }
    }
}
