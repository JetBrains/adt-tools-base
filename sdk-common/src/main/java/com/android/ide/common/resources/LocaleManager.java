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

import static java.util.Locale.US;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * The {@linkplain LocaleManager} provides access to locale information such as
 * language names and language to region name mappings for the various locales.
 */
public class LocaleManager {
    @SuppressWarnings("InstantiationOfUtilityClass")
    private static final LocaleManager sInstance = new LocaleManager();

    /**
     * Returns the {@linkplain LocaleManager} singleton
     *
     * @return the {@linkplain LocaleManager} singleton, never null
     */
    @NonNull
    public static LocaleManager get() {
        return sInstance;
    }

    /** Use the {@link #get()} factory method */
    private LocaleManager() {
    }

    /**
     * Map of default bindings from language to country (if a region is not
     * specified). Note that if a given language is the language of the default
     * locale on the user's machine, then the country corresponding to that
     * locale is used. Thus, even if for example the default binding of the "en"
     * language is "US", if the current locale has language="en" and the country
     * for that locale is "GB", then "GB" will be used.
     */
    private static Map<String, String> sLanguageToCountry = Maps.newHashMapWithExpectedSize(177);
    /** Names of the various languages according to ISO 639-1 */
    private static Map<String, String> sLanguageNames = Maps.newHashMapWithExpectedSize(187);
    /** Names of the various regions according to ISO 3166-1 */
    private static Map<String, String> sRegionNames = Maps.newHashMapWithExpectedSize(249);

    /**
     * Returns the name of the given region for a 2 letter region code, in English.
     *
     * @param regionCode the 2 letter region code (ISO 3166-1 alpha-2)
     * @return the name of the given region for a region code, in English, or
     *         null if not known
     */
    @Nullable
    public static String getRegionName(@NonNull String regionCode) {
        assert regionCode.length() == 2
                && Character.isUpperCase(regionCode.charAt(0))
                && Character.isUpperCase(regionCode.charAt(1)) : regionCode;

        return sRegionNames.get(regionCode);
    }

    /**
     * Returns the name of the given language for a language code, in English.
     *
     * @param languageCode the 2 letter language code (ISO 639-1)
     * @return the name of the given language for a language code, in English, or
     *         null if not known
     */
    @Nullable
    public static String getLanguageName(@NonNull String languageCode) {
        assert languageCode.length() == 2
                && Character.isLowerCase(languageCode.charAt(0))
                && Character.isLowerCase(languageCode.charAt(1)) : languageCode;

        return sLanguageNames.get(languageCode);
    }

    /**
     * Returns all the known language codes
     *
     * @return all the known language codes
     */
    @NonNull
    public static Set<String> getLanguageCodes() {
        return Collections.unmodifiableSet(sLanguageNames.keySet());
    }

    /**
     * Returns all the known region codes
     *
     * @return all the known region codes
     */
    @NonNull
    public static Set<String> getRegionCodes() {
        return Collections.unmodifiableSet(sRegionNames.keySet());
    }

    /**
     * Returns the region code for the given language. <b>Note that there can be
     * many regions that speak a given language; this just picks one</b> based
     * on a set of heuristics.
     *
     * @param languageCode the language to look up
     * @return the corresponding region code, if any
     */
    @Nullable
    public static String getLanguageRegion(@NonNull String languageCode) {
        return getLanguageRegion(languageCode, null);
    }

    /**
     * Returns the region code for the given language. <b>Note that there can be
     * many regions that speak a given language; this just picks one</b> based
     * on a set of heuristics.
     *
     * @param languageCode the language to look up
     * @return the corresponding region code, if any
     */
    @Nullable
    public static String getLanguageRegion(@NonNull String languageCode,
            @Nullable ResourceRepository resources) {
        // Try to pick one language based on various heuristics:

        // (1) Check to see if the user has deliberately picked a preferred
        //     region for this language with an option. That should always
        //     win. Example: STUDIO_LOCALES="en_GB,pt_PT" says that for English
        //     we should always use the region GB and for Portuguese we should always
        //     use the region PT. Also allow en-GB and en-rGB.
        String option = System.getenv("STUDIO_LOCALES");
        if (option == null) {
            option = System.getProperty("studio.locales");
        }
        if (option != null) {
            for (String regionLocale : Splitter.on(',').trimResults().split(option)) {
                if (SdkUtils.startsWithIgnoreCase(regionLocale, languageCode)) {
                    if (regionLocale.length() == 5 && ((regionLocale.charAt(2) == '_')
                            || regionLocale.charAt(2) == '-')) {
                        return regionLocale.substring(3).toUpperCase(US);
                    } else if (regionLocale.length() == 6 && regionLocale.charAt(2) == '-'
                            && regionLocale.charAt(3) == 'r') {
                        return regionLocale.substring(4).toUpperCase(US);
                    }
                }
            }
        }

        // (2) Check the user's locale; if it happens to be in the same language
        //     as the target language, and it specifies a region, use that region
        Locale locale = Locale.getDefault();
        if (languageCode.equalsIgnoreCase(locale.getLanguage())) {
            String country = locale.getCountry();
            if (!country.isEmpty()) {
                country = country.toUpperCase(US);
                if (country.length() == 2) {
                    return country;
                }
            }
        }

        // Do we have multiple known regions for this locale? If so, try to pick
        // among them based on heuristics.
        List<String> regions = getRelevantRegions(languageCode);
        if (regions.size() > 1) {
            // (3) Check the user's country. The user may not be using the target
            //     language, but if the current country matches one of the relevant
            //     regions, use it.
            String country = locale.getCountry();
            if (!country.isEmpty()) {
                country = country.toUpperCase(US);
                if (country.length() == 2 && regions.contains(country)) {
                    return country;
                }
            }

            // (4) Look at the user's network location; if we can resolve
            //     the domain name, the TLD might be an ISO 3166 country code:
            //     http://en.wikipedia.org/wiki/Country_code_top-level_domain
            //     If so, and that country code is in one of the candidate regions,
            //     use it. (Note the exceptions listed in there; we should treat
            //     "uk" as "gb" for ISO code lookup.)
            //
            //   NOTE DONE: It turns out this is tricky. Looking up the current domain
            //     typically requires a network connection, sometimes it can
            //     take seconds, and even the domain name may not be helpful;
            //     it may be for example a .com address.


            // (5) Use the timezone! The timezone can give us a very good clue
            //     about the region. In many cases we can get an exact match,
            //     e.g. if we're looking at the timezone Europe/Lisbon we know
            //     the region is PT. (In the future we could extend this to
            //     not only map from timezone to region code, but to look at
            //     the continent and raw offsets for further clues to guide the
            //     region choice.)
            String region = getTimeZoneRegion(TimeZone.getDefault());
            if (region != null && regions.contains(region)) {
                return region;
            }

            //
            // (6) Look at installed locales, and limit our options to the regions
            //     found in locales for the given language.
            //     For example, on my system, the LocaleManager provides 90
            //     relevant regions for English, but my system only has 11,
            //     so we can eliminate the remaining 79 from consideration.
            //     (Sadly, it doesn't look like the local locales are sorted
            //     in any way significant for the user, so we can't just assume
            //     that the first locale of the target language is somehow special.)
            Locale candidate = null;
            for (Locale available : Locale.getAvailableLocales()) {
                if (languageCode.equals(available.getLanguage()) &&
                        regions.contains(available.getCountry())) {
                    if (candidate != null) {
                        candidate = null; // more than one match; doesn't help us
                        break;
                    } else {
                        candidate = available;
                    }
                }
            }
            if (candidate != null) {
                return candidate.getCountry();
            }

            //
            // (7) Consult the project to see which locales are used there.
            //     If for example your project has resources in "en-rUS", it's
            //     unlikely that you intend for "US" to be the region for en
            //     (since that's a region specific overlay) so pick for example GB
            //     instead.
            if (resources != null) {
                ListMultimap<String, com.android.ide.common.res2.ResourceItem> strings = resources
                        .getItems().get(ResourceType.STRING);
                if (strings != null) {
                    Set<String> specified = Sets.newHashSet();
                    for (com.android.ide.common.res2.ResourceItem item : strings.values()) {
                        String qualifiers = item.getQualifiers();
                        if (qualifiers.startsWith(languageCode) && qualifiers.length() == 6
                                && qualifiers.charAt(3) == 'r') {
                            specified.add(qualifiers.substring(4));
                        }
                    }
                    if (!specified.isEmpty()) {
                        // Remove the specified locales from consideration
                        Set<String> all = Sets.newHashSet(regions);
                        all.removeAll(specified);
                        // Only one left?
                        if (all.size() == 1) {
                            return all.iterator().next();
                        }
                    }
                }
            }

            //
            // (8) Give preference to a region that has the same region code
            //     as the language code; this is usually where the language is named
            //     after a region
            char first = Character.toUpperCase(languageCode.charAt(0));
            char second = Character.toUpperCase(languageCode.charAt(1));
            for (String r : regions) {
                if (r.charAt(0) == first && r.charAt(1) == second) {
                    return r;
                }
            }
        } else if (regions.size() == 1) {
            return regions.get(0);
        }

        // Finally just pick the default one
        return sLanguageToCountry.get(languageCode);
    }

