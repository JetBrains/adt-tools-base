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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertNotNull;

import com.android.testutils.TestUtils;

import org.junit.Test;

import java.io.File;

public class ReadWithDifferentCompressionLevelsTest {

    @Test
    public void readL9() throws Exception {
        File packagingRoot = TestUtils.getRoot("packaging");
        String rsrcPath = packagingRoot.getAbsolutePath() + "/l9.zip";
        File l9File = new File(rsrcPath);

        try (ZFile read = new ZFile(l9File, new ZFileOptions())) {
            assertNotNull(read.get("text-files/rfc2460.txt"));
        }
    }

    @Test
    public void readL1() throws Exception {
        File packagingRoot = TestUtils.getRoot("packaging");
        String rsrcPath = packagingRoot.getAbsolutePath() + "/l1.zip";
        File l1File = new File(rsrcPath);

        try (ZFile read = new ZFile(l1File, new ZFileOptions())) {
            assertNotNull(read.get("text-files/rfc2460.txt"));
        }
    }
}
