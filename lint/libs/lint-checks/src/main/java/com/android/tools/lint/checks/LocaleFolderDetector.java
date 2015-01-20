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
import com.android.ide.common.resources.LocaleManager;
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
import com.google.common.collect.Lists;

import java.util.List;

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
            String language = locale.getFirst();
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

                String region = locale.getSecond();
                if (region != null) {
                    List<String> relevantRegions = LocaleManager.getRelevantRegions(language);
                    if (!relevantRegions.isEmpty() && !relevantRegions.contains(region)) {
                        List<String> suggestions = Lists.newArrayList();
                        for (String code : relevantRegions) {
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
    }
}
