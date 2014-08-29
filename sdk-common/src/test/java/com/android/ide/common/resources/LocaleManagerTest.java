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

import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

@SuppressWarnings({"javadoc", "SizeReplaceableByIsEmpty"})
public class LocaleManagerTest extends TestCase {
    /** Enable to update the switch lookup in {@link LocaleManager#getRelevantRegions(String)} */
    private static final boolean GENERATE_MULTI_REGION_SWITCH = false;
    /** Enable to update the switch lookup in {@link LocaleManager#getTimeZoneRegion(TimeZone)} */
    private static final boolean GENERATE_TIMEZONE_SWITCH = false;
    /** Flag relevant to {@link #GENERATE_MULTI_REGION_SWITCH} : if set, only includes the most
     * common regions */
    private static final boolean SKIP_REGIONS_WITHOUT_TIMEZONE_DATA = false;
    /** Enable to see the languages with multiple relevant regions */
    private static final boolean DUMP_INFERRED_REGIONS = false;

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

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    public void testGetRelevantRegions() {
        assertEquals(Arrays.asList("NO"), LocaleManager.getRelevantRegions("no"));
        assertEquals(Arrays.asList("NO", "SJ"), LocaleManager.getRelevantRegions("nb"));
        assertEquals(Arrays.asList("DK","GL"), LocaleManager.getRelevantRegions("da"));
        assertTrue(LocaleManager.getRelevantRegions("en").contains("US"));
        assertTrue(LocaleManager.getRelevantRegions("en").contains("GB"));
    }

