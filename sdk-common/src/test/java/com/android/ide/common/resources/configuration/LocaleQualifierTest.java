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

package com.android.ide.common.resources.configuration;

import static com.android.ide.common.resources.configuration.LocaleQualifier.isNormalizedCase;
import static com.android.ide.common.resources.configuration.LocaleQualifier.normalizeCase;
import static com.android.ide.common.resources.configuration.LocaleQualifier.parseBcp47;

import junit.framework.TestCase;

import java.util.Locale;

public class LocaleQualifierTest extends TestCase {

    private FolderConfiguration config;
    private LocaleQualifier lq;

    @Override
    public void setUp()  throws Exception {
        super.setUp();
        config = new FolderConfiguration();
        lq = new LocaleQualifier();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        config = null;
        lq = null;
    }

    public void testCheckAndSet() {
        assertEquals(true, lq.checkAndSet("b+kok", config)); //$NON-NLS-1$
        assertTrue(config.getLocaleQualifier() != null);
        assertEquals("b+kok", config.getLocaleQualifier().toString()); //$NON-NLS-1$
    }

    public void testCheckAndSetCaseInsensitive() {
        assertEquals(true, lq.checkAndSet("b+KOK", config)); //$NON-NLS-1$
        assertTrue(config.getLocaleQualifier() != null);
        assertEquals("b+kok", config.getLocaleQualifier().toString()); //$NON-NLS-1$
        assertEquals("b+kok", LocaleQualifier.getFolderSegment("b+KOK"));
    }

    public void testFailures() {
        assertEquals(false, lq.checkAndSet("", config)); //$NON-NLS-1$
        assertEquals(false, lq.checkAndSet("abc", config)); //$NON-NLS-1$
    }

    @SuppressWarnings("ConstantConditions")
    public void testParseBcp47() {

        assertNull(parseBcp47("kok-rIN"));

        assertEquals("kok", parseBcp47("b+kok").getFirst());
        assertNull(parseBcp47("b+kok").getSecond());

        assertEquals("kok", parseBcp47("b+kok+VARIANT").getFirst());
        assertNull(parseBcp47("b+kok+VARIANT").getSecond());

        assertEquals("kok", parseBcp47("b+kok+Knda+419+VARIANT").getFirst());
        assertEquals("419", parseBcp47("b+kok+Knda+419+VARIANT").getSecond());

        assertEquals("kok", parseBcp47("b+kok+VARIANT").getFirst());
        assertNull(parseBcp47("b+kok+VARIANT").getSecond());

        assertEquals("kok", parseBcp47("b+kok+IN").getFirst());
        assertEquals("IN", parseBcp47("b+kok+IN").getSecond());

        assertEquals("kok", parseBcp47("b+kok+Knda").getFirst());
        assertNull(parseBcp47("b+kok+Knda").getSecond());

        assertEquals("kok", parseBcp47("b+kok+Knda+419").getFirst());
        assertEquals("419", parseBcp47("b+kok+Knda+419").getSecond());
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetLanguageAndGetRegion() {
        assertEquals(true, lq.checkAndSet("b+kok", config)); //$NON-NLS-1$
        assertEquals("b+kok", config.getLocaleQualifier().getValue());
        assertNull("kok", config.getLanguageQualifier());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertNull("kok", config.getEffectiveRegion());

        assertEquals(true, lq.checkAndSet("b+kok+VARIANT", config)); //$NON-NLS-1$
        assertEquals("b+kok+variant", config.getLocaleQualifier().getValue());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertNull("kok", config.getEffectiveRegion());

        assertEquals(true, lq.checkAndSet("b+kok+Knda+419+VARIANT", config)); //$NON-NLS-1$
        assertEquals("b+kok+Knda+419+variant", config.getLocaleQualifier().getValue());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertEquals("419", config.getEffectiveRegion().getValue());

        assertEquals(true, lq.checkAndSet("b+kok+IN", config)); //$NON-NLS-1$
        assertEquals("b+kok+IN", config.getLocaleQualifier().getValue());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertEquals("IN", config.getEffectiveRegion().getValue());

        assertEquals(true, lq.checkAndSet("b+kok+Knda", config)); //$NON-NLS-1$
        assertEquals("b+kok+Knda", config.getLocaleQualifier().getValue());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertNull(config.getEffectiveRegion());

        assertEquals(true, lq.checkAndSet("b+kok+Knda+419", config)); //$NON-NLS-1$
        assertEquals("b+kok+Knda+419", config.getLocaleQualifier().getValue());
        assertEquals("kok", config.getEffectiveLanguage().getValue());
        assertEquals("419", config.getEffectiveRegion().getValue());
    }

    public void testIsNormalCase() {
        assertFalse(isNormalizedCase("b+en+CA+x+ca".toLowerCase(Locale.US)));
        assertTrue(isNormalizedCase("b+en+CA+x+ca"));
        assertFalse(isNormalizedCase("b+sgn+BE+FR".toLowerCase(Locale.US)));
        assertTrue(isNormalizedCase("b+sgn+BE+FR"));
        assertFalse(isNormalizedCase("b+az+Latn+x+latn".toLowerCase(Locale.US)));
        assertTrue(isNormalizedCase("b+az+Latn+x+latn"));
        assertFalse(isNormalizedCase("b+MN+cYRL+mn".toLowerCase(Locale.US)));
        assertTrue(isNormalizedCase("b+mn+Cyrl+MN"));
        assertFalse(isNormalizedCase("b+zh+CN+a+myext+x+private".toLowerCase(Locale.US)));
        assertTrue(isNormalizedCase("b+zh+CN+a+myext+x+private"));
    }

    public void testNormalizeCase() {
        assertEquals("b+en+CA+x+ca", normalizeCase("b+en+CA+x+ca".toLowerCase(Locale.US)));
        assertEquals("b+sgn+BE+FR", normalizeCase("b+sgn+BE+FR".toLowerCase(Locale.US)));
        assertEquals("b+az+Latn+x+latn", normalizeCase("b+az+Latn+x+latn".toLowerCase(Locale.US)));
        assertEquals("b+mn+Cyrl+MN", normalizeCase("b+MN+cYRL+mn".toLowerCase(Locale.US)));
        assertEquals("b+zh+CN+a+myext+x+private", normalizeCase(
                "b+zh+CN+a+myext+x+private".toLowerCase(Locale.US)));
    }
}

