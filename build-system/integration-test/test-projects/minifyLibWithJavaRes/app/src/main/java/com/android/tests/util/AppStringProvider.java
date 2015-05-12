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

package com.android.tests.util;

import java.io.InputStream;
import java.io.IOException;
import java.lang.RuntimeException;
import java.util.Properties;
import java.util.Enumeration;
import java.net.URL;

/**
 * String provider getting the string format from a co-bundled resources.properties file.
 */
public class AppStringProvider {

    private final static Properties properties = new Properties();

    static {
        try {
            InputStream inputStream = null;
            try {
                inputStream = AppStringProvider.class.getResourceAsStream("resources.properties");
                if (inputStream == null) {
                    properties.put("app.name", "Error, cannot find resources.properties for %d");
                } else {
                    properties.load(inputStream);
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            try {
                // load the second resource file, using absolute path.
                inputStream = AppStringProvider.class.getResourceAsStream("/com/android/tests/util/another.properties");
                if (inputStream == null) {
                    properties.put("app.string", "Error, cannot load another.properties %s %d");
                } else {
                    properties.load(inputStream);
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}