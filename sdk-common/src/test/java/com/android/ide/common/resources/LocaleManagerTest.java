/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.util.Locale;
import java.util.Map;

@SuppressWarnings({"javadoc", "SizeReplaceableByIsEmpty"})
public class LocaleManagerTest extends TestCase {
    public void testIntegrity() {
        LocaleManager localeManager = LocaleManager.get();
        assertSame(localeManager, LocaleManager.get());

        Map<String, String> languageToCountry = LocaleManager.getLanguageToCountryMap();
        Map<String, String> languageNames = LocaleManager.getLanguageNamesMap();
        Map<String, String> regionNames = LocaleManager.getRegionNamesMap();
        assertEquals("Make sure to update initial capacity in declaration after editing map",
                176, languageToCountry.size());
        assertEquals("Make sure to update initial capacity in declaration after editing map",
                187, languageNames.size());
        assertEquals("Make sure to update initial capacity in declaration after editing map",
                249, regionNames.size());

        assertTrue(Sets.difference(languageToCountry.keySet(),
                languageNames.keySet()).isEmpty());
        for (Map.Entry<String, String> entry : languageToCountry.entrySet()) {
            assertTrue(entry.getValue(), entry.getKey().length() > 0);
            assertTrue(entry.getKey(), entry.getValue().length() > 0);
        }
        for (Map.Entry<String, String> entry : regionNames.entrySet()) {
            assertTrue(entry.getValue(), entry.getKey().length() > 0);
            assertTrue(entry.getKey(), entry.getValue().length() > 0);
        }
        for (Map.Entry<String, String> entry : languageNames.entrySet()) {
            assertTrue(entry.getValue(), entry.getKey().length() > 0);
            assertTrue(entry.getKey(), entry.getValue().length() > 0);
        }
        for (String s : languageToCountry.keySet()) {
            assertTrue(s, s.length() == 2 && s.equals(s.toLowerCase(Locale.US)));
        }
        for (String s : languageNames.keySet()) {
            assertTrue(s, s.length() == 2 && s.equals(s.toLowerCase(Locale.US)));
        }
        for (String s : languageNames.values()) {
            assertTrue(s, s.length() > 2 && Character.isUpperCase(s.charAt(0)));
        }
        for (String s : languageToCountry.values()) {
            assertTrue(s, s.length() == 2 && s.equals(s.toUpperCase(Locale.US)));
        }
        for (String s : regionNames.keySet()) {
            assertTrue(s, s.length() == 2 && s.equals(s.toUpperCase(Locale.US)));
        }
        for (String s : regionNames.values()) {
            assertTrue(s, s.length() > 2 && Character.isUpperCase(s.charAt(0)));
        }
        assertNull(languageToCountry.get(""));
        assertNull(languageNames.get(""));
        assertTrue(Sets.difference(languageToCountry.keySet(),
                languageNames.keySet()).isEmpty());
    }

    public void testGetLanguageNames() throws Exception {
        assertEquals("English", LocaleManager.getLanguageName("en"));
        assertEquals("Norwegian Bokm\u00e5l", LocaleManager.getLanguageName("nb"));
        assertEquals("Norwegian", LocaleManager.getLanguageName("no"));
        assertEquals("French", LocaleManager.getLanguageName("fr"));
        assertEquals("German", LocaleManager.getLanguageName("de"));
        assertEquals("Hindi", LocaleManager.getLanguageName("hi"));
    }

    public void testGetRegionNames() {
        assertEquals("United States", LocaleManager.getRegionName("US"));
        assertEquals("Norway", LocaleManager.getRegionName("NO"));
        assertEquals("France", LocaleManager.getRegionName("FR"));
        assertEquals("India", LocaleManager.getRegionName("IN"));
    }

    /* Utility useful for identifying strings which must be using \\u in the string names
     * to ensure that they are handled properly during the build (outside of Eclipse,
     * where this source file is marked as using UTF-8.
    public void testPrintable() {
        Set<String> languageCodes = LocaleManager.getLanguageCodes();
        for (String code : languageCodes) {
            String name = LocaleManager.getLanguageName(code);
            assertNotNull(name);
            checkEncoding(name);
        }
        Set<String> regionCodes = LocaleManager.getRegionCodes();
        for (String code : regionCodes) {
            String name = LocaleManager.getRegionName(code);
            assertNotNull(name);
            checkEncoding(name);
        }
    }

    private static void checkEncoding(String s) {
        String encoded = escape(s);
        if (!encoded.equals(s)) {
            System.out.println("Need unicode encoding for '" + s + "'");
            System.out.println(" Replacement=" + encoded);
        }
    }

    private static String escape(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c >= 128) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0, m = s.length(); j < m; j++) {
                    char d = s.charAt(j);
                    if (d < 128) {
                        sb.append(d);
                    }
                    else {
                        sb.append('\\');
                        sb.append('u');
                        sb.append(String.format("%04x", (int) d));
                    }
                }
                return sb.toString();
            }
        }
        return s;
    }

    // Generates source code for region list sorted by region
    public void sortRegions() {
        final Map<String, String> map = LocaleManager.getRegionNamesMap();
        List<String> sorted = new ArrayList<String>(map.keySet());
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String code1, String code2) {
                String region1 = map.get(code1);
                String region2 = map.get(code2);
                return region1.compareTo(region2);
            }
        });
        for (String code : sorted) {
            String region = map.get(code);
            String line = "         sRegionNames.put(\"" + code + "\", \"" + escape(region)
                    + "\");";
            System.out.print(line);
            for (int column = line.length(); column < 86; column++) {
                System.out.print(' ');
            }
            System.out.println("//$NON-NLS-1$");
        }
    }
    */
}