    public void testGetTimeZoneRegion() {
        assertEquals("PT", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("Europe/Lisbon")));
        assertEquals("PT", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("Atlantic/Azores")));
        assertEquals("PT", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("Atlantic/Azores")));

        assertEquals("BR", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("America/Araguaina")));

        assertEquals("US", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("America/Adak")));
        assertEquals("US", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("America/Anchorage")));
        assertEquals("US", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("PST")));

        // Test JDK variations
        assertEquals("LY", LocaleManager.getTimeZoneRegion(TimeZone.getTimeZone("Africa/Tripoli")));
        assertEquals("LY", LocaleManager.getTimeZoneRegion(new SimpleTimeZone(3600000, "Africa/Tripoli")));
        assertEquals("LY", LocaleManager.getTimeZoneRegion(new SimpleTimeZone(7200000, "Africa/Tripoli"))); // changed in jdk8
        assertNull(LocaleManager.getTimeZoneRegion(new SimpleTimeZone(-42, "Africa/Tripoli"))); // wrong
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetLanguageRegion() {
        Locale prevLocale = Locale.getDefault();
        TimeZone prevTimeZone = TimeZone.getDefault();
        try {
            assertFalse("The envvar $STUDIO_LOCALES should not be set during unit test runs",
                    System.getenv("STUDIO_LOCALES") != null);
            assertNull(System.getProperty("studio.locales"), System.getProperty("studio.locales"));
            Locale.setDefault(Locale.US);

            // Pick English=>GU based on options
            System.setProperty("studio.locales", "es_US, en_GU");
            assertEquals("GU", LocaleManager.getLanguageRegion("en"));

            System.setProperty("studio.locales", "es-rUS, en-rGU"); // alternate supported syntax
            assertEquals("GU", LocaleManager.getLanguageRegion("en"));
            System.setProperty("studio.locales", "");

            // Pick English=>GB based on timezone
            Locale.setDefault(Locale.ITALY);
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
            assertEquals("GB", LocaleManager.getLanguageRegion("en"));

            // Pick English=>CA based on system locale country
            Locale.setDefault(Locale.CANADA);
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"));
            assertEquals("CA", LocaleManager.getLanguageRegion("en"));
        } finally {
            Locale.setDefault(prevLocale);
            TimeZone.setDefault(prevTimeZone);
        }
    }

    public void testGetTimeZoneRegionAll() {
        for (String id : TimeZone.getAvailableIDs()) {
            TimeZone zone = TimeZone.getTimeZone(id);
            assertNotNull(id, zone);
            String region = LocaleManager.getTimeZoneRegion(zone);
            assertNotNull(region, zone.getID());
        }
    }

    public void testGenerateLanguageRegionAndTimeZoneMaps() throws Exception {
        // Associated locales:
        String[] combinations = new String[]{
                // In ICU's data source (e.g. icu4c-54_1-data.zip) I went into data/locales and ran
                //    /bin/ls | egrep ^.._..\.txt$ | awk '{ print substr($0,0,5) }' | sort
                // Looks like data/regions omits the dominant sections; maybe check this
                "af_NA", "af_ZA", "ak_GH", "am_ET", "ar_AE", "ar_BH", "ar_DJ", "ar_DZ", "ar_EG",
                "ar_EH", "ar_ER", "ar_IL", "ar_IQ", "ar_JO", "ar_KM", "ar_KW", "ar_LB", "ar_LY",
                "ar_MA", "ar_MR", "ar_OM", "ar_PS", "ar_QA", "ar_SA", "ar_SD", "ar_SO", "ar_SS",
                "ar_SY", "ar_TD", "ar_TN", "ar_YE", "as_IN", "az_AZ", "be_BY", "bg_BG", "bn_BD",
                "bn_IN", "bo_CN", "bo_IN", "br_FR", "bs_BA", "ca_AD", "ca_ES", "ca_FR", "ca_IT",
                "cs_CZ", "cy_GB", "da_DK", "da_GL", "de_AT", "de_BE", "de_CH", "de_DE", "de_LI",
                "de_LU", "dz_BT", "ee_GH", "ee_TG", "el_CY", "el_GR", "en_AG", "en_AI", "en_AS",
                "en_AU", "en_BB", "en_BE", "en_BM", "en_BS", "en_BW", "en_BZ", "en_CA", "en_CC",
                "en_CK", "en_CM", "en_CX", "en_DG", "en_DM", "en_ER", "en_FJ", "en_FK", "en_FM",
                "en_GB", "en_GD", "en_GG", "en_GH", "en_GI", "en_GM", "en_GU", "en_GY", "en_HK",
                "en_IE", "en_IM", "en_IN", "en_IO", "en_JE", "en_JM", "en_KE", "en_KI", "en_KN",
                "en_KY", "en_LC", "en_LR", "en_LS", "en_MG", "en_MH", "en_MO", "en_MP", "en_MS",
                "en_MT", "en_MU", "en_MW", "en_MY", "en_NA", "en_NF", "en_NG", "en_NH", "en_NR",
                "en_NU", "en_NZ", "en_PG", "en_PH", "en_PK", "en_PN", "en_PR", "en_PW", "en_RH",
                "en_RW", "en_SB", "en_SC", "en_SD", "en_SG", "en_SH", "en_SL", "en_SS", "en_SX",
                "en_SZ", "en_TC", "en_TK", "en_TO", "en_TT", "en_TV", "en_TZ", "en_UG", "en_UM",
                "en_US", "en_VC", "en_VG", "en_VI", "en_VU", "en_WS", "en_ZA", "en_ZM", "en_ZW",
                "es_AR", "es_BO", "es_CL", "es_CO", "es_CR", "es_CU", "es_DO", "es_EA", "es_EC",
                "es_ES", "es_GQ", "es_GT", "es_HN", "es_IC", "es_MX", "es_NI", "es_PA", "es_PE",
                "es_PH", "es_PR", "es_PY", "es_SV", "es_US", "es_UY", "es_VE", "et_EE", "eu_ES",
                "fa_AF", "fa_IR", "ff_CM", "ff_GN", "ff_MR", "ff_SN", "fi_FI", "fo_FO", "fr_BE",
                "fr_BF", "fr_BI", "fr_BJ", "fr_BL", "fr_CA", "fr_CD", "fr_CF", "fr_CG", "fr_CH",
                "fr_CI", "fr_CM", "fr_DJ", "fr_DZ", "fr_FR", "fr_GA", "fr_GF", "fr_GN", "fr_GP",
                "fr_GQ", "fr_HT", "fr_KM", "fr_LU", "fr_MA", "fr_MC", "fr_MF", "fr_MG", "fr_ML",
                "fr_MQ", "fr_MR", "fr_MU", "fr_NC", "fr_NE", "fr_PF", "fr_PM", "fr_RE", "fr_RW",
                "fr_SC", "fr_SN", "fr_SY", "fr_TD", "fr_TG", "fr_TN", "fr_VU", "fr_WF", "fr_YT",
                "fy_NL", "ga_IE", "gd_GB", "gl_ES", "gu_IN", "gv_IM", "ha_GH", "ha_NE", "ha_NG",
                "he_IL", "hi_IN", "hr_BA", "hr_HR", "hu_HU", "hy_AM", "id_ID", "ig_NG", "ii_CN",
                "in_ID", "is_IS", "it_CH", "it_IT", "it_SM", "iw_IL", "ja_JP", "ka_GE", "ki_KE",
                "kk_KZ", "kl_GL", "km_KH", "kn_IN", "ko_KP", "ko_KR", "ks_IN", "kw_GB", "ky_KG",
                "lb_LU", "lg_UG", "ln_AO", "ln_CD", "ln_CF", "ln_CG", "lo_LA",
                "lt_LT", "lu_CD", "lv_LV", "mg_MG", "mk_MK", "ml_IN", "mn_MN", "mr_IN", "ms_BN",
                "ms_MY", "ms_SG", "mt_MT", "my_MM", "nb_NO", "nb_SJ", "nd_ZW", "ne_IN", "ne_NP",
                "nl_AW", "nl_BE", "nl_BQ", "nl_CW", "nl_NL", "nl_SR", "nl_SX", "nn_NO", "no_NO",
                "om_ET", "om_KE", "or_IN", "os_GE", "os_RU", "pa_IN", "pa_PK", "pl_PL", "ps_AF",
                "pt_AO", "pt_BR", "pt_CV", "pt_GW", "pt_MO", "pt_MZ", "pt_PT", "pt_ST", "pt_TL",
                "qu_BO", "qu_EC", "qu_PE", "rm_CH", "rn_BI", "ro_MD", "ro_RO", "ru_BY", "ru_KG",
                "ru_KZ", "ru_MD", "ru_RU", "ru_UA", "rw_RW", "se_FI", "se_NO", "se_SE", "sg_CF",
                "sh_BA", "sh_CS", "sh_YU", "si_LK", "sk_SK", "sl_SI", "sn_ZW", "so_DJ", "so_ET",
                "so_KE", "so_SO", "sq_AL", "sq_MK", "sq_XK", "sr_BA", "sr_CS", "sr_ME", "sr_RS",
                "sr_XK", "sr_YU", "sv_AX", "sv_FI", "sv_SE", "sw_KE", "sw_TZ", "sw_UG", "ta_IN",
                "ta_LK", "ta_MY", "ta_SG", "te_IN", "th_TH", "ti_ER", "ti_ET", "tl_PH", "to_TO",
                "tr_CY", "tr_TR", "ug_CN", "uk_UA", "ur_IN", "ur_PK", "uz_AF", "uz_UZ", "vi_VN",
                "yo_BJ", "yo_NG", "zh_CN",
                "zh_HK", "zh_MO", "zh_SG", "zh_TW", "zu_ZA"
        };

        Set<String> languages = LocaleManager.getLanguageCodes();
        Set<String> regions = LocaleManager.getRegionCodes();

        Multimap<String,String> regionMap = ArrayListMultimap.create();

        Map<String, Set<TimeZone>> regionToZones = getRegionToZones(); // ICU data
        if (regionToZones.isEmpty()) {
            System.out.println("Warning: No icu.jar configured: not generating timezone data");
        }

        for (String combination : combinations) {
            assert combination.length() == 5 : combination;
            assert combination.charAt(2) == '_': combination;
            String language = combination.substring(0, 2);
            String region = combination.substring(3, 5);

            if (languages.contains(language)) {
                if (regions.contains(region)) {
                    if (SKIP_REGIONS_WITHOUT_TIMEZONE_DATA) {
                        //noinspection StatementWithEmptyBody
                        if ((region.equals("BD") || region.equals("NP") || region.equals("PK"))
                                && language.equals("bn") || language.equals("ne")
                                || language.equals("pa")) {
                            // manual exclusions; these were found to be the only region data
                        } else {
                            // Takes it down from 119 languages with 381 regions to
                            // 80 languages with 172 regions
                            Set<TimeZone> timeZones = regionToZones.get(region);
                            if (timeZones == null || timeZones.isEmpty()) {
                                // Ignoring regions that don't have timezone data; these
                                // are less relevant
                                continue;
                            }
                        }
                    }

                    regionMap.put(language, region);
                } else {
                    if (region.equals("IC")
                            || region.equals("DG")
                            || region.equals("EA")
                            || region.equals("YU")
                            || region.equals("CS")
                            || region.equals("NH")
                            || region.equals("RH")
                            || region.equals("XK")) {
                        // Various deleted codes, known reservations etc; see
                        // http://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
                        continue;
                    }
                    System.out.println("Ignoring unknown region " + region + " for language " +
                            language);
                }
            } else {
                if ("sh".equals(language)) {
                    // The sh code for Serbo-Croatian is obsolete, so don't flag it here:
                    // http://www.w3.org/WAI/ER/IG/ert/iso639.htm
                    continue;
                }
                System.out.println("Ignoring unknown language code `" + language + "`");
            }
        }

        List<String> sortedLanguages = Lists.newArrayList(regionMap.keySet());
        Collections.sort(sortedLanguages);

        // Use the region data to make sure that for the case where there is a single
        // associated region, it agrees with our pre-recorded defaults
        for (String language : sortedLanguages) {
            Collection<String> relevantRegions = regionMap.get(language);
            assert relevantRegions != null && !relevantRegions.isEmpty() : language;
            if (relevantRegions.size() == 1) {
                String recordedRegion = LocaleManager.getLanguageRegion(language);
                String discoveredRegion = relevantRegions.iterator().next();
                assert discoveredRegion != null;
                if (recordedRegion == null) {
                    System.out.println("We had no primary region for language " + language +
                            " (" + LocaleManager.getLanguageName(language) +
                            "); but was discovered to be " + discoveredRegion +
                            " (" + LocaleManager.getRegionName(discoveredRegion) + ")");
                } else if (!recordedRegion.equals(discoveredRegion)) {
                    System.out.println("The primary region for language " + language +
                            " (" + LocaleManager.getLanguageName(language) +
                            ") was " + recordedRegion + "(" +
                            LocaleManager.getRegionName(recordedRegion) +
                            ") but was discovered to be " + discoveredRegion + " (" +
                            LocaleManager.getRegionName(discoveredRegion) + ")");
                }
            }
        }

        if (DUMP_INFERRED_REGIONS) {
            System.out.println("Found " + regionMap.keySet().size() + " languages with " +
               regionMap.values().size() + " regions");
            for (String language : sortedLanguages) {
                Collection<String> relevantRegions = regionMap.get(language);
                assert relevantRegions != null && !relevantRegions.isEmpty() : language;
                List<String> sortedRegions = Lists.newArrayList(relevantRegions);
                Collections.sort(sortedRegions);

                if (sortedRegions.size() == 1) {
                    continue;
                }

                System.out.println(language + ": " + Joiner.on(",").join(sortedRegions));
            }
        }

        // Dump out switch
        char lastFirst = 0;
        if (GENERATE_MULTI_REGION_SWITCH) {
            StringBuilder sb = new StringBuilder(1000);
            int level = 2;
            indent(sb, level);
            sb.append("switch (first) {\n");
            for (String language : sortedLanguages) {
                Collection<String> relevantRegions = regionMap.get(language);
                assert relevantRegions != null && !relevantRegions.isEmpty() : language;
                List<String> sortedRegions = Lists.newArrayList(relevantRegions);
                Collections.sort(sortedRegions);

                if (sortedRegions.size() == 1) {
                    continue;
                }

                char first = language.charAt(0);
                char second = language.charAt(1);
                boolean newFirstLetter = first != lastFirst;
                if (newFirstLetter) {
                    if (lastFirst != 0) {
                        indent(sb, level + 2);
                        sb.append("break;\n");
                        indent(sb, level + 1);
                        sb.append("}\n");
                    }
                    lastFirst = first;
                    indent(sb, level + 1);
                    sb.append("case '").append(first).append("': {\n");
                }
                indent(sb, level + 2);
                if (newFirstLetter) {
                    sb.append("if (second == '").append(second).append("') {\n");
                } else {
                    sb.append("else if (second == '").append(second).append("') {\n");
                }
                String list = '"' + Joiner.on("\",\"").join(sortedRegions) + '"';
                indent(sb, level + 3);
                sb.append("return Arrays.asList(").append(list).append(");\n");
                indent(sb, level + 2);
                sb.append("}\n");
            }
            indent(sb, level + 2);
            sb.append("break;\n");
            indent(sb, level + 1);
            sb.append("}\n");
            indent(sb, level);
            sb.append("}\n");

            String code = sb.toString();
            dumpLineCount(code);
            File tempFile = File.createTempFile("switches", ".java");
            Files.write(code, tempFile, Charsets.UTF_8);
            System.out.println("Wrote updated code fragment to " + tempFile);
        }

        // Attempt to generate timezone data for conflicted regions
        if (!regionToZones.isEmpty()) {
            final Map<TimeZone, String> zoneToRegion = getZoneToRegion(regionToZones);

            // Idea: In order to limit the number of cases, we find out which region
            // has the largest number of timezones, and use that as the default. In other
            // words, if there isn't a match for any of the other timezones, then we fall
            // back to that one without actually having to check the individual timezones.


            // Generate switch statements
            if (GENERATE_TIMEZONE_SWITCH) {
                // Make sure this is generated on a recent JDK which has a lot of
                // the new time zones
                String property = System.getProperty("java.version");
                assertTrue(property, property.startsWith("1."));
                assertTrue("Use a more recent JRE when generating data",
                        property.charAt(2) >= '8');

                List<TimeZone> candidates = Lists.newArrayList(zoneToRegion.keySet());

                SwitchGenerator generator = new SwitchGenerator(zoneToRegion, candidates);
                String code = generator.generate();
                dumpLineCount(code);
                File tempFile = File.createTempFile("code", ".java");
                Files.write(code, tempFile, Charsets.UTF_8);
                System.out.println("Wrote updated code fragment to " + tempFile);

                // Check that our lookup method not only always produces a region
                // (which is checked by testFindRegionByTimeZone), but also check
                // that it produces the *same* results as the ICU mappings!
                for (TimeZone zone : generator.getCandidates()) {
                    String region = LocaleManager.getTimeZoneRegion(zone);
                    String expected = zoneToRegion.get(zone);
                    if (!expected.equals(region)) {
                        assertEquals(zone.getID(), expected, region);
                    }
                }
            }
        }
    }

    public static void dumpLineCount(String code) {
        int lines = 0;
        for (int i = 0, n = code.length(); i < n; i++) {
            if (code.charAt(i) == '\n') {
                lines++;
            }
        }
        System.out.println("Generated " + lines + " lines.");
    }

    /** Produce a map from a region code to relevant timezones */
    private static Map<String, Set<TimeZone>> getRegionToZones() throws Exception {
        Map<String, Set<TimeZone>> zones = new HashMap<String, Set<TimeZone>>();

        // For this we rely on ICU's com.ibm.icu.util.TimeZone#getAvailableIDs() method;
        // we have icu4j in the SDK for layoutlib, so load it dynamically here and access
        // via reflection
        String sdk = System.getenv("ANDROID_HOME");
        if (sdk == null) {
            return Collections.emptyMap();
        }
        File icu = new File(new File(sdk), "platforms/"
                + AndroidTargetHash.getPlatformHashString(new AndroidVersion(
                    HIGHEST_KNOWN_STABLE_API, null))
                + "/data/icu4j.jar"
                .replace('/', File.separatorChar));
        if (!icu.exists()) {
            return Collections.emptyMap();
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{icu.toURI().toURL()},
                LocaleManagerTest.class.getClassLoader());
        Class<?> timeZoneClass = loader.loadClass("com.ibm.icu.util.TimeZone");
        Method getAvailableIDs = timeZoneClass.getDeclaredMethod("getAvailableIDs", String.class);

        for (Locale locale : Locale.getAvailableLocales()) {
            String countryCode = locale.getCountry();
            if (countryCode.length() != 2) {
                continue;
            }
            Set<TimeZone> timezones = zones.get(countryCode);
            if (timezones == null) {
                timezones = Sets.newHashSet();
                zones.put(countryCode, timezones);
            }
            String[] ids = (String[]) getAvailableIDs.invoke(null, countryCode);
            for (String id : ids) {
                timezones.add(TimeZone.getTimeZone(id));
            }
        }
        return zones;
    }

    /** Produces an inverse mapping for the result from {@link #getRegionToZones()} */
    private static Map<TimeZone, String> getZoneToRegion(
            @NonNull Map<String, Set<TimeZone>> regionToZones) {
        final Map<TimeZone, String> zoneToRegion = Maps.newHashMap();
        for (Map.Entry<String, Set<TimeZone>> entry : regionToZones.entrySet()) {
            String region = entry.getKey();
            for (TimeZone zone : entry.getValue()) {
                if (zoneToRegion.containsKey(zone) && !zoneToRegion.get(zone).equals(region)) {
                    fail("Didn't expect multiple regions to have the same time zone: " +
                            zone.getID() + " in both " + zoneToRegion.get(zone) +
                            " and now " + region);
                }
                zoneToRegion.put(zone, region);
            }
        }

        return zoneToRegion;
    }

    /** Removes timezones that reference regions directly in the id */
    private static List<TimeZone> filterCountryZones(Collection<TimeZone> zones) {
        // Generate lookup tree for regions

        Map<String,String> countryToRegionCode = Maps.newHashMap();
        for (String region : LocaleManager.getRegionCodes()) {
            countryToRegionCode.put(LocaleManager.getRegionName(region), region);
        }

        // Remove continent codes that are actually country names; these are
        // obvious later
        List<TimeZone> filtered = Lists.newArrayListWithExpectedSize(zones.size());
        for (TimeZone zone : zones) {
            String id = zone.getID();

            if (id.equals("GMT")) {
                // In some ICU database this is mapped to Taiwan; that's not right.
                continue;
            }

            boolean containsCountry = false;
            // Look in all elements of the timezone, e.g. we can have
            // Portugal, or Asia/Singapore, or America/Argentina/Mendoza
            for (String element : Splitter.on('/').trimResults().split(id)) {
                // Change '_' to ' ' such that we look up e.g. El Salvador, not El_Salvador
                String name = element.replace('_', ' ');
                if (countryToRegionCode.get(name) != null) {
                    containsCountry = true;
                    break;
                }
                if (element.equals("US")) {
                    // Region name is United States, but this really does map to a country code
                    containsCountry = true;
                    break;
                }

            }
            if (!containsCountry) {
                filtered.add(zone);
            }
        }

        return filtered;
    }

    @NonNull
    private static <T extends Comparable> List<T> sorted(Collection<T> list) {
        List<T> sorted = Lists.newArrayList(list);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Code generator which creates a fast lookup method from timezone string to
     * the corresponding region code.
     */
    private static class SwitchGenerator {
        private static final int PADDING = 16;

        @SuppressWarnings("StringBufferField")
        private final StringBuilder sb = new StringBuilder(1000);

        private final Map<TimeZone, String> mZoneToRegion;
        private final List<TimeZone> mCandidates;

        public SwitchGenerator(Map<TimeZone, String> zoneToRegion, List<TimeZone> candidates) {
            mZoneToRegion = zoneToRegion;
            mCandidates = candidates;
        }

        /**
         * Returns the relevant timezones that we are generating a switch for.
         * This will have filtered out time zones with geographic locations.
         */
        private List<TimeZone> getCandidates() {
            return mCandidates;
        }

        private static int hashedId(TimeZone zone) {
            // Instead of String#hashCode, use this to ensure stable across platforms
            int h = 0;
            String id = zone.getID();
            for (int i = 0, n = id.length(); i < n; i++) {
                h = 31 * h + id.charAt(i);
            }
            return h;
        }

        @NonNull
        private List<TimeZone> sortByRegion(Collection<TimeZone> zones) {
            List<TimeZone> sorted = Lists.newArrayList(zones);

            final Map<String,Integer> regionFrequency = Maps.newHashMap();
            for (TimeZone zone : zones) {
                String region = mZoneToRegion.get(zone);
                Integer frequency = regionFrequency.get(region);
                if (frequency == null) {
                    regionFrequency.put(region, 1);
                } else {
                    regionFrequency.put(region, frequency + 1);
                }
            }

            Collections.sort(sorted, new Comparator<TimeZone>() {
                // Sort such that the timezones are all sorted (alphabetically) grouped
                // by the same target region, and the regions in turn are sorted
                // by frequency
                @Override
                public int compare(TimeZone o1, TimeZone o2) {
                    String r1 = mZoneToRegion.get(o1);
                    String r2 = mZoneToRegion.get(o2);
                    assert r1 != null : o1.getID();
                    assert r2 != null : o2.getID();
                    int delta = r1.compareTo(r2);
                    if (delta == 0) { // Same region: compare alphabetically by id
                        return o1.getID().compareTo(o2.getID());
                    } else {
                        // Different regions: compare by frequency
                        int f1 = regionFrequency.get(r1);
                        int f2 = regionFrequency.get(r2);
                        delta = f1 - f2;
                        if (delta == 0) {
                            delta = r1.compareTo(r2); // If same region frequency, alphabetical
                        }
                    }

                    return delta;
                }
            });
            return sorted;
        }

        public String generate() {
            // Generate lookup tree for regions
            final Multimap<String, TimeZone> mContinentMap = ArrayListMultimap.create();
            for (TimeZone zone : mCandidates) {
                String id = zone.getID();
                int index = id.indexOf('/');
                String continent = "";
                if (index != -1) {
                    continent = id.substring(0, index);
                }
                mContinentMap.put(continent, zone);
            }
            List<String> sortedContinents = Lists.newArrayList(mContinentMap.keySet());
            Collections.sort(sortedContinents, new Comparator<String>() {
                // Sort by decreasing timezone-per continent count, but put the
                // no-continent codes at the end
                @Override
                public int compare(String o1, String o2) {
                    boolean e1 = o1.isEmpty();
                    boolean e2 = o2.isEmpty();
                    if (e1 && e2) {
                        return 0;
                    } else if (e1) {
                        return 1;
                    } else if (e2) {
                        return -1;
                    } else {
                        int delta = mContinentMap.get(o2).size() - mContinentMap.get(o1).size();
                        if (delta != 0) {
                            return delta;
                        }
                        return o1.compareTo(o2);
                    }
                }
            });

            int level = 1;

            indent(sb, level);
            sb.append("// This code is generated by LocaleManagerTest#testGenerateLanguageRegionMap\n");
            indent(sb, level);
            sb.append("// if LocaleManagerTest#GENERATE_MULTI_REGION_SWITCH is true\n");
            indent(sb, level);
            sb.append("@SuppressWarnings(\"SpellCheckingInspection\")\n");
            indent(sb, level);
            sb.append("@Nullable\n");
            indent(sb, level);
            sb.append("@VisibleForTesting\n");
            indent(sb, level);
            sb.append("static String getTimeZoneRegion(@NonNull TimeZone zone) {   \n");
            level++;

            indent(sb, level);
            sb.append("// Instead of String#hashCode, use this to ensure stable across platforms\n");
            indent(sb, level);
            sb.append("String id = zone.getID();\n");
            indent(sb, level);
            sb.append("int hashedId = 0;\n");
            indent(sb, level);
            sb.append("for (int i = 0, n = id.length(); i < n; i++) {\n");
            indent(sb, level + 1);
            sb.append("hashedId = 31 * hashedId + id.charAt(i);\n");
            indent(sb, level);
            sb.append("}\n");

            indent(sb, level);
            sb.append("switch (zone.getRawOffset()) {\n");

            Multimap<Integer,TimeZone> offsetMap = ArrayListMultimap.create();
            for (TimeZone zone : mCandidates) {
                int rawOffset = zone.getRawOffset();
                offsetMap.put(rawOffset, zone);
            }
            makeJdkTimezoneCorrections(offsetMap);

            level++;
            for (Integer offset : sorted(offsetMap.keySet())) {
                indent(sb, level);
                sb.append("case ").append(offset).append(":");

                sb.append(" // ");
                sb.append(String.valueOf(offset / 3600000.0));
                sb.append(" hours\n");

                generateZones(level + 1, offsetMap.get(offset));
            }
            level--;

            indent(sb, level);
            sb.append("}\n");

            indent(sb, level);
            sb.append("return null;\n");
            level--;
            indent(sb, level);
            sb.append("}\n");

            return sb.toString();
        }

        private void makeJdkTimezoneCorrections(Multimap<Integer,TimeZone> offsetMap) {
            // There have been some timezone corrections; since our switch-based lookup is
            // based on offsets, we should list these corrected timezones in both switch
            // blocks.
            //
            // For example, in JDK 6, the timezone "America/Rio_Branco" had rawOffset -14400000,
            // and in JDK 8 it's -18000000. Therefore, we should have switch statements for
            // both.

            // JDK 1.6 to JDK 1.7:
            correctOffset(offsetMap, "Africa/Tripoli", 3600000, 7200000);
            correctOffset(offsetMap, "Libya", 3600000, 7200000);
            correctOffset(offsetMap, "America/Argentina/San_Luis", -14400000, -10800000);
            correctOffset(offsetMap, "America/Eirunepe", -14400000, -10800000);
            correctOffset(offsetMap, "America/Porto_Acre", -14400000, -10800000);
            correctOffset(offsetMap, "America/Rio_Branco", -14400000, -10800000);
            correctOffset(offsetMap, "Brazil/Acre", -14400000, -10800000);
            //correctOffset(offsetMap, "Pacific/Fakaofo", 50400000, 46800000);

            // JDK 1.7 to JDK 1.8:
            correctOffset(offsetMap, "Europe/Simferopol", 7200000, 14400000);
            //correctId(offsetMap, "Asia/Riyadh87", "Mideast/Riyadh87", 11224000);
            //correctId(offsetMap, "Asia/Riyadh88", "Mideast/Riyadh88", 11224000);
            //correctId(offsetMap, "Asia/Riyadh89", "Mideast/Riyadh89", 11224000);

            // JDK 1.8 to JDK 1. EA:
            //correctId(offsetMap, null, "Asia/Chita", 28800000);
            //correctId(offsetMap, null, "Asia/Srednekolymsk", 39600000);
            //correctId(offsetMap, null, "Pacific/Bougainville", 39600000);
            correctOffset(offsetMap, "Asia/Irkutsk", 32400000, 28800000);
            correctOffset(offsetMap, "Asia/Kashgar", 28800000, 21600000);
            correctOffset(offsetMap, "Asia/Khandyga", 36000000, 32400000);
            correctOffset(offsetMap, "Asia/Krasnoyarsk", 28800000, 25200000);
            correctOffset(offsetMap, "Asia/Magadan", 43200000, 36000000);
            correctOffset(offsetMap, "Asia/Novosibirsk", 25200000, 21600000);
            correctOffset(offsetMap, "Asia/Omsk", 25200000, 21600000);
            correctOffset(offsetMap, "Asia/Sakhalin", 39600000, 36000000);
            correctOffset(offsetMap, "Asia/Urumqi", 28800000, 21600000);
            correctOffset(offsetMap, "Asia/Ust-Nera", 39600000, 36000000);
            correctOffset(offsetMap, "Asia/Vladivostok", 39600000, 36000000);
            correctOffset(offsetMap, "Asia/Yakutsk", 36000000, 32400000);
            correctOffset(offsetMap, "Asia/Yekaterinburg", 21600000, 18000000);
            correctOffset(offsetMap, "Europe/Kaliningrad", 10800000, 7200000);
            correctOffset(offsetMap, "Europe/Moscow", 14400000, 10800000);
            correctOffset(offsetMap, "Europe/Simferopol", 14400000, 10800000);
            correctOffset(offsetMap, "Europe/Volgograd", 14400000, 10800000);
            correctOffset(offsetMap, "W-SU", 14400000, 10800000);
        }

        private void correctOffset(
                @NonNull Multimap<Integer, TimeZone> offsetMap,
                @NonNull String id,
                int rawOffset1,
                int rawOffset2) {
            TimeZone old = findZone(id);
            if (old == null) {
                // Not relevant for our usage
                return;
            }
            TimeZone zone1 = findZone(offsetMap, id, rawOffset1);
            if (zone1 == null) {
                addZone(offsetMap, id, rawOffset1, old);
            }
            TimeZone zone2 = findZone(offsetMap, id, rawOffset2);
            if (zone2 == null) {
                addZone(offsetMap, id, rawOffset2, old);
            }
        }

        private void addZone(Multimap<Integer, TimeZone> offsetMap, String id, int rawOffset,
                TimeZone old) {
            TimeZone zone = new SimpleTimeZone(rawOffset, id);
            offsetMap.put(rawOffset, zone);
            mCandidates.add(zone);
            mZoneToRegion.put(zone, mZoneToRegion.get(old));
        }

        private void correctId(
                @NonNull Multimap<Integer, TimeZone> offsetMap,
                @Nullable String id1,
                @Nullable String id2,
                int rawOffset) {
            if (id1 != null) {
                TimeZone zone1 = findZone(offsetMap, id1, rawOffset);
                if (zone1 == null && id2 != null) {
                    TimeZone old = findZone(id2);
                    addZone(offsetMap, id1, rawOffset, old);
                }
            }
            if (id2 != null) {
                TimeZone zone2 = findZone(offsetMap, id2, rawOffset);
                if (zone2 == null && id1 != null) {
                    TimeZone old = findZone(id1);
                    addZone(offsetMap, id2, rawOffset, old);
                }
            }
        }

        private TimeZone findZone(@NonNull String id) {
            for (TimeZone zone : mCandidates) {
                if (zone.getID().equals(id)) {
                    return zone;
                }
            }

            return null;
        }

        private static TimeZone findZone(@NonNull Multimap<Integer, TimeZone> offsetMap,
                @NonNull String id, int rawOffset) {
            Collection<TimeZone> zones = offsetMap.get(rawOffset);
            if (zones == null) {
                return null;
            }

            for (TimeZone zone : zones) {
                if (id.equals(zone.getID())) {
                    return zone;
                }
            }

            return null;
        }

        private void generateZones(int level, Collection<TimeZone> zones) {
            assert zones.size() > 0;

            // See if they all map to the same region
            boolean regionsDiffer = false;
            String sameRegion = mZoneToRegion.get(zones.iterator().next());
            for (TimeZone zone : zones) {
                String region = mZoneToRegion.get(zone);
                if (!sameRegion.equals(region)) {
                    regionsDiffer = true;
                    break;
                }
            }
            if (!regionsDiffer) {
                returnRegion(zones, level);
                return;
            }

            indent(sb, level);
            sb.append("switch (hashedId) {\n");
            level++;

            Map<Integer,TimeZone> hashCodes = Maps.newHashMap();
            List<TimeZone> sorted = sortByRegion(zones);
            String lastRegion = mZoneToRegion.get(sorted.get(zones.size() - 1));
            for (int i = 0, n = sorted.size(); i < n; i++) {
                TimeZone zone = sorted.get(i);
                int hash = hashedId(zone);
                if (hashCodes.containsKey(hash)) {
                    fail("Timezones clash: same hash " + hash + " for " + zone.getID() + " and "
                            + hashCodes.get(hash));
                }

                String region = mZoneToRegion.get(zone);

                if (i < n - 1 && region.equals(lastRegion)) {
                    // TODO: Combine into a list instead
                    indent(sb, level);
                    pad(7);
                    sb.append("// ").append(escape(zone.getID())).append("\n");
                    continue;
                } else if (i == n - 1) {
                    indent(sb, level);
                    sb.append("default:\n");
                } else {
                    indent(sb, level);
                    sb.append("case ").append(hash).append(":");
                    String text = String.valueOf(hash);
                    pad(text);

                    sb.append(" // ").append(escape(zone.getID())).append("\n");
                }
                if (i < n - 1 && region.equals(mZoneToRegion.get(sorted.get(i + 1)))) {
                    // Don't return each one; share a single body when the regions are the same
                    continue;
                }
                returnRegion(zone, level + 1);
            }

            level--;
            indent(sb, level);
            sb.append("}\n");
        }

        private void pad(int space) {
            int padding = PADDING + space;
            for (int j = 0; j < padding; j++) {
                sb.append(' ');
            }
        }

        private void pad(String text) {
            int padding = PADDING - text.length();
            for (int j = 0; j < padding; j++) {
                sb.append(' ');
            }
        }

        private void returnRegion(Collection<TimeZone> zones, int level) {
            String region = mZoneToRegion.get(zones.iterator().next());
            List<String> ids = Lists.newArrayList();
            for (TimeZone zone : zones) {
                ids.add(zone.getID());
                assert region.equals(mZoneToRegion.get(zone)) : zone + " vs " + region;
            }
            indent(sb, level);
            sb.append("return \"").append(region).append("\";");
            pad(-9);
            sb.append("// ").append(escape(LocaleManager.getRegionName(region)));
            sb.append("\n");
        }

        private void returnRegion(TimeZone zone, int level) {
            returnRegion(Collections.singletonList(zone), level);
        }
    }

    private static void indent(@NonNull StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("    ");
        }
    }

    private static String escape(@NonNull String s) {
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

    /* Utility useful for identifying strings which must be using \\u in the string names
     * to ensure that they are handled properly during the build (outside of Studio/Eclipse,
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
