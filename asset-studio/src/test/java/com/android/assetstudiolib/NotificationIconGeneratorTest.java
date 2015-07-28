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

import com.android.assetstudiolib.NotificationIconGenerator.NotificationOptions;

import java.io.IOException;

@SuppressWarnings("javadoc")
public class NotificationIconGeneratorTest extends BitmapGeneratorTest {
    private void checkGraphic(String baseName, int minSdk, String folderName,
            int expectedCount) throws IOException {
        NotificationOptions options = new NotificationOptions();
        options.minSdk = minSdk;

        NotificationIconGenerator generator = new NotificationIconGenerator();
        checkGraphic(expectedCount, folderName, baseName, generator, options);
    }

    private void checkGraphic(String baseName) throws IOException {
        checkGraphic(baseName, 1, "notification", 12);
    }

    public void testNotification1() throws Exception {
        checkGraphic("ic_stat_1");
    }

    public void testNotification2() throws Exception {
        checkGraphic("ic_stat_1", 9 /* minSdk */, "notification-v9+", 8 /* fileCount */);
    }

    public void testNotification3() throws Exception {
        checkGraphic("ic_stat_1", 11, "notification-v11+", 4);
    }
}
