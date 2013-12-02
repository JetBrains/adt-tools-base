/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.assetstudiolib;

import java.io.IOException;

@SuppressWarnings("javadoc")
public class TabIconGeneratorTest extends GeneratorTest {
    private void checkGraphic(String folderName, String baseName, int minSdk,
            int expectedFileCount) throws IOException {
        TabIconGenerator generator = new TabIconGenerator();
        TabIconGenerator.TabOptions options = new TabIconGenerator.TabOptions();
        options.minSdk = minSdk;
        checkGraphic(expectedFileCount, folderName, baseName, generator, options);
    }

    public void testTabs1() throws Exception {
        checkGraphic("tabs", "ic_tab_1", 1 /* minSdk */, 16 /* expectedFileCount */);
    }

    public void testTabs2() throws Exception {
        checkGraphic("tabs-v5+", "ic_tab_1", 5, 8);
    }
}
