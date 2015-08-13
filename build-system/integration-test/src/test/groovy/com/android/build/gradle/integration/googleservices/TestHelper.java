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

package com.android.build.gradle.integration.googleservices;

import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.junit.Assert;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;

/**
 */
public class TestHelper {

    public static final String JSON_FILE_NAME = "google-services.json";

    public static File getResourceFolder2(String... pathSegments) {
        CodeSource source = TestHelper.class.getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                Assert.assertTrue(dir.getPath(), dir.exists());

                dir = dir.getParentFile().getParentFile().getParentFile();

                List<String> segments = Lists.newArrayList("src", "test", "resources");
                if (pathSegments != null) {
                    segments.addAll(Lists.newArrayList(pathSegments));
                }

                return new File(
                        dir,
                        Joiner.on(File.separator).join(segments));
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        fail("Fail to get the test resource folder");

        return null;
    }
}
