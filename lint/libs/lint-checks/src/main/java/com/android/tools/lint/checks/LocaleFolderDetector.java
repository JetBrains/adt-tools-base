/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Checks for errors related to locale handling
 */
public class LocaleFolderDetector extends Detector implements Detector.ResourceFolderScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            LocaleFolderDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE);

    /**
     * Using a locale folder that is not consulted
     */
    public static final Issue DEPRECATED_CODE = Issue.create(
            "LocaleFolder", //$NON-NLS-1$
            "Wrong locale name",
            "From the `java.util.Locale` documentation:\n" +
            "\"Note that Java uses several deprecated two-letter codes. The Hebrew (\"he\") " +
            "language code is rewritten as \"iw\", Indonesian (\"id\") as \"in\", and " +
            "Yiddish (\"yi\") as \"ji\". This rewriting happens even if you construct your " +
            "own Locale object, not just for instances returned by the various lookup methods.\n" +
            "\n" +
            "Because of this, if you add your localized resources in for example `values-he` " +
            "they will not be used, since the system will look for `values-iw` instead.\n" +
            "\n" +
            "To work around this, place your resources in a `values` folder using the " +
            "deprecated language code instead.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/reference/java/util/Locale.html");

    /**
     * Using a region that might not be a match for the given language
     */
    public static final Issue WRONG_REGION = Issue.create(
            "WrongRegion", //$NON-NLS-1$
            "Suspicious Language/Region Combination",
            "Android uses the letter codes ISO 639-1 for languages, and the letter codes " +
            "ISO 3166-1 for the region codes. In many cases, the language code and the " +
            "country where the language is spoken is the same, but it is also often not " +
            "the case. For example, while 'se' refers to Sweden, where Swedish is spoken, " +
            "the language code for Swedish is *not* `se` (which refers to the Northern " +
            "Sami language), the language code is `sv`. And similarly the region code for " +
            "`sv` is El Salvador.\n" +
            "\n" +
            "This lint check looks for suspicious language and region combinations, to help " +
            "catch cases where you've accidentally used the wrong language or region code. " +
            "Lint knows about the most common regions where a language is spoken, and if " +
            "a folder combination is not one of these, it is flagged as suspicious.\n" +
            "\n" +
            "Note however that it may not be an error: you can theoretically have speakers " +
            "of any language in any region and want to target that with your resources, so " +
            "this check is aimed at tracking down likely mistakes, not to enforce a specific " +
            "set of region and language combinations.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    public static final Issue USE_ALPHA_2 = Issue.create(
            "UseAlpha2", //$NON-NLS-1$
            "Using 3-letter Codes",
            "For compatibility with earlier devices, you should only use 3-letter language " +
            "and region codes when there is no corresponding 2 letter code.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo("https://tools.ietf.org/html/bcp47");

    public static final Issue INVALID_FOLDER = Issue.create(
            "InvalidResourceFolder", //$NON-NLS-1$
            "Invalid Resource Folder",
            "This lint check looks for a folder name that is not a valid resource folder " +
            "name; these will be ignored and not packaged by the Android Gradle build plugin.\n" +
            "\n" +
            "Note that the order of resources is very important; for example, you can't specify " +
            "a language before a network code.\n" +
            "\n" +
            "Similarly, note that to use 3 letter language or region codes, you have to use " +
            "a special BCP 47 syntax: the prefix b+ followed by the BCP 47 language tag but " +
            "with `+` as the individual separators instead of `-`. Therefore, for the BCP 47 " +
            "language tag `es-419` you have to use `b+es+419`.",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo("http://developer.android.com/guide/topics/resources/providing-resources.html")
            .addMoreInfo("https://tools.ietf.org/html/bcp47");

    /**
     * Constructs a new {@link LocaleFolderDetector}
     */
    public LocaleFolderDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ResourceFolderScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        Pair<String, String> locale = TypoDetector.getLocale(folderName);
        if (locale != null) {
            final String language = locale.getFirst();
            if (language != null) {
                String replace = null;
                if (language.equals("he")) {
                    replace = "iw";
                } else if (language.equals("id")) {
                    replace = "in";
                } else if (language.equals("yi")) {
                    replace = "ji";
                }
                // Note: there is also fil=>tl

                if (replace != null) {
                    // TODO: Check for suppress somewhere other than lint.xml?
                    String message = String.format("The locale folder \"`%1$s`\" should be "
                                    + "called \"`%2$s`\" instead; see the "
                                    + "`java.util.Locale` documentation",
                            language, replace);
                    context.report(DEPRECATED_CODE, Location.create(context.file), message);
                }

                if (language.length() == 3) {
                    String languageAlpha2 = LocaleManager.getLanguageAlpha2(language.toLowerCase(Locale.US));
                    if (languageAlpha2 != null) {
                        String message = String.format("For compatibility, should use 2-letter "
                                       + "language codes when available; use `%1$s` instead of `%2$s`",
                                languageAlpha2, language);
                        context.report(USE_ALPHA_2, Location.create(context.file), message);
                    }
                }

                String region = locale.getSecond();
                if (region != null && region.length() == 3) {
                    String regionAlpha2 = LocaleManager.getRegionAlpha2(region.toUpperCase(Locale.UK));
                    if (regionAlpha2 != null) {
                        String message = String.format("For compatibility, should use 2-letter "
                                        + "region codes when available; use `%1$s` instead of `%2$s`",
                                regionAlpha2 , region);
                        context.report(USE_ALPHA_2, Location.create(context.file), message);

                    }
                }

                if (region != null && region.length() == 2) {
                    List<String> relevantRegions = LocaleManager.getRelevantRegions(language);
                    if (!relevantRegions.isEmpty() && !relevantRegions.contains(region)) {
                        List<String> sortedRegions = sortRegions(language, relevantRegions);
                        List<String> suggestions = Lists.newArrayList();
                        for (String code : sortedRegions) {
                            suggestions.add(code + " (" + LocaleManager.getRegionName(code) + ")");
                        }

                        String message = String.format(
                                "Suspicious language and region combination %1$s (%2$s) "
                                        + "with %3$s (%4$s): language %5$s is usually "
                                        + "paired with: %6$s",
                                language, LocaleManager.getLanguageName(language), region,
                                LocaleManager.getRegionName(region), language,
                                Joiner.on(", ").join(suggestions));
                        context.report(WRONG_REGION, Location.create(context.file), message);
                    }
                }
            }
        }

        if (ResourceFolderType.getFolderType(folderName) != null &&
                FolderConfiguration.getConfigForFolder(folderName) == null) {
            String message = "Invalid resource folder: make sure qualifiers appear in the "
                    + "correct order, are spelled correctly, etc.";
            String bcpSuggestion = suggestBcp47Correction(folderName);
            if (bcpSuggestion != null) {
                message = String.format("Invalid resource folder; did you mean `%1$s` ?",
                        bcpSuggestion);
            }
            context.report(INVALID_FOLDER, Location.create(context.file), message);
        }
    }

    /**
     * Look at the given folder name and see if it looks like an unintentional attempt to use
     * 3-letter language codes or region codes, and if so, suggest a replacement.
     *
     * @param folderName a folder name
     * @return a suggestion, or null
     */
    @Nullable
    @VisibleForTesting
    static String suggestBcp47Correction(String folderName) {
        String language = null;
        String region = null;
        Iterator<String> iterator =
                Splitter.on('-').omitEmptyStrings().split(folderName).iterator();
        // Skip folder type
        if (!iterator.hasNext()) {
            return null;
        }
        iterator.next();

        while (iterator.hasNext()) {
            String segment = iterator.next();
            String original = segment;
            int length = segment.length();
            if (language != null) {
                // Only look for region
                segment = segment.toUpperCase(Locale.US);
                if (length == 3) {
                    if (original.charAt(0) == 'r' && Character.isUpperCase(original.charAt(1)) &&
                            LocaleManager.isValidRegionCode(segment.substring(1))) {
                        region = segment.substring(1);
                        break;
                    } else if (Character.isDigit(original.charAt(0))) {
                        region = segment;
                    } else if (LocaleManager.isValidRegionCode(segment)) {
                        region = segment;
                    }
                } else if (length == 4 && original.charAt(0) == 'r'
                        && Character.isUpperCase(original.charAt(1))) {
                    if (LocaleManager.isValidRegionCode(segment.substring(1))) {
                        region = segment.substring(1);
                        break;
                    }
                }
            } else {
                segment = segment.toLowerCase(Locale.US);
                if ("car".equals(segment)) { // "car" is a valid value for UI mode
                    return null;
                }
                if (LocaleManager.isValidLanguageCode(segment)) {
                    language = segment;
                }
            }
        }

        if (language != null) {
            if (language.length() == 3) {
                String better = LocaleManager.getLanguageAlpha2(language);
                if (better != null) {
                    language = better;
                }
            }
            if (region != null) {
                if (region.length() == 3 && !Character.isDigit(region.charAt(0))) {
                    String better = LocaleManager.getRegionAlpha2(region);
                    if (better != null) {
                        region = better;
                    }
                }
                return LocaleQualifier.PREFIX + language + '+' + region;
            }
            return LocaleQualifier.PREFIX + language;
        }

        return null;
    }

    /**
     * Sort the "usually combined with" regions such that the preferred region
     * for the language is first, followed by the default primary region (if
     * not the same, followed by the same letter codes, followed by alphabetical
     * order.
     */
    private static List<String> sortRegions(
            @NonNull final String language,
            @NonNull List<String> regions) {
        List<String> sortedRegions = Lists.newArrayList(regions);
        final String primary = LocaleManager.getLanguageRegion(language);
        final String secondary = LocaleManager.getDefaultLanguageRegion(language);
        Collections.sort(sortedRegions, new Comparator<String>() {
            @Override
            public int compare(@NonNull String r1, @NonNull String r2) {
                int rank1 = r1.equals(primary) ? 1
                        : r1.equals(secondary) ? 2 : r1.equalsIgnoreCase(language) ? 3 : 4;
                int rank2 = r2.equals(primary) ? 1
                        : r2.equals(secondary) ? 2 : r2.equalsIgnoreCase(language) ? 3 : 4;
                int delta = rank1 - rank2;
                if (delta == 0) {
                    delta = r1.compareTo(r2);
                }
                return delta;
            }
        });
        return sortedRegions;
    }
}
