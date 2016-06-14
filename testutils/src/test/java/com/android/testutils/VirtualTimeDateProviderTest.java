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

package com.android.testutils;

import junit.framework.TestCase;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link VirtualTimeDateProvider}.
 */
public class VirtualTimeDateProviderTest extends TestCase {
    public void testVirtualTimeDateProviderBasics() {
        // Set up an instance of the VirtualTimeDateProvider to be tested.

        VirtualTimeScheduler virtualTimeScheduler = new VirtualTimeScheduler();
        VirtualTimeDateProvider virtualTimeDateProvider =
                new VirtualTimeDateProvider(virtualTimeScheduler);

        // Check that the inital state of the scheduler matches the VirtualTimeDateProvider
        assertEquals(new Date(0), virtualTimeDateProvider.now());

        // Move the notion of time in the VirtualTimeScheduler ahead and assert that the
        // VirtualTimeDateProvider is in line with that notion of time.
        virtualTimeScheduler.advanceBy(100, TimeUnit.MILLISECONDS);
        assertEquals(new Date(100), virtualTimeDateProvider.now());
        virtualTimeScheduler.advanceBy(10, TimeUnit.MINUTES);
        assertEquals(new Date(600100), virtualTimeDateProvider.now());
    }
}
