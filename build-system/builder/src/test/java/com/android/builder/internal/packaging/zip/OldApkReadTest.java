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
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;

import org.junit.Test;

import java.io.File;

public class OldApkReadTest {

    @Test
    public void testReadOldApk() throws Exception {
        File packagingRoot = TestUtils.getRoot("packaging");
        String apkPath = packagingRoot.getAbsolutePath() + "/test.apk";
        File apkFile = new File(apkPath);
        assertTrue(apkFile.exists());

        try (ZFile zf = new ZFile(apkFile, new ZFileOptions())) {
            StoredEntry classesDex = zf.get("classes.dex");
            assertNotNull(classesDex);
        }
    }
}
