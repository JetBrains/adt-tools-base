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
package com.android.tools.chartlib;

import junit.framework.TestCase;

public class BaseAxisDomainTest extends TestCase {

    private BaseAxisDomain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // minMinorSpacing = 10, minMajorSpacing = 100, switchThreshold = 5;
        domain = new MockAxisDomain(10, 100, 5);
    }

    @Override
    public void tearDown() throws Exception {
        domain = null;
    }

    public void testGetMajorInterval() throws Exception {
        // minMajoringSpacing is 100, so we are expected to
        // to get one major interval of a 100 units (10cm).
        int interval = domain.getMajorInterval(100, 100);
        assertEquals(100, interval);

        // Axis length is smaller than min spacing, and we should
        // expect at least one major tick of a 100 units.
        interval = domain.getMajorInterval(100, 10);
        assertEquals(100, interval);

        // minMajorSpacing is 100, so we can fit at least 10 major ticks
        // and is just equal to the min interval (1cm) at that scale.
        interval = domain.getMajorInterval(100, 1000);
        assertEquals(10, interval);
    }

    public void testGetMinorInterval() throws Exception {
        // minMinorSpacing is 10, so we are expected to get
        // 10 minor intervals of 10 units each.
        int interval = domain.getMinorInterval(100, 100);
        assertEquals(10, interval);

        // minMinorSpacing is 10, so we are expected to get
        // 5 minor intervals of 1 unit each
        interval = domain.getMinorInterval(5, 50);
        assertEquals(1, interval);
    }

    public void testGetMultiplierIndex() throws Exception {
        // value is equal or less than the threshold to get to the next multiplier
        // so we are still in "mm" scale
        int index = domain.getMultiplierIndex(50, 5);
        assertEquals(0, index);
        assertEquals(1, domain.mMultiplier);
        assertEquals("mm", domain.getUnit(index));

        // value is greater than the first multiplier * threshold
        // jumps to "cm"
        index = domain.getMultiplierIndex(51, 5);
        assertEquals(1, index);
        assertEquals(10, domain.mMultiplier);
        assertEquals("cm", domain.getUnit(index));

        // value is greater than the second multiplier * threshold
        // jumps to "m"
        index = domain.getMultiplierIndex(5001, 5);
        assertEquals(2, index);
        assertEquals(1000, domain.mMultiplier);
        assertEquals("m", domain.getUnit(index));
    }
}