    /**
     * Populate the various maps.
     * <p>
     * The language to region mapping was constructed by using the ISO 639-1 table from
     * http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
     * and for each language, looking up the corresponding Wikipedia entry
     * and picking the first mentioned or in some cases largest country where
     * the language is spoken, then mapping that back to the corresponding ISO 3166-1
     * code.
     */
    static {
        // Afar -> Ethiopia
        sLanguageToCountry.put("aa", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
        sLanguageNames.put("aa", "Afar"); //$NON-NLS-1$

         // "ab": Abkhaz -> Abkhazia, Georgia
         sLanguageToCountry.put("ab", "GE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ab", "Abkhaz"); //$NON-NLS-1$

         // "af": Afrikaans  -> South Africa, Namibia
         sLanguageToCountry.put("af", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("af", "Afrikaans"); //$NON-NLS-1$

         // "ak": Akan -> Ghana, Ivory Coast
         sLanguageToCountry.put("ak", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ak", "Akan"); //$NON-NLS-1$

         // "am": Amharic -> Ethiopia
         sLanguageToCountry.put("am", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("am", "Amharic"); //$NON-NLS-1$

         // "an": Aragonese  -> Aragon in Spain
         sLanguageToCountry.put("an", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("an", "Aragonese"); //$NON-NLS-1$

         // "ar": Arabic -> United Arab Emirates, Kuwait, Oman, Saudi Arabia, Qatar, and Bahrain
         sLanguageToCountry.put("ar", "AE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ar", "Arabic"); //$NON-NLS-1$

         // "as": Assamese -> India
         sLanguageToCountry.put("as", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("as", "Assamese"); //$NON-NLS-1$

         // "av": Avaric -> Azerbaijan
         sLanguageToCountry.put("av", "AZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("av", "Avaric"); //$NON-NLS-1$

         // "ay": Aymara -> Bolivia
         sLanguageToCountry.put("ay", "BO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ay", "Aymara"); //$NON-NLS-1$

         // "az": Azerbaijani -> Azerbaijan
         sLanguageToCountry.put("az", "AZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("az", "Azerbaijani"); //$NON-NLS-1$

         // "ba": Bashkir -> Russia
         sLanguageToCountry.put("ba", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ba", "Bashkir"); //$NON-NLS-1$

         // "be": Belarusian -> Belarus
         sLanguageToCountry.put("be", "BY"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("be", "Belarusian"); //$NON-NLS-1$

         // "bg": Bulgarian -> Bulgaria
         sLanguageToCountry.put("bg", "BG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bg", "Bulgarian"); //$NON-NLS-1$

         // "bh": Bihari languages -> India, Nepal
         sLanguageToCountry.put("bh", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bh", "Bihari languages"); //$NON-NLS-1$

         // "bi": Bislama -> Vanatu
         sLanguageToCountry.put("bi", "VU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bi", "Bislama"); //$NON-NLS-1$

         // "bm": Bambara -> Mali
         sLanguageToCountry.put("bm", "ML"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bm", "Bambara"); //$NON-NLS-1$

         // "bn": Bengali -> Bangladesh, India
         sLanguageToCountry.put("bn", "BD"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bn", "Bengali"); //$NON-NLS-1$

         // "bo": Tibetan -> China
         sLanguageToCountry.put("bo", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bo", "Tibetan"); //$NON-NLS-1$

         // "br": Breton -> France
         sLanguageToCountry.put("br", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("br", "Breton"); //$NON-NLS-1$

         // "bs": Bosnian -> Bosnia and Herzegovina
         sLanguageToCountry.put("bs", "BA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("bs", "Bosnian"); //$NON-NLS-1$

         // "ca": Catalan -> Andorra, Catalonia
         //sLanguageToCountry.put("ca", "AD"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ca", "Catalan"); //$NON-NLS-1$

         // "ce": Chechen -> Russia
         sLanguageToCountry.put("ce", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ce", "Chechen"); //$NON-NLS-1$

         // "ch": Chamorro -> Guam, Northern Mariana Islands
         sLanguageToCountry.put("ch", "GU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ch", "Chamorro"); //$NON-NLS-1$

         // "co": Corsican -> France
         sLanguageToCountry.put("co", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("co", "Corsican"); //$NON-NLS-1$

         // "cr": Cree -> Canada and United States
         sLanguageToCountry.put("cr", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("cr", "Cree"); //$NON-NLS-1$

         // "cs": Czech -> Czech Republic
         sLanguageToCountry.put("cs", "CZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("cs", "Czech"); //$NON-NLS-1$

         // "cv": Chuvash -> Russia, Kazakhstan, Ukraine, Uzbekistan...
         sLanguageToCountry.put("cv", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("cv", "Chuvash"); //$NON-NLS-1$

         // "cy": Welsh -> Wales (no 3166 code; using GB)
         sLanguageToCountry.put("cy", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("cy", "Welsh"); //$NON-NLS-1$

         // "da": Danish -> Denmark
         sLanguageToCountry.put("da", "DK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("da", "Danish"); //$NON-NLS-1$

         // "de": German -> Germany
         sLanguageToCountry.put("de", "DE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("de", "German"); //$NON-NLS-1$

         // "dv": Divehi -> Maldives
         sLanguageToCountry.put("dv", "MV"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("dv", "Divehi"); //$NON-NLS-1$

         // "dz": Dzongkha -> Bhutan
         sLanguageToCountry.put("dz", "BT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("dz", "Dzongkha"); //$NON-NLS-1$

         // "ee": Ewe -> Ghana, Togo
         sLanguageToCountry.put("ee", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ee", "Ewe"); //$NON-NLS-1$

         // "el": Greek -> Greece
         sLanguageToCountry.put("el", "GR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("el", "Greek"); //$NON-NLS-1$

         // "en": English -> United States, United Kingdom, Australia, ...
         sLanguageToCountry.put("en", "US"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("en", "English"); //$NON-NLS-1$

         // "es": Spanish -> Spain, Mexico, ...
         sLanguageToCountry.put("es", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("es", "Spanish"); //$NON-NLS-1$

         // "et": Estonian ->
         sLanguageToCountry.put("et", "EE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("et", "Estonian"); //$NON-NLS-1$

         // "eu": Basque -> Spain, France
         sLanguageToCountry.put("eu", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("eu", "Basque"); //$NON-NLS-1$

         // "fa": Persian -> Iran, Afghanistan
         sLanguageToCountry.put("fa", "IR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fa", "Persian"); //$NON-NLS-1$

         // "ff": Fulah -> Senegal, Mauritania, Mali, Guinea, Burkina Faso, ...
         sLanguageToCountry.put("ff", "SN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ff", "Fulah"); //$NON-NLS-1$

         // "fi": Finnish -> Finland
         sLanguageToCountry.put("fi", "FI"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fi", "Finnish"); //$NON-NLS-1$

         // "fj": Fijian -> Fiji
         sLanguageToCountry.put("fj", "FJ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fj", "Fijian"); //$NON-NLS-1$

         // "fo": Faroese -> Faroe Islands
         sLanguageToCountry.put("fo", "FO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fo", "Faroese"); //$NON-NLS-1$

         // "fr": French -> France
         sLanguageToCountry.put("fr", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fr", "French"); //$NON-NLS-1$

         // "fy": Western Frisian -> Netherlands
         sLanguageToCountry.put("fy", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("fy", "Western Frisian"); //$NON-NLS-1$

         // "ga": Irish -> Ireland
         sLanguageToCountry.put("ga", "IE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ga", "Irish"); //$NON-NLS-1$

         // "gd": Gaelic -> Scotland
         sLanguageToCountry.put("gd", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("gd", "Gaelic"); //$NON-NLS-1$

         // "gl": Galician -> Galicia/Spain
         sLanguageToCountry.put("gl", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("gl", "Galician"); //$NON-NLS-1$

         // "gn": Guaraní -> Paraguay
         sLanguageToCountry.put("gn", "PY"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("gn", "Guaran\u00ed" /*Guaraní*/); //$NON-NLS-1$

         // "gu": Gujarati -> India
         sLanguageToCountry.put("gu", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("gu", "Gujarati"); //$NON-NLS-1$

         // "gv": Manx -> Isle of Man
         sLanguageToCountry.put("gv", "IM"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("gv", "Manx"); //$NON-NLS-1$

         // "ha": Hausa -> Nigeria, Niger
         sLanguageToCountry.put("ha", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ha", "Hausa"); //$NON-NLS-1$

         // "he": Hebrew -> Israel
         sLanguageToCountry.put("he", "IL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("he", "Hebrew"); //$NON-NLS-1$

         // "hi": Hindi -> India
         sLanguageToCountry.put("hi", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("hi", "Hindi"); //$NON-NLS-1$

         // "ho": Hiri Motu -> Papua New Guinea
         sLanguageToCountry.put("ho", "PG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ho", "Hiri Motu"); //$NON-NLS-1$

         // "hr": Croatian ->
         sLanguageToCountry.put("hr", "HR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("hr", "Croatian"); //$NON-NLS-1$

         // "ht": Haitian -> Haiti
         sLanguageToCountry.put("ht", "HT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ht", "Haitian"); //$NON-NLS-1$

         // "hu": Hungarian -> Hungary
         sLanguageToCountry.put("hu", "HU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("hu", "Hungarian"); //$NON-NLS-1$

         // "hy": Armenian -> Armenia
         sLanguageToCountry.put("hy", "AM"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("hy", "Armenian"); //$NON-NLS-1$

         // "hz": Herero -> Namibia, Botswana
         sLanguageToCountry.put("hz", "NA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("hz", "Herero"); //$NON-NLS-1$

         // "id": Indonesian -> Indonesia
         sLanguageToCountry.put("id", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("id", "Indonesian"); //$NON-NLS-1$

         // "ig": Igbo ->
         sLanguageToCountry.put("ig", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ig", "Igbo"); //$NON-NLS-1$

         // "ii": Nuosu -> China
         sLanguageToCountry.put("ii", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ii", "Nuosu"); //$NON-NLS-1$

         // "ik": Inupiaq -> USA
         sLanguageToCountry.put("ik", "US"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ik", "Inupiaq"); //$NON-NLS-1$

         // "is": Icelandic -> Iceland
         sLanguageToCountry.put("is", "IS"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("is", "Icelandic"); //$NON-NLS-1$

         // "it": Italian -> Italy
         sLanguageToCountry.put("it", "IT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("it", "Italian"); //$NON-NLS-1$

         // "iu": Inuktitut -> Canada
         sLanguageToCountry.put("iu", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("iu", "Inuktitut"); //$NON-NLS-1$

         // "ja": Japanese -> Japan
         sLanguageToCountry.put("ja", "JP"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ja", "Japanese"); //$NON-NLS-1$

         // "jv": Javanese -> Indonesia
         sLanguageToCountry.put("jv", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("jv", "Javanese"); //$NON-NLS-1$

         // "ka": Georgian -> Georgia
         sLanguageToCountry.put("ka", "GE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ka", "Georgian"); //$NON-NLS-1$

         // "kg": Kongo -> Angola, Congo
         sLanguageToCountry.put("kg", "AO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kg", "Kongo"); //$NON-NLS-1$

         // "ki": Kikuyu -> Kenya
         sLanguageToCountry.put("ki", "KE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ki", "Kikuyu"); //$NON-NLS-1$

         // "kj": Kwanyama -> Angola, Namibia
         sLanguageToCountry.put("kj", "AO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kj", "Kwanyama"); //$NON-NLS-1$

         // "kk": Kazakh -> Kazakhstan
         sLanguageToCountry.put("kk", "KZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kk", "Kazakh"); //$NON-NLS-1$

         // "kl": Kalaallisut -> Greenland
         sLanguageToCountry.put("kl", "GL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kl", "Kalaallisut"); //$NON-NLS-1$

         // "km": Khmer -> Cambodia
         sLanguageToCountry.put("km", "KH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("km", "Khmer"); //$NON-NLS-1$

         // "kn": Kannada -> India
         sLanguageToCountry.put("kn", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kn", "Kannada"); //$NON-NLS-1$

         // "ko": Korean -> Korea
         sLanguageToCountry.put("ko", "KR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ko", "Korean"); //$NON-NLS-1$

         // "kr": Kanuri -> Nigeria
         sLanguageToCountry.put("kr", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kr", "Kanuri"); //$NON-NLS-1$

         // "ks": Kashmiri -> India
         sLanguageToCountry.put("ks", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ks", "Kashmiri"); //$NON-NLS-1$

         // "ku": Kurdish -> Maps to multiple ISO 3166 codes
         sLanguageNames.put("ku", "Kurdish"); //$NON-NLS-1$

         // "kv": Komi -> Russia
         sLanguageToCountry.put("kv", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kv", "Komi"); //$NON-NLS-1$

         // "kw": Cornish -> UK
         sLanguageToCountry.put("kw", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("kw", "Cornish"); //$NON-NLS-1$

         // "ky": Kyrgyz -> Kyrgyzstan
         sLanguageToCountry.put("ky", "KG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ky", "Kyrgyz"); //$NON-NLS-1$

         // "lb": Luxembourgish -> Luxembourg
         sLanguageToCountry.put("lb", "LU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lb", "Luxembourgish"); //$NON-NLS-1$

         // "lg": Ganda -> Uganda
         sLanguageToCountry.put("lg", "UG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lg", "Ganda"); //$NON-NLS-1$

         // "li": Limburgish -> Netherlands
         sLanguageToCountry.put("li", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("li", "Limburgish"); //$NON-NLS-1$

         // "ln": Lingala -> Congo
         sLanguageToCountry.put("ln", "CD"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ln", "Lingala"); //$NON-NLS-1$

         // "lo": Lao -> Laos
         sLanguageToCountry.put("lo", "LA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lo", "Lao"); //$NON-NLS-1$

         // "lt": Lithuanian -> Lithuania
         sLanguageToCountry.put("lt", "LT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lt", "Lithuanian"); //$NON-NLS-1$

         // "lu": Luba-Katanga -> Congo
         sLanguageToCountry.put("lu", "CD"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lu", "Luba-Katanga"); //$NON-NLS-1$

         // "lv": Latvian -> Latvia
         sLanguageToCountry.put("lv", "LV"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("lv", "Latvian"); //$NON-NLS-1$

         // "mg": Malagasy -> Madagascar
         sLanguageToCountry.put("mg", "MG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mg", "Malagasy"); //$NON-NLS-1$

         // "mh": Marshallese -> Marshall Islands
         sLanguageToCountry.put("mh", "MH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mh", "Marshallese"); //$NON-NLS-1$

         // "mi": Maori -> New Zealand
         sLanguageToCountry.put("mi", "NZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mi", "M\u0101ori"); //$NON-NLS-1$

         // "mk": Macedonian -> Macedonia
         sLanguageToCountry.put("mk", "MK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mk", "Macedonian"); //$NON-NLS-1$

         // "ml": Malayalam -> India
         sLanguageToCountry.put("ml", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ml", "Malayalam"); //$NON-NLS-1$

         // "mn": Mongolian -> Mongolia
         sLanguageToCountry.put("mn", "MN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mn", "Mongolian"); //$NON-NLS-1$

         // "mr": Marathi -> India
         sLanguageToCountry.put("mr", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mr", "Marathi"); //$NON-NLS-1$

         // "ms": Malay -> Malaysia, Indonesia ...
         sLanguageToCountry.put("ms", "MY"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ms", "Malay"); //$NON-NLS-1$

         // "mt": Maltese -> Malta
         sLanguageToCountry.put("mt", "MT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("mt", "Maltese"); //$NON-NLS-1$

         // "my": Burmese -> Myanmar
         sLanguageToCountry.put("my", "MM"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("my", "Burmese"); //$NON-NLS-1$

         // "na": Nauru -> Nauru
         sLanguageToCountry.put("na", "NR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("na", "Nauru"); //$NON-NLS-1$

         // "nb": Norwegian -> Norway
         sLanguageToCountry.put("nb", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nb", "Norwegian Bokm\u00e5l" /*Norwegian Bokmål*/); //$NON-NLS-1$

         // "nd": North Ndebele -> Zimbabwe
         sLanguageToCountry.put("nd", "ZW"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nd", "North Ndebele"); //$NON-NLS-1$

         // "ne": Nepali -> Nepal
         sLanguageToCountry.put("ne", "NP"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ne", "Nepali"); //$NON-NLS-1$

         // "ng":Ndonga  -> Namibia
         sLanguageToCountry.put("ng", "NA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ng", "Ndonga"); //$NON-NLS-1$

         // "nl": Dutch -> Netherlands
         sLanguageToCountry.put("nl", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nl", "Dutch"); //$NON-NLS-1$

         // "nn": Norwegian Nynorsk -> Norway
         sLanguageToCountry.put("nn", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nn", "Norwegian Nynorsk"); //$NON-NLS-1$

         // "no": Norwegian -> Norway
         sLanguageToCountry.put("no", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("no", "Norwegian"); //$NON-NLS-1$

         // "nr": South Ndebele -> South Africa
         sLanguageToCountry.put("nr", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nr", "South Ndebele"); //$NON-NLS-1$

         // "nv": Navajo -> USA
         sLanguageToCountry.put("nv", "US"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("nv", "Navajo"); //$NON-NLS-1$

         // "ny": Chichewa -> Malawi, Zambia
         sLanguageToCountry.put("ny", "MW"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ny", "Chichewa"); //$NON-NLS-1$

         // "oc": Occitan -> France, Italy, Spain, Monaco
         sLanguageToCountry.put("oc", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("oc", "Occitan"); //$NON-NLS-1$

         // "oj": Ojibwe -> Canada, United States
         sLanguageToCountry.put("oj", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("oj", "Ojibwe"); //$NON-NLS-1$

         // "om": Oromo -> Ethiopia
         sLanguageToCountry.put("om", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("om", "Oromo"); //$NON-NLS-1$

         // "or": Oriya -> India
         sLanguageToCountry.put("or", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("or", "Oriya"); //$NON-NLS-1$

         // "os": Ossetian -> Russia (North Ossetia), Georgia
         sLanguageToCountry.put("os", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("os", "Ossetian"); //$NON-NLS-1$

         // "pa": Panjabi, -> Pakistan, India
         sLanguageToCountry.put("pa", "PK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("pa", "Panjabi"); //$NON-NLS-1$

         // "pl": Polish -> Poland
         sLanguageToCountry.put("pl", "PL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("pl", "Polish"); //$NON-NLS-1$

         // "ps": Pashto -> Afghanistan, Pakistan
         sLanguageToCountry.put("ps", "AF"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ps", "Pashto"); //$NON-NLS-1$

         // "pt": Portuguese -> Portugal, Brazil, ...
         sLanguageToCountry.put("pt", "PT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("pt", "Portuguese"); //$NON-NLS-1$

         // "qu": Quechua -> Peru, Bolivia
         sLanguageToCountry.put("qu", "PE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("qu", "Quechua"); //$NON-NLS-1$

         // "rm": Romansh -> Switzerland
         sLanguageToCountry.put("rm", "CH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("rm", "Romansh"); //$NON-NLS-1$

         // "rn": Kirundi -> Burundi, Uganda
         sLanguageToCountry.put("rn", "BI"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("rn", "Kirundi"); //$NON-NLS-1$

         // "ro": Romanian -> Romania, Republic of Moldova
         sLanguageToCountry.put("ro", "RO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ro", "Romanian"); //$NON-NLS-1$

         // "ru": Russian -> Russia
         sLanguageToCountry.put("ru", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ru", "Russian"); //$NON-NLS-1$

         // "rw": Kinyarwanda -> Rwanda, Uganda, Democratic Republic of the Congo
         sLanguageToCountry.put("rw", "RW"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("rw", "Kinyarwanda"); //$NON-NLS-1$

         // "sa": Sanskrit -> India
         sLanguageToCountry.put("sa", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sa", "Sanskrit"); //$NON-NLS-1$

         // "sc": Sardinian -> Italy
         sLanguageToCountry.put("sc", "IT"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sc", "Sardinian"); //$NON-NLS-1$

         // "sd": Sindhi -> Pakistan, India
         sLanguageToCountry.put("sd", "PK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sd", "Sindhi"); //$NON-NLS-1$

         // "se": Northern Sami -> Norway, Sweden, Finland
         sLanguageToCountry.put("se", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("se", "Northern Sami"); //$NON-NLS-1$

         // "sg": Sango -> Central African Republic
         sLanguageToCountry.put("sg", "CF"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sg", "Sango"); //$NON-NLS-1$

         // "si": Sinhala ->  Sri Lanka
         sLanguageToCountry.put("si", "LK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("si", "Sinhala"); //$NON-NLS-1$

         // "sk": Slovak -> Slovakia
         sLanguageToCountry.put("sk", "SK"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sk", "Slovak"); //$NON-NLS-1$

         // "sl": Slovene -> Slovenia
         sLanguageToCountry.put("sl", "SI"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sl", "Slovene"); //$NON-NLS-1$

         // "sm": Samoan -> Samoa
         sLanguageToCountry.put("sm", "WS"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sm", "Samoan"); //$NON-NLS-1$

         // "sn": Shona -> Zimbabwe
         sLanguageToCountry.put("sn", "ZW"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sn", "Shona"); //$NON-NLS-1$

         // "so": Somali -> Somalia
         sLanguageToCountry.put("so", "SO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("so", "Somali"); //$NON-NLS-1$

         // "sq": Albanian -> Albania
         sLanguageToCountry.put("sq", "AL"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sq", "Albanian"); //$NON-NLS-1$

         // "sr": Serbian -> Serbia, Bosnia and Herzegovina
         sLanguageToCountry.put("sr", "RS"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sr", "Serbian"); //$NON-NLS-1$

         // "ss": Swati -> Swaziland
         sLanguageToCountry.put("ss", "SZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ss", "Swati"); //$NON-NLS-1$

         // "st": Southern Sotho -> Lesotho, South Africa
         sLanguageToCountry.put("st", "LS"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("st", "Southern Sotho"); //$NON-NLS-1$

         // "su": Sundanese -> Indoniesia
         sLanguageToCountry.put("su", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("su", "Sundanese"); //$NON-NLS-1$

         // "sv": Swedish -> Sweden
         sLanguageToCountry.put("sv", "SE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sv", "Swedish"); //$NON-NLS-1$

         // "sw": Swahili -> Tanzania, Kenya, and Congo (DRC)
         sLanguageToCountry.put("sw", "TZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("sw", "Swahili"); //$NON-NLS-1$

         // "ta": Tamil -> India, Sri Lanka
         sLanguageToCountry.put("ta", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ta", "Tamil"); //$NON-NLS-1$

         // "te": Telugu -> India
         sLanguageToCountry.put("te", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("te", "Telugu"); //$NON-NLS-1$

         // "tg": Tajik -> Tajikistan, Uzbekistan, Russia, Afghanistan
         sLanguageToCountry.put("tg", "TJ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tg", "Tajik"); //$NON-NLS-1$

         // "th": Thai -> Thailand
         sLanguageToCountry.put("th", "TH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("th", "Thai"); //$NON-NLS-1$

         // "ti": Tigrinya -> Eritrea, Ethiopia
         sLanguageToCountry.put("ti", "ER"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ti", "Tigrinya"); //$NON-NLS-1$

         // "tk": Turkmen -> Turkmenistan
         sLanguageToCountry.put("tk", "TM"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tk", "Turkmen"); //$NON-NLS-1$

         // "tl": Tagalog -> Philippines
         sLanguageToCountry.put("tl", "PH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tl", "Tagalog"); //$NON-NLS-1$

         // "tn": Tswana -> Botswana, South Africa,
         sLanguageToCountry.put("tn", "BW"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tn", "Tswana"); //$NON-NLS-1$

         // "to": Tonga -> Tonga
         sLanguageToCountry.put("to", "TO"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("to", "Tonga"); //$NON-NLS-1$

         // "tr": Turkish -> Turkey
         sLanguageToCountry.put("tr", "TR"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tr", "Turkish"); //$NON-NLS-1$

         // "ts": Tsonga -> Mozambique, South Africa
         sLanguageToCountry.put("ts", "MZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ts", "Tsonga"); //$NON-NLS-1$

         // "tt": Tatar -> Russia
         sLanguageToCountry.put("tt", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tt", "Tatar"); //$NON-NLS-1$

         // "tw": Twi -> Ghana, Ivory Coast
         sLanguageToCountry.put("tw", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("tw", "Twi"); //$NON-NLS-1$

         // "ty": Tahitian -> French Polynesia
         sLanguageToCountry.put("ty", "PF"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ty", "Tahitian"); //$NON-NLS-1$

         // "ug": Uighur -> China, Kazakhstan
         sLanguageToCountry.put("ug", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ug", "Uighur"); //$NON-NLS-1$

         // "uk": Ukrainian -> Ukraine
         sLanguageToCountry.put("uk", "UA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("uk", "Ukrainian"); //$NON-NLS-1$

         // "ur": Urdu -> India, Pakistan
         sLanguageToCountry.put("ur", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ur", "Urdu"); //$NON-NLS-1$

         // "uz": Uzbek -> Uzbekistan
         sLanguageToCountry.put("uz", "UZ"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("uz", "Uzbek"); //$NON-NLS-1$

         // "ve": Venda -> South Africa, Zimbabwe
         sLanguageToCountry.put("ve", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ve", "Venda"); //$NON-NLS-1$

         // "vi": Vietnamese -> Vietnam
         sLanguageToCountry.put("vi", "VN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("vi", "Vietnamese"); //$NON-NLS-1$

         // "wa": Walloon -> Belgium, France
         sLanguageToCountry.put("wa", "BE"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("wa", "Walloon"); //$NON-NLS-1$

         // "wo": Wolof -> Senegal, Gambia, Mauritania
         sLanguageToCountry.put("wo", "SN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("wo", "Wolof"); //$NON-NLS-1$

         // "xh": Xhosa -> South Africa, Lesotho
         sLanguageToCountry.put("xh", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("xh", "Xhosa"); //$NON-NLS-1$

         // "yi": Yiddish -> United States, Israel, Argentina, Brazil, ...
         sLanguageToCountry.put("yi", "US"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("yi", "Yiddish"); //$NON-NLS-1$

         // "yo": Yorùbá -> Nigeria, Togo, Benin
         sLanguageToCountry.put("yo", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("yo", "Yor\u00f9b\u00e1" /*Yorùbá*/); //$NON-NLS-1$

         // "za": Zhuang -> China
         sLanguageToCountry.put("za", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("za", "Zhuang"); //$NON-NLS-1$

         // "zh": Chinese -> China, Taiwan, Singapore
         sLanguageToCountry.put("zh", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("zh", "Chinese"); //$NON-NLS-1$

         // "zu": Zulu -> South Africa
         sLanguageToCountry.put("zu", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("zu", "Zulu"); //$NON-NLS-1$

         // Region Name Map, ISO_3166-1, alpha-2
         sRegionNames.put("AF", "Afghanistan");                                       //$NON-NLS-1$
         sRegionNames.put("AL", "Albania");                                           //$NON-NLS-1$
         sRegionNames.put("DZ", "Algeria");                                           //$NON-NLS-1$
         sRegionNames.put("AS", "American Samoa");                                    //$NON-NLS-1$
         sRegionNames.put("AD", "Andorra");                                           //$NON-NLS-1$
         sRegionNames.put("AO", "Angola");                                            //$NON-NLS-1$
         sRegionNames.put("AI", "Anguilla");                                          //$NON-NLS-1$
         sRegionNames.put("AQ", "Antarctica");                                        //$NON-NLS-1$
         sRegionNames.put("AG", "Antigua and Barbuda");                               //$NON-NLS-1$
         sRegionNames.put("AR", "Argentina");                                         //$NON-NLS-1$
         sRegionNames.put("AM", "Armenia");                                           //$NON-NLS-1$
         sRegionNames.put("AW", "Aruba");                                             //$NON-NLS-1$
         sRegionNames.put("AU", "Australia");                                         //$NON-NLS-1$
         sRegionNames.put("AT", "Austria");                                           //$NON-NLS-1$
         sRegionNames.put("AZ", "Azerbaijan");                                        //$NON-NLS-1$
         sRegionNames.put("BS", "Bahamas");                                           //$NON-NLS-1$
         sRegionNames.put("BH", "Bahrain");                                           //$NON-NLS-1$
         sRegionNames.put("BD", "Bangladesh");                                        //$NON-NLS-1$
         sRegionNames.put("BB", "Barbados");                                          //$NON-NLS-1$
         sRegionNames.put("BY", "Belarus");                                           //$NON-NLS-1$
         sRegionNames.put("BE", "Belgium");                                           //$NON-NLS-1$
         sRegionNames.put("BZ", "Belize");                                            //$NON-NLS-1$
         sRegionNames.put("BJ", "Benin");                                             //$NON-NLS-1$
         sRegionNames.put("BM", "Bermuda");                                           //$NON-NLS-1$
         sRegionNames.put("BT", "Bhutan");                                            //$NON-NLS-1$
         sRegionNames.put("BO", "Bolivia, Plurinational State of");                   //$NON-NLS-1$
         sRegionNames.put("BQ", "Bonaire, Sint Eustatius and Saba");                  //$NON-NLS-1$
         sRegionNames.put("BA", "Bosnia and Herzegovina");                            //$NON-NLS-1$
         sRegionNames.put("BW", "Botswana");                                          //$NON-NLS-1$
         sRegionNames.put("BV", "Bouvet Island");                                     //$NON-NLS-1$
         sRegionNames.put("BR", "Brazil");                                            //$NON-NLS-1$
         sRegionNames.put("IO", "British Indian Ocean Territory");                    //$NON-NLS-1$
         sRegionNames.put("BN", "Brunei Darussalam");                                 //$NON-NLS-1$
         sRegionNames.put("BG", "Bulgaria");                                          //$NON-NLS-1$
         sRegionNames.put("BF", "Burkina Faso");                                      //$NON-NLS-1$
         sRegionNames.put("BI", "Burundi");                                           //$NON-NLS-1$
         sRegionNames.put("KH", "Cambodia");                                          //$NON-NLS-1$
         sRegionNames.put("CM", "Cameroon");                                          //$NON-NLS-1$
         sRegionNames.put("CA", "Canada");                                            //$NON-NLS-1$
         sRegionNames.put("CV", "Cape Verde");                                        //$NON-NLS-1$
         sRegionNames.put("KY", "Cayman Islands");                                    //$NON-NLS-1$
         sRegionNames.put("CF", "Central African Republic");                          //$NON-NLS-1$
         sRegionNames.put("TD", "Chad");                                              //$NON-NLS-1$
         sRegionNames.put("CL", "Chile");                                             //$NON-NLS-1$
         sRegionNames.put("CN", "China");                                             //$NON-NLS-1$
         sRegionNames.put("CX", "Christmas Island");                                  //$NON-NLS-1$
         sRegionNames.put("CC", "Cocos (Keeling) Islands");                           //$NON-NLS-1$
         sRegionNames.put("CO", "Colombia");                                          //$NON-NLS-1$
         sRegionNames.put("KM", "Comoros");                                           //$NON-NLS-1$
         sRegionNames.put("CG", "Congo");                                             //$NON-NLS-1$
         sRegionNames.put("CD", "Congo, the Democratic Republic of the");             //$NON-NLS-1$
         sRegionNames.put("CK", "Cook Islands");                                      //$NON-NLS-1$
         sRegionNames.put("CR", "Costa Rica");                                        //$NON-NLS-1$
         sRegionNames.put("HR", "Croatia");                                           //$NON-NLS-1$
         sRegionNames.put("CU", "Cuba");                                              //$NON-NLS-1$
         sRegionNames.put("CW", "Cura\u00e7ao");                                      //$NON-NLS-1$
         sRegionNames.put("CY", "Cyprus");                                            //$NON-NLS-1$
         sRegionNames.put("CZ", "Czech Republic");                                    //$NON-NLS-1$
         sRegionNames.put("CI", "C\u00f4te d'Ivoire");                                //$NON-NLS-1$
         sRegionNames.put("DK", "Denmark");                                           //$NON-NLS-1$
         sRegionNames.put("DJ", "Djibouti");                                          //$NON-NLS-1$
         sRegionNames.put("DM", "Dominica");                                          //$NON-NLS-1$
         sRegionNames.put("DO", "Dominican Republic");                                //$NON-NLS-1$
         sRegionNames.put("EC", "Ecuador");                                           //$NON-NLS-1$
         sRegionNames.put("EG", "Egypt");                                             //$NON-NLS-1$
         sRegionNames.put("SV", "El Salvador");                                       //$NON-NLS-1$
         sRegionNames.put("GQ", "Equatorial Guinea");                                 //$NON-NLS-1$
         sRegionNames.put("ER", "Eritrea");                                           //$NON-NLS-1$
         sRegionNames.put("EE", "Estonia");                                           //$NON-NLS-1$
         sRegionNames.put("ET", "Ethiopia");                                          //$NON-NLS-1$
         sRegionNames.put("FK", "Falkland Islands (Malvinas)");                       //$NON-NLS-1$
         sRegionNames.put("FO", "Faroe Islands");                                     //$NON-NLS-1$
         sRegionNames.put("FJ", "Fiji");                                              //$NON-NLS-1$
         sRegionNames.put("FI", "Finland");                                           //$NON-NLS-1$
         sRegionNames.put("FR", "France");                                            //$NON-NLS-1$
         sRegionNames.put("GF", "French Guiana");                                     //$NON-NLS-1$
         sRegionNames.put("PF", "French Polynesia");                                  //$NON-NLS-1$
         sRegionNames.put("TF", "French Southern Territories");                       //$NON-NLS-1$
         sRegionNames.put("GA", "Gabon");                                             //$NON-NLS-1$
         sRegionNames.put("GM", "Gambia");                                            //$NON-NLS-1$
         sRegionNames.put("GE", "Georgia");                                           //$NON-NLS-1$
         sRegionNames.put("DE", "Germany");                                           //$NON-NLS-1$
         sRegionNames.put("GH", "Ghana");                                             //$NON-NLS-1$
         sRegionNames.put("GI", "Gibraltar");                                         //$NON-NLS-1$
         sRegionNames.put("GR", "Greece");                                            //$NON-NLS-1$
         sRegionNames.put("GL", "Greenland");                                         //$NON-NLS-1$
         sRegionNames.put("GD", "Grenada");                                           //$NON-NLS-1$
         sRegionNames.put("GP", "Guadeloupe");                                        //$NON-NLS-1$
         sRegionNames.put("GU", "Guam");                                              //$NON-NLS-1$
         sRegionNames.put("GT", "Guatemala");                                         //$NON-NLS-1$
         sRegionNames.put("GG", "Guernsey");                                          //$NON-NLS-1$
         sRegionNames.put("GN", "Guinea");                                            //$NON-NLS-1$
         sRegionNames.put("GW", "Guinea-Bissau");                                     //$NON-NLS-1$
         sRegionNames.put("GY", "Guyana");                                            //$NON-NLS-1$
         sRegionNames.put("HT", "Haiti");                                             //$NON-NLS-1$
         sRegionNames.put("HM", "Heard Island and McDonald Islands");                 //$NON-NLS-1$
         sRegionNames.put("VA", "Holy See (Vatican City State)");                     //$NON-NLS-1$
         sRegionNames.put("HN", "Honduras");                                          //$NON-NLS-1$
         sRegionNames.put("HK", "Hong Kong");                                         //$NON-NLS-1$
         sRegionNames.put("HU", "Hungary");                                           //$NON-NLS-1$
         sRegionNames.put("IS", "Iceland");                                           //$NON-NLS-1$
         sRegionNames.put("IN", "India");                                             //$NON-NLS-1$
         sRegionNames.put("ID", "Indonesia");                                         //$NON-NLS-1$
         sRegionNames.put("IR", "Iran, Islamic Republic of");                         //$NON-NLS-1$
         sRegionNames.put("IQ", "Iraq");                                              //$NON-NLS-1$
         sRegionNames.put("IE", "Ireland");                                           //$NON-NLS-1$
         sRegionNames.put("IM", "Isle of Man");                                       //$NON-NLS-1$
         sRegionNames.put("IL", "Israel");                                            //$NON-NLS-1$
         sRegionNames.put("IT", "Italy");                                             //$NON-NLS-1$
         sRegionNames.put("JM", "Jamaica");                                           //$NON-NLS-1$
         sRegionNames.put("JP", "Japan");                                             //$NON-NLS-1$
         sRegionNames.put("JE", "Jersey");                                            //$NON-NLS-1$
         sRegionNames.put("JO", "Jordan");                                            //$NON-NLS-1$
         sRegionNames.put("KZ", "Kazakhstan");                                        //$NON-NLS-1$
         sRegionNames.put("KE", "Kenya");                                             //$NON-NLS-1$
         sRegionNames.put("KI", "Kiribati");                                          //$NON-NLS-1$
         sRegionNames.put("KP", "Korea, Democratic People's Republic of");            //$NON-NLS-1$
         sRegionNames.put("KR", "Korea, Republic of");                                //$NON-NLS-1$
         sRegionNames.put("KW", "Kuwait");                                            //$NON-NLS-1$
         sRegionNames.put("KG", "Kyrgyzstan");                                        //$NON-NLS-1$
         sRegionNames.put("LA", "Lao People's Democratic Republic");                  //$NON-NLS-1$
         sRegionNames.put("LV", "Latvia");                                            //$NON-NLS-1$
         sRegionNames.put("LB", "Lebanon");                                           //$NON-NLS-1$
         sRegionNames.put("LS", "Lesotho");                                           //$NON-NLS-1$
         sRegionNames.put("LR", "Liberia");                                           //$NON-NLS-1$
         sRegionNames.put("LY", "Libya");                                             //$NON-NLS-1$
         sRegionNames.put("LI", "Liechtenstein");                                     //$NON-NLS-1$
         sRegionNames.put("LT", "Lithuania");                                         //$NON-NLS-1$
         sRegionNames.put("LU", "Luxembourg");                                        //$NON-NLS-1$
         sRegionNames.put("MO", "Macao");                                             //$NON-NLS-1$
         sRegionNames.put("MK", "Macedonia, the former Yugoslav Republic of");        //$NON-NLS-1$
         sRegionNames.put("MG", "Madagascar");                                        //$NON-NLS-1$
         sRegionNames.put("MW", "Malawi");                                            //$NON-NLS-1$
         sRegionNames.put("MY", "Malaysia");                                          //$NON-NLS-1$
         sRegionNames.put("MV", "Maldives");                                          //$NON-NLS-1$
         sRegionNames.put("ML", "Mali");                                              //$NON-NLS-1$
         sRegionNames.put("MT", "Malta");                                             //$NON-NLS-1$
         sRegionNames.put("MH", "Marshall Islands");                                  //$NON-NLS-1$
         sRegionNames.put("MQ", "Martinique");                                        //$NON-NLS-1$
         sRegionNames.put("MR", "Mauritania");                                        //$NON-NLS-1$
         sRegionNames.put("MU", "Mauritius");                                         //$NON-NLS-1$
         sRegionNames.put("YT", "Mayotte");                                           //$NON-NLS-1$
         sRegionNames.put("MX", "Mexico");                                            //$NON-NLS-1$
         sRegionNames.put("FM", "Micronesia, Federated States of");                   //$NON-NLS-1$
         sRegionNames.put("MD", "Moldova, Republic of");                              //$NON-NLS-1$
         sRegionNames.put("MC", "Monaco");                                            //$NON-NLS-1$
         sRegionNames.put("MN", "Mongolia");                                          //$NON-NLS-1$
         sRegionNames.put("ME", "Montenegro");                                        //$NON-NLS-1$
         sRegionNames.put("MS", "Montserrat");                                        //$NON-NLS-1$
         sRegionNames.put("MA", "Morocco");                                           //$NON-NLS-1$
         sRegionNames.put("MZ", "Mozambique");                                        //$NON-NLS-1$
         sRegionNames.put("MM", "Myanmar");                                           //$NON-NLS-1$
         sRegionNames.put("NA", "Namibia");                                           //$NON-NLS-1$
         sRegionNames.put("NR", "Nauru");                                             //$NON-NLS-1$
         sRegionNames.put("NP", "Nepal");                                             //$NON-NLS-1$
         sRegionNames.put("NL", "Netherlands");                                       //$NON-NLS-1$
         sRegionNames.put("NC", "New Caledonia");                                     //$NON-NLS-1$
         sRegionNames.put("NZ", "New Zealand");                                       //$NON-NLS-1$
         sRegionNames.put("NI", "Nicaragua");                                         //$NON-NLS-1$
         sRegionNames.put("NE", "Niger");                                             //$NON-NLS-1$
         sRegionNames.put("NG", "Nigeria");                                           //$NON-NLS-1$
         sRegionNames.put("NU", "Niue");                                              //$NON-NLS-1$
         sRegionNames.put("NF", "Norfolk Island");                                    //$NON-NLS-1$
         sRegionNames.put("MP", "Northern Mariana Islands");                          //$NON-NLS-1$
         sRegionNames.put("NO", "Norway");                                            //$NON-NLS-1$
         sRegionNames.put("OM", "Oman");                                              //$NON-NLS-1$
         sRegionNames.put("PK", "Pakistan");                                          //$NON-NLS-1$
         sRegionNames.put("PW", "Palau");                                             //$NON-NLS-1$
         sRegionNames.put("PS", "Palestine");                                         //$NON-NLS-1$
         sRegionNames.put("PA", "Panama");                                            //$NON-NLS-1$
         sRegionNames.put("PG", "Papua New Guinea");                                  //$NON-NLS-1$
         sRegionNames.put("PY", "Paraguay");                                          //$NON-NLS-1$
         sRegionNames.put("PE", "Peru");                                              //$NON-NLS-1$
         sRegionNames.put("PH", "Philippines");                                       //$NON-NLS-1$
         sRegionNames.put("PN", "Pitcairn");                                          //$NON-NLS-1$
         sRegionNames.put("PL", "Poland");                                            //$NON-NLS-1$
         sRegionNames.put("PT", "Portugal");                                          //$NON-NLS-1$
         sRegionNames.put("PR", "Puerto Rico");                                       //$NON-NLS-1$
         sRegionNames.put("QA", "Qatar");                                             //$NON-NLS-1$
         sRegionNames.put("RO", "Romania");                                           //$NON-NLS-1$
         sRegionNames.put("RU", "Russian Federation");                                //$NON-NLS-1$
         sRegionNames.put("RW", "Rwanda");                                            //$NON-NLS-1$
         sRegionNames.put("RE", "R\u00e9union");                                      //$NON-NLS-1$
         sRegionNames.put("BL", "Saint Barth\u00e9lemy");                             //$NON-NLS-1$
         sRegionNames.put("SH", "Saint Helena, Ascension and Tristan da Cunha");      //$NON-NLS-1$
         sRegionNames.put("KN", "Saint Kitts and Nevis");                             //$NON-NLS-1$
         sRegionNames.put("LC", "Saint Lucia");                                       //$NON-NLS-1$
         sRegionNames.put("MF", "Saint Martin (French part)");                        //$NON-NLS-1$
         sRegionNames.put("PM", "Saint Pierre and Miquelon");                         //$NON-NLS-1$
         sRegionNames.put("VC", "Saint Vincent and the Grenadines");                  //$NON-NLS-1$
         sRegionNames.put("WS", "Samoa");                                             //$NON-NLS-1$
         sRegionNames.put("SM", "San Marino");                                        //$NON-NLS-1$
         sRegionNames.put("ST", "Sao Tome and Principe");                             //$NON-NLS-1$
         sRegionNames.put("SA", "Saudi Arabia");                                      //$NON-NLS-1$
         sRegionNames.put("SN", "Senegal");                                           //$NON-NLS-1$
         sRegionNames.put("RS", "Serbia");                                            //$NON-NLS-1$
         sRegionNames.put("SC", "Seychelles");                                        //$NON-NLS-1$
         sRegionNames.put("SL", "Sierra Leone");                                      //$NON-NLS-1$
         sRegionNames.put("SG", "Singapore");                                         //$NON-NLS-1$
         sRegionNames.put("SX", "Sint Maarten (Dutch part)");                         //$NON-NLS-1$
         sRegionNames.put("SK", "Slovakia");                                          //$NON-NLS-1$
         sRegionNames.put("SI", "Slovenia");                                          //$NON-NLS-1$
         sRegionNames.put("SB", "Solomon Islands");                                   //$NON-NLS-1$
         sRegionNames.put("SO", "Somalia");                                           //$NON-NLS-1$
         sRegionNames.put("ZA", "South Africa");                                      //$NON-NLS-1$
         sRegionNames.put("GS", "South Georgia and the South Sandwich Islands");      //$NON-NLS-1$
         sRegionNames.put("SS", "South Sudan");                                       //$NON-NLS-1$
         sRegionNames.put("ES", "Spain");                                             //$NON-NLS-1$
         sRegionNames.put("LK", "Sri Lanka");                                         //$NON-NLS-1$
         sRegionNames.put("SD", "Sudan");                                             //$NON-NLS-1$
         sRegionNames.put("SR", "Suriname");                                          //$NON-NLS-1$
         sRegionNames.put("SJ", "Svalbard and Jan Mayen");                            //$NON-NLS-1$
         sRegionNames.put("SZ", "Swaziland");                                         //$NON-NLS-1$
         sRegionNames.put("SE", "Sweden");                                            //$NON-NLS-1$
         sRegionNames.put("CH", "Switzerland");                                       //$NON-NLS-1$
         sRegionNames.put("SY", "Syrian Arab Republic");                              //$NON-NLS-1$
         sRegionNames.put("TW", "Taiwan, Province of China");                         //$NON-NLS-1$
         sRegionNames.put("TJ", "Tajikistan");                                        //$NON-NLS-1$
         sRegionNames.put("TZ", "Tanzania, United Republic of");                      //$NON-NLS-1$
         sRegionNames.put("TH", "Thailand");                                          //$NON-NLS-1$
         sRegionNames.put("TL", "Timor-Leste");                                       //$NON-NLS-1$
         sRegionNames.put("TG", "Togo");                                              //$NON-NLS-1$
         sRegionNames.put("TK", "Tokelau");                                           //$NON-NLS-1$
         sRegionNames.put("TO", "Tonga");                                             //$NON-NLS-1$
         sRegionNames.put("TT", "Trinidad and Tobago");                               //$NON-NLS-1$
         sRegionNames.put("TN", "Tunisia");                                           //$NON-NLS-1$
         sRegionNames.put("TR", "Turkey");                                            //$NON-NLS-1$
         sRegionNames.put("TM", "Turkmenistan");                                      //$NON-NLS-1$
         sRegionNames.put("TC", "Turks and Caicos Islands");                          //$NON-NLS-1$
         sRegionNames.put("TV", "Tuvalu");                                            //$NON-NLS-1$
         sRegionNames.put("UG", "Uganda");                                            //$NON-NLS-1$
         sRegionNames.put("UA", "Ukraine");                                           //$NON-NLS-1$
         sRegionNames.put("AE", "United Arab Emirates");                              //$NON-NLS-1$
         sRegionNames.put("GB", "United Kingdom");                                    //$NON-NLS-1$
         sRegionNames.put("US", "United States");                                     //$NON-NLS-1$
         sRegionNames.put("UM", "United States Minor Outlying Islands");              //$NON-NLS-1$
         sRegionNames.put("UY", "Uruguay");                                           //$NON-NLS-1$
         sRegionNames.put("UZ", "Uzbekistan");                                        //$NON-NLS-1$
         sRegionNames.put("VU", "Vanuatu");                                           //$NON-NLS-1$
         sRegionNames.put("VE", "Venezuela, Bolivarian Republic of");                 //$NON-NLS-1$
         sRegionNames.put("VN", "Viet Nam");                                          //$NON-NLS-1$
         sRegionNames.put("VG", "Virgin Islands, British");                           //$NON-NLS-1$
         sRegionNames.put("VI", "Virgin Islands, U.S.");                              //$NON-NLS-1$
         sRegionNames.put("WF", "Wallis and Futuna");                                 //$NON-NLS-1$
         sRegionNames.put("EH", "Western Sahara");                                    //$NON-NLS-1$
         sRegionNames.put("YE", "Yemen");                                             //$NON-NLS-1$
         sRegionNames.put("ZM", "Zambia");                                            //$NON-NLS-1$
         sRegionNames.put("ZW", "Zimbabwe");                                          //$NON-NLS-1$
         sRegionNames.put("AX", "\u00c5land Islands");                                //$NON-NLS-1$

         // Aliases
         // http://developer.android.com/reference/java/util/Locale.html
         // Apparently we're using some old aliases for some languages
         //  The Hebrew ("he") language code is rewritten as "iw", Indonesian ("id") as "in",
         // and Yiddish ("yi") as "ji".
         sLanguageToCountry.put("iw", sLanguageToCountry.get("he")); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageToCountry.put("in", sLanguageToCountry.get("id")); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageToCountry.put("ji", sLanguageToCountry.get("yi")); //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("iw", sLanguageNames.get("he"));         //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("in", sLanguageNames.get("id"));         //$NON-NLS-1$ //$NON-NLS-2$
         sLanguageNames.put("ji", sLanguageNames.get("yi"));         //$NON-NLS-1$ //$NON-NLS-2$

        // The following miscellaneous languages have no binding to a region
        // in sLanguageToCountry, since they are either extinct or constructed or
        // only in literary use:
        sLanguageNames.put("pi", "Pali"); //$NON-NLS-1$
        sLanguageNames.put("vo", "Volap\u00fck" /*Volapük*/); //$NON-NLS-1$
        sLanguageNames.put("eo", "Esperanto"); //$NON-NLS-1$
        sLanguageNames.put("la", "Latin"); //$NON-NLS-1$
        sLanguageNames.put("ia", "Interlingua"); //$NON-NLS-1$
        sLanguageNames.put("ie", "Interlingue"); //$NON-NLS-1$
        sLanguageNames.put("io", "Ido"); //$NON-NLS-1$
        sLanguageNames.put("ae", "Avestan"); //$NON-NLS-1$
        sLanguageNames.put("cu", "Church Slavic"); //$NON-NLS-1$

        // To check initial capacities of the maps and avoid dynamic resizing:
        //System.out.println("Language count = " + sLanguageNames.size());
        //System.out.println("Language Binding count = " + sLanguageToCountry.size());
        //System.out.println("Region count = " + sRegionNames.size());
    }

    /** Returns the relevant regions for the given language, if known. */
    @NonNull
    public static List<String> getRelevantRegions(@NonNull String languageCode) {
        assert languageCode.length() == 2;
        char first = languageCode.charAt(0);
        char second = languageCode.charAt(1);
        assert Character.isLowerCase(first) && Character.isLowerCase(second) : languageCode;

        // This table is generated by LocaleManagerTest#testGenerateLanguageRegionMap
        // if LocaleManagerTest#GENERATE_MULTI_REGION_SWITCH is true
        switch (first) {
            case 'a': {
                if (second == 'f') {
                    return Arrays.asList("NA", "ZA");
                } else if (second == 'r') {
                    return Arrays.asList("AE", "BH", "DJ", "DZ", "EG", "EH", "ER", "IL", "IQ", "JO",
                            "KM", "KW", "LB", "LY", "MA", "MR", "OM", "PS", "QA", "SA", "SD", "SO",
                            "SS", "SY", "TD", "TN", "YE");
                }
                break;
            }
            case 'b': {
                if (second == 'n') {
                    return Arrays.asList("BD", "IN");
                } else if (second == 'o') {
                    return Arrays.asList("CN", "IN");
                }
                break;
            }
            case 'c': {
                if (second == 'a') {
                    return Arrays.asList("AD", "ES", "FR", "IT");
                }
                break;
            }
            case 'd': {
                if (second == 'a') {
                    return Arrays.asList("DK", "GL");
                } else if (second == 'e') {
                    return Arrays.asList("AT", "BE", "CH", "DE", "LI", "LU");
                }
                break;
            }
            case 'e': {
                if (second == 'e') {
                    return Arrays.asList("GH", "TG");
                } else if (second == 'l') {
                    return Arrays.asList("CY", "GR");
                } else if (second == 'n') {
                    return Arrays.asList("AG", "AI", "AS", "AU", "BB", "BE", "BM", "BS", "BW", "BZ",
                            "CA", "CC", "CK", "CM", "CX", "DM", "ER", "FJ", "FK", "FM", "GB", "GD",
                            "GG", "GH", "GI", "GM", "GU", "GY", "HK", "IE", "IM", "IN", "IO", "JE",
                            "JM", "KE", "KI", "KN", "KY", "LC", "LR", "LS", "MG", "MH", "MO", "MP",
                            "MS", "MT", "MU", "MW", "MY", "NA", "NF", "NG", "NR", "NU", "NZ", "PG",
                            "PH", "PK", "PN", "PR", "PW", "RW", "SB", "SC", "SD", "SG", "SH", "SL",
                            "SS", "SX", "SZ", "TC", "TK", "TO", "TT", "TV", "TZ", "UG", "UM", "US",
                            "VC", "VG", "VI", "VU", "WS", "ZA", "ZM", "ZW");
                } else if (second == 's') {
                    return Arrays.asList("AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "ES", "GQ",
                            "GT", "HN", "MX", "NI", "PA", "PE", "PH", "PR", "PY", "SV", "US", "UY",
                            "VE");
                }
                break;
            }
            case 'f': {
                if (second == 'a') {
                    return Arrays.asList("AF", "IR");
                } else if (second == 'f') {
                    return Arrays.asList("CM", "GN", "MR", "SN");
                } else if (second == 'r') {
                    return Arrays.asList("BE", "BF", "BI", "BJ", "BL", "CA", "CD", "CF", "CG", "CH",
                            "CI", "CM", "DJ", "DZ", "FR", "GA", "GF", "GN", "GP", "GQ", "HT", "KM",
                            "LU", "MA", "MC", "MF", "MG", "ML", "MQ", "MR", "MU", "NC", "NE", "PF",
                            "PM", "RE", "RW", "SC", "SN", "SY", "TD", "TG", "TN", "VU", "WF", "YT");
                }
                break;
            }
            case 'h': {
                if (second == 'a') {
                    return Arrays.asList("GH", "NE", "NG");
                } else if (second == 'r') {
                    return Arrays.asList("BA", "HR");
                }
                break;
            }
            case 'i': {
                if (second == 't') {
                    return Arrays.asList("CH", "IT", "SM");
                }
                break;
            }
            case 'k': {
                if (second == 'o') {
                    return Arrays.asList("KP", "KR");
                }
                break;
            }
            case 'l': {
                if (second == 'n') {
                    return Arrays.asList("AO", "CD", "CF", "CG");
                }
                break;
            }
            case 'm': {
                if (second == 's') {
                    return Arrays.asList("BN", "MY", "SG");
                }
                break;
            }
            case 'n': {
                if (second == 'b') {
                    return Arrays.asList("NO", "SJ");
                } else if (second == 'e') {
                    return Arrays.asList("IN", "NP");
                } else if (second == 'l') {
                    return Arrays.asList("AW", "BE", "BQ", "CW", "NL", "SR", "SX");
                }
                break;
            }
            case 'o': {
                if (second == 'm') {
                    return Arrays.asList("ET", "KE");
                } else if (second == 's') {
                    return Arrays.asList("GE", "RU");
                }
                break;
            }
            case 'p': {
                if (second == 'a') {
                    return Arrays.asList("IN", "PK");
                } else if (second == 't') {
                    return Arrays.asList("AO", "BR", "CV", "GW", "MO", "MZ", "PT", "ST", "TL");
                }
                break;
            }
            case 'q': {
                if (second == 'u') {
                    return Arrays.asList("BO", "EC", "PE");
                }
                break;
            }
            case 'r': {
                if (second == 'o') {
                    return Arrays.asList("MD", "RO");
                } else if (second == 'u') {
                    return Arrays.asList("BY", "KG", "KZ", "MD", "RU", "UA");
                }
                break;
            }
            case 's': {
                if (second == 'e') {
                    return Arrays.asList("FI", "NO", "SE");
                } else if (second == 'o') {
                    return Arrays.asList("DJ", "ET", "KE", "SO");
                } else if (second == 'q') {
                    return Arrays.asList("AL", "MK");
                } else if (second == 'r') {
                    return Arrays.asList("BA", "ME", "RS");
                } else if (second == 'v') {
                    return Arrays.asList("AX", "FI", "SE");
                } else if (second == 'w') {
                    return Arrays.asList("KE", "TZ", "UG");
                }
                break;
            }
            case 't': {
                if (second == 'a') {
                    return Arrays.asList("IN", "LK", "MY", "SG");
                } else if (second == 'i') {
                    return Arrays.asList("ER", "ET");
                } else if (second == 'r') {
                    return Arrays.asList("CY", "TR");
                }
                break;
            }
            case 'u': {
                if (second == 'r') {
                    return Arrays.asList("IN", "PK");
                } else if (second == 'z') {
                    return Arrays.asList("AF", "UZ");
                }
                break;
            }
            case 'y': {
                if (second == 'o') {
                    return Arrays.asList("BJ", "NG");
                }
                break;
            }
            case 'z': {
                if (second == 'h') {
                    return Arrays.asList("CN", "HK", "MO", "SG", "TW");
                }
                break;
            }
        }

        String languageRegion = sLanguageToCountry.get(languageCode);
        if (languageRegion != null) {
            return Collections.singletonList(languageRegion);
        }

        return Collections.emptyList();
    }

    /**
     * Attempts to guess the region for the given language with the given timezone
     * as a clue for where to look.
     */
    // This code is generated by LocaleManagerTest#testGenerateLanguageRegionMap
    // if LocaleManagerTest#GENERATE_MULTI_REGION_SWITCH is true
    @SuppressWarnings("SpellCheckingInspection")
    @Nullable
    @VisibleForTesting
    static String getTimeZoneRegion(@NonNull TimeZone zone) {
        // Instead of String#hashCode, use this to ensure stable across platforms
        String id = zone.getID();
        int hashedId = 0;
        for (int i = 0, n = id.length(); i < n; i++) {
            hashedId = 31 * hashedId + id.charAt(i);
        }
        switch (zone.getRawOffset()) {
            case -36000000: // -10.0 hours
                return "US";       // United States
            case -32400000: // -9.0 hours
                return "US";       // United States
            case -28800000: // -8.0 hours
                switch (hashedId) {
                    case -459287604:       // America/Ensenada
                    case 256046501:        // America/Santa_Isabel
                    case 1647318035:       // America/Tijuana
                    case -1983011822:      // Mexico/BajaNorte
                        return "MX";       // Mexico
                    case 1389185817:       // America/Dawson
                    case 900028252:        // America/Vancouver
                    case -347637707:       // America/Whitehorse
                    case 364935240:        // Canada/Pacific
                    case -2010814355:      // Canada/Yukon
                        return "CA";       // Canada
                    // America/Los_Angeles
                    // America/Metlakatla
                    // PST
                    // US/Pacific
                    default:
                        return "US";       // United States
                }
            case -25200000: // -7.0 hours
                switch (hashedId) {
                    case 202222115:        // America/Chihuahua
                    case 611591843:        // America/Hermosillo
                    case 2142546433:       // America/Mazatlan
                    case 1532263802:       // America/Ojinaga
                    case -641163936:       // Mexico/BajaSur
                        return "MX";       // Mexico
                    case -1774689070:      // America/Cambridge_Bay
                    case -302339179:       // America/Creston
                    case -1998145482:      // America/Dawson_Creek
                    case -906910905:       // America/Edmonton
                    case 1544280457:       // America/Inuvik
                    case 1924477936:       // America/Yellowknife
                    case 1850095790:       // Canada/Mountain
                        return "CA";       // Canada
                    // America/Boise
                    // America/Denver
                    // America/Phoenix
                    // America/Shiprock
                    // Navajo
                    // PNT
                    // US/Arizona
                    default:
                        return "US";       // United States
                }
            case -21600000: // -6.0 hours
                switch (hashedId) {
                    case -355081471:       // America/Costa_Rica
                        return "CR";       // Costa Rica
                    case 662067781:        // Pacific/Galapagos
                        return "EC";       // Ecuador
                    case 268098540:        // America/Guatemala
                        return "GT";       // Guatemala
                    case -1192934179:      // America/Tegucigalpa
                        return "HN";       // Honduras
                    case -496169397:       // America/Managua
                        return "NI";       // Nicaragua
                    case -610612331:       // America/El_Salvador
                        return "SV";       // El Salvador
                    case 35870737:         // Chile/EasterIsland
                    case -2089950224:      // Pacific/Easter
                        return "CL";       // Chile
                    case 1033313139:       // America/Bahia_Banderas
                    case 1360273357:       // America/Cancun
                    case 958016402:        // America/Matamoros
                    case 1650383341:       // America/Merida
                    case -1436528620:      // America/Mexico_City
                    case -905842704:       // America/Monterrey
                    case -380253810:       // Mexico/General
                        return "MX";       // Mexico
                    case -1997850159:      // America/Rainy_River
                    case 1290869225:       // America/Rankin_Inlet
                    case 1793201705:       // America/Regina
                    case 1334007082:       // America/Resolute
                    case 99854508:         // America/Swift_Current
                    case 569007676:        // America/Winnipeg
                    case 1837303604:       // Canada/Central
                    case -1616213428:      // Canada/East-Saskatchewan
                    case -1958461186:      // Canada/Saskatchewan
                        return "CA";       // Canada
                    // America/Chicago
                    // America/Indiana/Knox
                    // America/Indiana/Tell_City
                    // America/Knox_IN
                    // America/Menominee
                    // America/North_Dakota/Beulah
                    // America/North_Dakota/Center
                    // America/North_Dakota/New_Salem
                    // CST
                    // US/Central
                    default:
                        return "US";       // United States
                }
            case -18000000: // -5.0 hours
                switch (hashedId) {
                    case 1344376451:       // America/Bogota
                        return "CO";       // Colombia
                    case 407688513:        // America/Guayaquil
                        return "EC";       // Ecuador
                    case 1732450137:       // America/Panama
                        return "PA";       // Panama
                    case 2039677810:       // America/Lima
                        return "PE";       // Peru
                    case 1503655288:       // America/Havana
                    case 2111569:          // Cuba
                        return "CU";       // Cuba
                    case -615687308:       // America/Eirunepe
                    case 42696295:         // America/Porto_Acre
                    case -1756511823:      // America/Rio_Branco
                    case 1213658776:       // Brazil/Acre
                        return "BR";       // Brazil
                    case 1908749375:       // America/Atikokan
                    case -1694184172:      // America/Coral_Harbour
                    case 695184620:        // America/Iqaluit
                    case 1356626855:       // America/Montreal
                    case 622452689:        // America/Nipigon
                    case 977509670:        // America/Pangnirtung
                    case 151241566:        // America/Thunder_Bay
                    case 1826315056:       // America/Toronto
                    case -792567293:       // Canada/Eastern
                        return "CA";       // Canada
                    // America/Detroit
                    // America/Fort_Wayne
                    // America/Indiana/Indianapolis
                    // America/Indiana/Marengo
                    // America/Indiana/Petersburg
                    // America/Indiana/Vevay
                    // America/Indiana/Vincennes
                    // America/Indiana/Winamac
                    // America/Indianapolis
                    // America/Kentucky/Louisville
                    // America/Kentucky/Monticello
                    // America/Louisville
                    // America/New_York
                    // IET
                    // US/East-Indiana
                    // US/Eastern
                    default:
                        return "US";       // United States
                }
            case -16200000: // -4.5 hours
                return "VE";       // Venezuela, Bolivarian Republic of
            case -14400000: // -4.0 hours
                switch (hashedId) {
                    case 1501639611:       // America/Argentina/San_Luis
                        return "AR";       // Argentina
                    case 1617469984:       // America/La_Paz
                        return "BO";       // Bolivia, Plurinational State of
                    case -432820086:       // America/Santo_Domingo
                        return "DO";       // Dominican Republic
                    case 1367207089:       // America/Asuncion
                        return "PY";       // Paraguay
                    case -611834443:       // America/Santiago
                    case -2036395347:      // Chile/Continental
                        return "CL";       // Chile
                    case -691236908:       // America/Puerto_Rico
                    case 79506:            // PRT
                        return "PR";       // Puerto Rico
                    case -1680637607:      // America/Blanc-Sablon
                    case 1275531960:       // America/Glace_Bay
                    case -2087755565:      // America/Goose_Bay
                    case -640330778:       // America/Halifax
                    case -95289381:        // America/Moncton
                    case -2011036567:      // Canada/Atlantic
                        return "CA";       // Canada
                    // America/Boa_Vista
                    // America/Campo_Grande
                    // America/Cuiaba
                    // America/Eirunepe
                    // America/Manaus
                    // America/Porto_Acre
                    // America/Porto_Velho
                    // America/Rio_Branco
                    // Brazil/Acre
                    default:
                        return "BR";       // Brazil
                }
            case -12600000: // -3.5 hours
                return "CA";       // Canada
            case -10800000: // -3.0 hours
                switch (hashedId) {
                    case 1987071743:       // America/Montevideo
                        return "UY";       // Uruguay
                    case 1231674648:       // America/Araguaina
                    case -1203975328:      // America/Bahia
                    case -1203852432:      // America/Belem
                    case -615687308:       // America/Eirunepe
                    case -1887400619:      // America/Fortaleza
                    case 1646238717:       // America/Maceio
                    case 42696295:         // America/Porto_Acre
                    case 1793082297:       // America/Recife
                    case -1756511823:      // America/Rio_Branco
                    case -612056498:       // America/Santarem
                    case -1523781592:      // America/Sao_Paulo
                    case 65649:            // BET
                    case 1213658776:       // Brazil/Acre
                    case 1213776064:       // Brazil/East
                        return "BR";       // Brazil
                    // AGT
                    // America/Argentina/Buenos_Aires
                    // America/Argentina/Catamarca
                    // America/Argentina/ComodRivadavia
                    // America/Argentina/Cordoba
                    // America/Argentina/Jujuy
                    // America/Argentina/La_Rioja
                    // America/Argentina/Mendoza
                    // America/Argentina/Rio_Gallegos
                    // America/Argentina/Salta
                    // America/Argentina/San_Juan
                    // America/Argentina/San_Luis
                    // America/Argentina/Tucuman
                    // America/Argentina/Ushuaia
                    // America/Buenos_Aires
                    // America/Catamarca
                    // America/Cordoba
                    // America/Jujuy
                    // America/Mendoza
                    default:
                        return "AR";       // Argentina
                }
            case -7200000: // -2.0 hours
                return "BR";       // Brazil
            case -3600000: // -1.0 hours
                return "PT";       // Portugal
            case 0: // 0.0 hours
                switch (hashedId) {
                    case -2002672065:      // Atlantic/Canary
                        return "ES";       // Spain
                    case -3562122:         // Africa/Casablanca
                        return "MA";       // Morocco
                    case 70702:            // GMT
                        return "TW";       // Taiwan, Province of China
                    case 2160119:          // Eire
                    case 300259341:        // Europe/Dublin
                        return "IE";       // Ireland
                    case -1722575083:      // Atlantic/Reykjavik
                    case -1000832298:      // Iceland
                        return "IS";       // Iceland
                    case -1677314468:      // Atlantic/Madeira
                    case 518707320:        // Europe/Lisbon
                    case 794006110:        // Portugal
                        return "PT";       // Portugal
                    // Europe/Belfast
                    // Europe/London
                    // GB
                    default:
                        return "GB";       // United Kingdom
                }
            case 3600000: // 1.0 hours
                switch (hashedId) {
                    case 747709736:        // Europe/Tirane
                        return "AL";       // Albania
                    case 804593244:        // Europe/Vienna
                        return "AT";       // Austria
                    case 1036497278:       // Europe/Sarajevo
                        return "BA";       // Bosnia and Herzegovina
                    case -516035308:       // Europe/Brussels
                        return "BE";       // Belgium
                    case 930574244:        // Europe/Zurich
                        return "CH";       // Switzerland
                    case 641004357:        // Europe/Prague
                        return "CZ";       // Czech Republic
                    case 228701359:        // Europe/Berlin
                        return "DE";       // Germany
                    case -862787273:       // Europe/Copenhagen
                        return "DK";       // Denmark
                    case -977866396:       // Africa/Algiers
                        return "DZ";       // Algeria
                    case 911784828:        // Europe/Zagreb
                        return "HR";       // Croatia
                    case 1643067635:       // Europe/Budapest
                        return "HU";       // Hungary
                    case -1407095582:      // Europe/Rome
                        return "IT";       // Italy
                    case 432607731:        // Europe/Luxembourg
                        return "LU";       // Luxembourg
                    case -1834768363:      // Europe/Podgorica
                        return "ME";       // Montenegro
                    case 720852545:        // Europe/Skopje
                        return "MK";       // Macedonia, the former Yugoslav Republic of
                    case -675325160:       // Europe/Malta
                        return "MT";       // Malta
                    case 1107183657:       // Europe/Amsterdam
                        return "NL";       // Netherlands
                    case 562540219:        // Europe/Belgrade
                        return "RS";       // Serbia
                    case -1783944015:      // Europe/Stockholm
                        return "SE";       // Sweden
                    case -1262503490:      // Europe/Ljubljana
                        return "SI";       // Slovenia
                    case -1871032358:      // Europe/Bratislava
                        return "SK";       // Slovakia
                    case 1817919522:       // Africa/Tunis
                        return "TN";       // Tunisia
                    case 1801750059:       // Africa/Ceuta
                    case 539516618:        // Europe/Madrid
                        return "ES";       // Spain
                    case 68470:            // ECT
                    case -672549154:       // Europe/Paris
                        return "FR";       // France
                    case -1121325742:      // Africa/Tripoli
                    case 73413677:         // Libya
                        return "LY";       // Libya
                    case -72083073:        // Atlantic/Jan_Mayen
                    case -1407181132:      // Europe/Oslo
                        return "NO";       // Norway
                    // Europe/Warsaw
                    default:
                        return "PL";       // Poland
                }
            case 7200000: // 2.0 hours
                switch (hashedId) {
                    case -669373067:       // Europe/Sofia
                        return "BG";       // Bulgaria
                    case 1469914287:       // Europe/Tallinn
                        return "EE";       // Estonia
                    case -1854672812:      // Europe/Helsinki
                        return "FI";       // Finland
                    case 213620546:        // Europe/Athens
                        return "GR";       // Greece
                    case -1678352343:      // Asia/Amman
                        return "JO";       // Jordan
                    case -468176592:       // Asia/Beirut
                        return "LB";       // Lebanon
                    case -820952635:       // Europe/Vilnius
                        return "LT";       // Lithuania
                    case -1407101538:      // Europe/Riga
                        return "LV";       // Latvia
                    case -1305089392:      // Europe/Bucharest
                        return "RO";       // Romania
                    case 1640682817:       // Europe/Kaliningrad
                        return "RU";       // Russian Federation
                    case 1088211684:       // Asia/Damascus
                        return "SY";       // Syrian Arab Republic
                    case 1587535273:       // Africa/Johannesburg
                        return "ZA";       // South Africa
                    case 540421055:        // Asia/Nicosia
                    case 660679831:        // Europe/Nicosia
                        return "CY";       // Cyprus
                    case -1121325742:      // Africa/Tripoli
                    case 73413677:         // Libya
                        return "LY";       // Libya
                    case 65091:            // ART
                    case 1801619315:       // Africa/Cairo
                    case 66911291:         // Egypt
                        return "EG";       // Egypt
                    case 511371267:        // Asia/Jerusalem
                    case -1868494453:      // Asia/Tel_Aviv
                    case -2095341728:      // Israel
                        return "IL";       // Israel
                    case 207779975:        // Asia/Istanbul
                    case -359165265:       // Europe/Istanbul
                    case -1778564402:      // Turkey
                        return "TR";       // Turkey
                    // Europe/Kiev
                    // Europe/Simferopol
                    // Europe/Uzhgorod
                    default:
                        return "UA";       // Ukraine
                }
            case 10800000: // 3.0 hours
                switch (hashedId) {
                    case -1744032040:      // Asia/Bahrain
                        return "BH";       // Bahrain
                    case -675084931:       // Europe/Minsk
                        return "BY";       // Belarus
                    case -1745250846:      // Asia/Baghdad
                        return "IQ";       // Iraq
                    case -195337532:       // Asia/Kuwait
                        return "KW";       // Kuwait
                    case -1663926768:      // Asia/Qatar
                        return "QA";       // Qatar
                    case -5956312:         // Asia/Riyadh
                        return "SA";       // Saudi Arabia
                    case 581080470:        // Africa/Khartoum
                        return "SD";       // Sudan
                    case -2046172313:      // Europe/Simferopol
                        return "UA";       // Ukraine
                    case -1439622607:      // Asia/Aden
                        return "YE";       // Yemen
                    // Europe/Kaliningrad
                    // Europe/Moscow
                    // Europe/Volgograd
                    default:
                        return "RU";       // Russian Federation
                }
            case 14400000: // 4.0 hours
                switch (hashedId) {
                    case -1675354028:      // Asia/Dubai
                        return "AE";       // United Arab Emirates
                    case -138196720:       // Asia/Muscat
                        return "OM";       // Oman
                    case -2046172313:      // Europe/Simferopol
                        return "UA";       // Ukraine
                    // Europe/Moscow
                    // Europe/Samara
                    // Europe/Volgograd
                    default:
                        return "RU";       // Russian Federation
                }
            case 18000000: // 5.0 hours
                return "RU";       // Russian Federation
            case 19800000: // 5.5 hours
                return "IN";       // India
            case 21600000: // 6.0 hours
                switch (hashedId) {
                    case 1958400136:       // Asia/Kashgar
                    case 88135602:         // Asia/Urumqi
                        return "CN";       // China
                    // Asia/Novosibirsk
                    // Asia/Omsk
                    default:
                        return "RU";       // Russian Federation
                }
            case 25200000: // 7.0 hours
                switch (hashedId) {
                    case -1738808822:      // Asia/Bangkok
                        return "TH";       // Thailand
                    case 1063310893:       // Asia/Jakarta
                    case -788096746:       // Asia/Pontianak
                        return "ID";       // Indonesia
                    case 1214715332:       // Asia/Ho_Chi_Minh
                    case 14814128:         // Asia/Saigon
                    case 85303:            // VST
                        return "VN";       // Viet Nam
                    // Asia/Krasnoyarsk
                    // Asia/Novokuznetsk
                    // Asia/Novosibirsk
                    default:
                        return "RU";       // Russian Federation
                }
            case 28800000: // 8.0 hours
                switch (hashedId) {
                    case -156810007:       // Asia/Manila
                        return "PH";       // Philippines
                    case 43451613:         // Asia/Taipei
                        return "TW";       // Taiwan, Province of China
                    case 307946178:        // Australia/Perth
                    case 1811257630:       // Australia/West
                        return "AU";       // Australia
                    case 404568855:        // Asia/Hong_Kong
                    case -390386883:       // Hongkong
                        return "HK";       // Hong Kong
                    case -463608032:       // Asia/Makassar
                    case -84259736:        // Asia/Ujung_Pandang
                        return "ID";       // Indonesia
                    case -99068543:        // Asia/Kuala_Lumpur
                    case -1778758162:      // Asia/Kuching
                        return "MY";       // Malaysia
                    case 663100500:        // Asia/Irkutsk
                    case -808657565:       // Asia/Krasnoyarsk
                        return "RU";       // Russian Federation
                    case 133428255:        // Asia/Singapore
                    case 499614468:        // Singapore
                        return "SG";       // Singapore
                    // Asia/Chongqing
                    // Asia/Chungking
                    // Asia/Harbin
                    // Asia/Kashgar
                    // Asia/Shanghai
                    // Asia/Urumqi
                    // CTT
                    default:
                        return "CN";       // China
                }
            case 31500000: // 8.75 hours
                return "AU";       // Australia
            case 32400000: // 9.0 hours
                switch (hashedId) {
                    case -996350568:       // Asia/Jayapura
                        return "ID";       // Indonesia
                    case -1661964753:      // Asia/Seoul
                    case 81326:            // ROK
                        return "KR";       // Korea, Republic of
                    case 663100500:        // Asia/Irkutsk
                    case 1491561941:       // Asia/Yakutsk
                        return "RU";       // Russian Federation
                    // Asia/Tokyo
                    // JST
                    default:
                        return "JP";       // Japan
                }
            case 34200000: // 9.5 hours
                return "AU";       // Australia
            case 36000000: // 10.0 hours
                switch (hashedId) {
                    case -572853474:       // Asia/Magadan
                    case 1409241312:       // Asia/Sakhalin
                    case 1755599521:       // Asia/Vladivostok
                    case 1491561941:       // Asia/Yakutsk
                        return "RU";       // Russian Federation
                    // AET
                    // Australia/ACT
                    // Australia/Brisbane
                    // Australia/Canberra
                    // Australia/Currie
                    // Australia/Hobart
                    // Australia/Lindeman
                    // Australia/Melbourne
                    // Australia/NSW
                    // Australia/Queensland
                    // Australia/Sydney
                    // Australia/Tasmania
                    default:
                        return "AU";       // Australia
                }
            case 37800000: // 10.5 hours
                return "AU";       // Australia
            case 39600000: // 11.0 hours
                return "RU";       // Russian Federation
            case 43200000: // 12.0 hours
                switch (hashedId) {
                    case 77615:            // NST
                    case 2508:             // NZ
                    case -969722739:       // Pacific/Auckland
                        return "NZ";       // New Zealand
                    // Asia/Anadyr
                    // Asia/Kamchatka
                    default:
                        return "RU";       // Russian Federation
                }
            case 45900000: // 12.75 hours
                return "NZ";       // New Zealand
        }
        return null;
    }

    @VisibleForTesting
    public static Map<String, String> getLanguageToCountryMap() {
        return sLanguageToCountry;
    }

    @VisibleForTesting
    public static Map<String, String> getLanguageNamesMap() {
        return sLanguageNames;
    }

    @VisibleForTesting
    public static Map<String, String> getRegionNamesMap() {
        return sRegionNames;
    }
}
