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

package com.android.build.gradle.internal.transforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.repository.Revision;
import com.android.utils.FileUtils;

import org.junit.Test;

import java.io.File;

/**
 * Test cases for {@link DexTransform}.
 */
public class DexTransformTest {

    @Test
    public void testGetKey() throws Exception {
        File inputFile = new File("/Users/foo/Android/Sdk/extras/android/m2repository/"
                + "com/android/support/support-annotations/23.3.0/support-annotations-23.3.0.jar");
        Revision buildToolsRevision = Revision.parseRevision("23.0.3");
        String key = DexTransform.getKey(inputFile, buildToolsRevision, true, false, true);

        assertTrue(key.matches("\\Qsupport-annotations-23.3.0.jar_\\E\\w{40}"
                + "\\Q_build=23.0.3_jumbo=true_multidex=false_optimize=true\\E"));

        inputFile = new File("/Users/foo/MyApplication/app/build/intermediates/exploded-aar/"
                + "com.android.support/design/23.3.0/jars/classes.jar");
        buildToolsRevision = Revision.parseRevision("23.0");
        key = DexTransform.getKey(inputFile, buildToolsRevision, false, true, false);

        assertEquals(FileUtils.join("com.android.support", "design", "23.3.0", "jars", "classes.jar")
                + "_build=23.0_jumbo=false_multidex=true_optimize=false", key);

        inputFile = new File("/Users/foo/MyApplication/app/build/intermediates/exploded-aar/"
                + "com.android.support/support-v4/23.3.0/jars/libs/internal_impl-23.3.0.jar");
        buildToolsRevision = Revision.parseRevision("23");
        key = DexTransform.getKey(inputFile, buildToolsRevision, true, true, true);

        assertEquals(FileUtils.join("com.android.support", "support-v4", "23.3.0", "jars", "libs",
                "internal_impl-23.3.0.jar") + "_build=23_jumbo=true_multidex=true_optimize=true",
                key);

        inputFile = new File("/Users/foo/MyApplication/app/build/intermediates/pre-dexed/debug/"
                + "debug_283caf89dd7987cc2f3b325eb70525d5b717a7b5.jar");
        buildToolsRevision = Revision.NOT_SPECIFIED;
        key = DexTransform.getKey(inputFile, buildToolsRevision, false, false, false);

        assertTrue(key.matches("\\Qdebug_283caf89dd7987cc2f3b325eb70525d5b717a7b5.jar_\\E\\w{40}"
                + "\\Q_build=0_jumbo=false_multidex=false_optimize=false\\E"));
    }

}
