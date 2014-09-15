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

/**
 * Checks for errors related to locale handling
 */
public class LocaleFolderDetector extends Detector implements Detector.ResourceFolderScanner {
    public static final Implementation IMPLEMENTATION = new Implementation(
            LocaleFolderDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE);

    /**
     * Using a locale folder that is not consulted
     */
    public static final Issue ISSUE = Issue.create(
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
            new Implementation(
                    LocaleFolderDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE)).addMoreInfo(
            "http://developer.android.com/reference/java/util/Locale.html");

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
                    context.report(ISSUE, Location.create(context.file), message);
                }
            }
        }
    }
}
