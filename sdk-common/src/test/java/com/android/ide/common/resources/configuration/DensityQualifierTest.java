/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.common.resources.configuration;

import com.android.resources.Density;

import junit.framework.TestCase;

public class DensityQualifierTest extends TestCase {

    private DensityQualifier pdq;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pdq = new DensityQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        pdq = null;
        config = null;
    }

    public void testCheckAndSet() {
        assertEquals(true, pdq.checkAndSet("ldpi", config));//$NON-NLS-1$
        assertTrue(config.getDensityQualifier() != null);
        assertEquals(Density.LOW, config.getDensityQualifier().getValue());
        assertEquals("ldpi", config.getDensityQualifier().toString()); //$NON-NLS-1$
    }

    public void testFailures() {
        assertEquals(false, pdq.checkAndSet("", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("dpi", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123dpi", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("sdfdpi", config));//$NON-NLS-1$
    }

    public void testIsBetterMatchThan() {
        DensityQualifier ldpi = new DensityQualifier(Density.LOW);
        DensityQualifier mdpi = new DensityQualifier(Density.MEDIUM);
        DensityQualifier hdpi = new DensityQualifier(Density.HIGH);
        DensityQualifier xhdpi = new DensityQualifier(Density.XHIGH);
        DensityQualifier xxhdpi = new DensityQualifier(Density.XXHIGH);
        DensityQualifier anydpi = new DensityQualifier(Density.ANYDPI);
        DensityQualifier nulldpi = ldpi.getNullQualifier();

        // first test that each Q is a better match than all other Qs when the ref is the same Q.
        assertTrue(ldpi.isBetterMatchThan(mdpi, ldpi));
        assertTrue(ldpi.isBetterMatchThan(hdpi, ldpi));
        assertTrue(ldpi.isBetterMatchThan(xhdpi, ldpi));

        assertTrue(mdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(mdpi.isBetterMatchThan(hdpi, mdpi));
        assertTrue(mdpi.isBetterMatchThan(xhdpi, mdpi));

        assertTrue(hdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(hdpi.isBetterMatchThan(mdpi, hdpi));
        assertTrue(hdpi.isBetterMatchThan(xhdpi, hdpi));

        assertTrue(xhdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(xhdpi.isBetterMatchThan(mdpi, xhdpi));
        assertTrue(xhdpi.isBetterMatchThan(hdpi, xhdpi));

        assertTrue(anydpi.isBetterMatchThan(ldpi, anydpi));
        assertTrue(anydpi.isBetterMatchThan(mdpi, anydpi));
        assertTrue(anydpi.isBetterMatchThan(hdpi, anydpi));

        // now test when there's no exact match

        // looking for ldpi:
        assertTrue(mdpi.isBetterMatchThan(hdpi, ldpi));
        assertTrue(nulldpi.isBetterMatchThan(mdpi, ldpi));
        assertTrue(mdpi.isBetterMatchThan(xhdpi, ldpi));
        assertTrue(hdpi.isBetterMatchThan(xhdpi, ldpi));
        assertTrue(anydpi.isBetterMatchThan(hdpi, ldpi));
        // the other way around
        assertFalse(hdpi.isBetterMatchThan(mdpi, ldpi));
        assertFalse(mdpi.isBetterMatchThan(nulldpi, ldpi));
        assertFalse(xhdpi.isBetterMatchThan(mdpi, ldpi));
        assertFalse(xhdpi.isBetterMatchThan(hdpi, ldpi));
        assertFalse(hdpi.isBetterMatchThan(anydpi, ldpi));

        // looking for mdpi
        assertTrue(hdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(xhdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(ldpi.isBetterMatchThan(xxhdpi, mdpi));
        assertTrue(hdpi.isBetterMatchThan(xhdpi, mdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(hdpi, mdpi));
        assertFalse(ldpi.isBetterMatchThan(xhdpi, mdpi));
        assertFalse(xxhdpi.isBetterMatchThan(ldpi, mdpi));
        assertFalse(xhdpi.isBetterMatchThan(hdpi, mdpi));

        // looking for hdpi
        assertTrue(mdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(mdpi.isBetterMatchThan(nulldpi, hdpi));
        assertTrue(xhdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(xhdpi.isBetterMatchThan(mdpi, hdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(mdpi, hdpi));
        assertFalse(nulldpi.isBetterMatchThan(mdpi, hdpi));
        assertFalse(ldpi.isBetterMatchThan(xhdpi, hdpi));
        assertFalse(mdpi.isBetterMatchThan(xhdpi, hdpi));

        // looking for xhdpi
        assertTrue(mdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(hdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(hdpi.isBetterMatchThan(mdpi, xhdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(mdpi, xhdpi));
        assertFalse(ldpi.isBetterMatchThan(hdpi, xhdpi));
        assertFalse(mdpi.isBetterMatchThan(hdpi, xhdpi));

        // looking for anydpi
        assertTrue(anydpi.isBetterMatchThan(ldpi, anydpi));
        assertTrue(anydpi.isBetterMatchThan(ldpi, anydpi));
        assertTrue(anydpi.isBetterMatchThan(mdpi, anydpi));
        assertTrue(anydpi.isBetterMatchThan(xhdpi, anydpi));
    }
}
