/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

/**
 * Tests for {@link DateProvider}.
 */
public class DateProviderTest {
    @Test
    public void testSystemDateProvider() {
        // This test checks if the System DateProvider's notion of time is within 1 second of the
        // system time. We chose this relatively large range to avoid this test from being flaky.
        long systemDateProviderTime = DateProvider.SYSTEM.now().getTime();
        long dateTime = new Date().getTime();
        Assert.assertTrue(Math.abs(systemDateProviderTime - dateTime) < 1000);
    }
}
