/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ATTR_LOCALE;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TRANSLATABLE;
import static com.android.SdkConstants.STRING_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING;
import static com.android.SdkConstants.TAG_STRING_ARRAY;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks for incomplete translations - e.g. keys that are only present in some
 * locales but not all.
 */
public class TranslationDetector extends ResourceXmlDetector {
    @VisibleForTesting
    static boolean sCompleteRegions =
            System.getenv("ANDROID_LINT_COMPLETE_REGIONS") != null; //$NON-NLS-1$

    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}$"); //$NON-NLS-1$
    private static final Pattern REGION_PATTERN = Pattern.compile("^r([A-Z]{2})$"); //$NON-NLS-1$

    private static final Implementation IMPLEMENTATION = new Implementation(
            TranslationDetector.class,
            Scope.ALL_RESOURCES_SCOPE);

    /** Are all translations complete? */
    public static final Issue MISSING = Issue.create(
            "MissingTranslation", //$NON-NLS-1$
            "Incomplete translation",
            "Checks for incomplete translations where not all strings are translated",
            "If an application has more than one locale, then all the strings declared in " +
            "one language should also be translated in all other languages.\n" +
            "\n" +
            "If the string should *not* be translated, you can add the attribute " +
            "`translatable=\"false\"` on the `<string>` element, or you can define all " +
            "your non-translatable strings in a resource file called `donottranslate.xml`. " +
            "Or, you can ignore the issue with a `tools:ignore=\"MissingTranslation\"` " +
            "attribute.\n" +
            "\n" +
            "By default this detector allows regions of a language to just provide a " +
            "subset of the strings and fall back to the standard language strings. " +
            "You can require all regions to provide a full translation by setting the " +
            "environment variable `ANDROID_LINT_COMPLETE_REGIONS`.\n" +
            "\n" +
            "You can tell lint (and other tools) which language is the default language " +
            "in your `res/values/` folder by specifying `tools:locale=\"languageCode\"` for " +
            "the root `<resources>` element in your resource file. (The `tools` prefix refers " +
            "to the namespace declaration `http://schemas.android.com/tools`.)",
            Category.MESSAGES,
            8,
            Severity.FATAL,
            IMPLEMENTATION);

    /** Are there extra translations that are "unused" (appear only in specific languages) ? */
    public static final Issue EXTRA = Issue.create(
            "ExtraTranslation", //$NON-NLS-1$
            "Extra translation",
            "Checks for translations that appear to be unused (no default language string)",
            "If a string appears in a specific language translation file, but there is " +
            "no corresponding string in the default locale, then this string is probably " +
            "unused. (It's technically possible that your application is only intended to " +
            "run in a specific locale, but it's still a good idea to provide a fallback.).\n" +
            "\n" +
            "Note that these strings can lead to crashes if the string is looked up on any " +
            "locale not providing a translation, so it's important to clean them up.",
            Category.MESSAGES,
            6,
            Severity.FATAL,
            IMPLEMENTATION);

    private Set<String> mNames;
    private Set<String> mTranslatedArrays;
    private Set<String> mNonTranslatable;
    private boolean mIgnoreFile;
    private Map<File, Set<String>> mFileToNames;
    private Map<File, String> mFileToLocale;

    /** Locations for each untranslated string name. Populated during phase 2, if necessary */
    private Map<String, Location> mMissingLocations;

    /** Locations for each extra translated string name. Populated during phase 2, if necessary */
    private Map<String, Location> mExtraLocations;

    /** Error messages for each untranslated string name. Populated during phase 2, if necessary */
    private Map<String, String> mDescriptions;

    /** Constructs a new {@link TranslationDetector} */
    public TranslationDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_STRING,
                TAG_STRING_ARRAY
        );
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getDriver().getPhase() == 1) {
            mFileToNames = new HashMap<File, Set<String>>();
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mNames = new HashSet<String>();
        }

        // Convention seen in various projects
        mIgnoreFile = context.file.getName().startsWith("donottranslate") //$NON-NLS-1$
                        || UnusedResourceDetector.isAnalyticsFile(context);

        if (!context.getProject().getReportIssues()) {
            mIgnoreFile = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // Store this layout's set of ids for full project analysis in afterCheckProject
            if (context.getProject().getReportIssues() && mNames != null) {
                mFileToNames.put(context.file, mNames);

                Element root = ((XmlContext) context).document.getDocumentElement();
                if (root != null) {
                    String locale = root.getAttributeNS(TOOLS_URI, ATTR_LOCALE);
                    if (locale != null && !locale.isEmpty()) {
                        if (mFileToLocale == null) {
                            mFileToLocale = Maps.newHashMap();
                        }
                        mFileToLocale.put(context.file, locale);
                    }
                }
            }

            mNames = null;
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // NOTE - this will look for the presence of translation strings.
            // If you create a resource folder but don't actually place a file in it
            // we won't detect that, but it seems like a smaller problem.

            checkTranslations(context);

            mFileToNames = null;

            if (mMissingLocations != null || mExtraLocations != null) {
                context.getDriver().requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
        } else {
            assert context.getPhase() == 2;

            reportMap(context, MISSING, mMissingLocations);
            reportMap(context, EXTRA, mExtraLocations);
            mMissingLocations = null;
            mExtraLocations = null;
            mDescriptions = null;
        }
    }

    private void reportMap(Context context, Issue issue, Map<String, Location> map) {
        if (map != null) {
            for (Map.Entry<String, Location> entry : map.entrySet()) {
                Location location = entry.getValue();
                String name = entry.getKey();
                String message = mDescriptions.get(name);

                // We were prepending locations, but we want to prefer the base folders
                location = Location.reverse(location);

                context.report(issue, location, message, null);
            }
        }
    }

    private void checkTranslations(Context context) {
        // Only one file defining strings? If so, no problems.
        Set<File> files = mFileToNames.keySet();
        if (files.size() == 1) {
            return;
        }

        Set<File> parentFolders = new HashSet<File>();
        for (File file : files) {
            parentFolders.add(file.getParentFile());
        }
        if (parentFolders.size() == 1) {
            // Only one language - no problems.
            return;
        }

        boolean reportMissing = context.isEnabled(MISSING);
        boolean reportExtra = context.isEnabled(EXTRA);

        // res/strings.xml etc
        String defaultLanguage = "Default";

        Map<File, String> parentFolderToLanguage = new HashMap<File, String>();
        for (File parent : parentFolders) {
            String name = parent.getName();

            // Look up the language for this folder.
            String language = getLanguage(name);
            if (language == null) {
                language = defaultLanguage;
            }

            parentFolderToLanguage.put(parent, language);
        }

        int languageCount = parentFolderToLanguage.values().size();
        if (languageCount <= 1) {
            // At most one language -- no problems.
            return;
        }

        // Merge together the various files building up the translations for each language
        Map<String, Set<String>> languageToStrings =
                new HashMap<String, Set<String>>(languageCount);
        Set<String> allStrings = new HashSet<String>(200);
        for (File file : files) {
            String language = null;
            if (mFileToLocale != null) {
                String locale = mFileToLocale.get(file);
                if (locale != null) {
                    int index = locale.indexOf('-');
                    if (index != -1) {
                        locale = locale.substring(0, index);
                    }
                    language = locale;
                }
            }
            if (language == null) {
                language = parentFolderToLanguage.get(file.getParentFile());
            }
            assert language != null : file.getParent();
            Set<String> fileStrings = mFileToNames.get(file);

            Set<String> languageStrings = languageToStrings.get(language);
            if (languageStrings == null) {
                // We don't need a copy; we're done with the string tables now so we
                // can modify them
                languageToStrings.put(language, fileStrings);
            } else {
                languageStrings.addAll(fileStrings);
            }
            allStrings.addAll(fileStrings);
        }

        Set<String> defaultStrings = languageToStrings.get(defaultLanguage);
        if (defaultStrings == null) {
            defaultStrings = new HashSet<String>();

            // See if it looks like the user has named a specific locale as the base language
            // (this impacts whether we report strings as "extra" or "missing")
            if (mFileToLocale != null) {
                Set<String> specifiedLocales = Sets.newHashSet();
                for (Map.Entry<File, String> entry : mFileToLocale.entrySet()) {
                    String locale = entry.getValue();
                    int index = locale.indexOf('-');
                    if (index != -1) {
                        locale = locale.substring(0, index);
                    }
                    specifiedLocales.add(locale);
                }
                if (specifiedLocales.size() == 1) {
                    String first = specifiedLocales.iterator().next();
                    Set<String> languageStrings = languageToStrings.get(first);
                    assert languageStrings != null;
                    defaultStrings.addAll(languageStrings);
                }
            }
        }

        // Fast check to see if there's no problem: if the default locale set is the
        // same as the all set (meaning there are no extra strings in the other languages)
        // then we can quickly determine if everything is okay by just making sure that
        // each language defines everything. If that's the case they will all have the same
        // string count.
        int stringCount = allStrings.size();
        if (stringCount == defaultStrings.size()) {
            boolean haveError = false;
            for (Map.Entry<String, Set<String>> entry : languageToStrings.entrySet()) {
                Set<String> strings = entry.getValue();
                if (stringCount != strings.size()) {
                    haveError = true;
                    break;
                }
            }
            if (!haveError) {
                return;
            }
        }

        // Do we need to resolve fallback strings for regions that only define a subset
        // of the strings in the language and fall back on the main language for the rest?
        if (!sCompleteRegions) {
            for (String l : languageToStrings.keySet()) {
                if (l.indexOf('-') != -1) {
                    // Yes, we have regions. Merge all base language string names into each region.
                    for (Map.Entry<String, Set<String>> entry : languageToStrings.entrySet()) {
                        Set<String> strings = entry.getValue();
                        if (stringCount != strings.size()) {
                            String languageRegion = entry.getKey();
                            int regionIndex = languageRegion.indexOf('-');
                            if (regionIndex != -1) {
                                String language = languageRegion.substring(0, regionIndex);
                                Set<String> fallback = languageToStrings.get(language);
                                if (fallback != null) {
                                    strings.addAll(fallback);
                                }
                            }
                        }
                    }
                    // We only need to do this once; when we see the first region we know
                    // we need to do it; once merged we can bail
                    break;
                }
            }
        }

        List<String> languages = new ArrayList<String>(languageToStrings.keySet());
        Collections.sort(languages);
        for (String language : languages) {
            Set<String> strings = languageToStrings.get(language);
            if (defaultLanguage.equals(language)) {
                continue;
            }

            // if strings.size() == stringCount, then this language is defining everything,
            // both all the default language strings and the union of all extra strings
            // defined in other languages, so there's no problem.
            if (stringCount != strings.size()) {
                if (reportMissing) {
                    Set<String> difference = Sets.difference(defaultStrings, strings);
                    if (!difference.isEmpty()) {
                        if (mMissingLocations == null) {
                            mMissingLocations = new HashMap<String, Location>();
                        }
                        if (mDescriptions == null) {
                            mDescriptions = new HashMap<String, String>();
                        }

                        for (String s : difference) {
                            mMissingLocations.put(s, null);
                            String message = mDescriptions.get(s);
                            if (message == null) {
                                message = String.format("\"%1$s\" is not translated in %2$s",
                                        s, language);
                            } else {
                                message = message + ", " + language;
                            }
                            mDescriptions.put(s, message);
                        }
                    }
                }

                if (reportExtra) {
                    Set<String> difference = Sets.difference(strings, defaultStrings);
                    if (!difference.isEmpty()) {
                        if (mExtraLocations == null) {
                            mExtraLocations = new HashMap<String, Location>();
                        }
                        if (mDescriptions == null) {
                            mDescriptions = new HashMap<String, String>();
                        }

                        for (String s : difference) {
                            if (mTranslatedArrays != null && mTranslatedArrays.contains(s)) {
                                continue;
                            }
                            mExtraLocations.put(s, null);
                            String message = String.format(
                                "\"%1$s\" is translated here but not found in default locale", s);
                            mDescriptions.put(s, message);
                        }
                    }
                }
            }
        }
    }

    /** Look up the language for the given folder name */
    private static String getLanguage(String name) {
        String[] segments = name.split("-"); //$NON-NLS-1$

        // TODO: To get an accurate answer, this should later do a
        //   FolderConfiguration.getConfig(String[] folderSegments)
        // to obtain a FolderConfiguration, then call
        // getLanguageQualifier() on it, and if not null, call getValue() to get the
        // actual language value.
        // However, we don't have sdk-common on the build path for lint, so for now
        // use a simple guess about what constitutes a language qualifier here:

        String language = null;
        for (String segment : segments) {
            // Language
            if (language == null && segment.length() == 2
                    && LANGUAGE_PATTERN.matcher(segment).matches()) {
                language = segment;
            }

            // Add in region
            if (language != null && segment.length() == 3
                    && REGION_PATTERN.matcher(segment).matches()) {
                language = language + '-' + segment;
                break;
            }
        }

        return language;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mIgnoreFile) {
            return;
        }

        Attr attribute = element.getAttributeNode(ATTR_NAME);

        if (context.getPhase() == 2) {
            // Just locating names requested in the {@link #mLocations} map
            if (attribute == null) {
                return;
            }
            String name = attribute.getValue();
            if (mMissingLocations != null && mMissingLocations.containsKey(name)) {
                String language = getLanguage(context.file.getParentFile().getName());
                if (language == null) {
                    if (context.getDriver().isSuppressed(MISSING, element)) {
                        mMissingLocations.remove(name);
                        return;
                    }

                    Location location = context.getLocation(attribute);
                    location.setClientData(element);
                    location.setSecondary(mMissingLocations.get(name));
                    mMissingLocations.put(name, location);
                }
            }
            if (mExtraLocations != null && mExtraLocations.containsKey(name)) {
                if (context.getDriver().isSuppressed(EXTRA, element)) {
                    mExtraLocations.remove(name);
                    return;
                }
                Location location = context.getLocation(attribute);
                location.setClientData(element);
                location.setMessage("Also translated here");
                location.setSecondary(mExtraLocations.get(name));
                mExtraLocations.put(name, location);
            }
            return;
        }

        assert context.getPhase() == 1;
        if (attribute == null || attribute.getValue().isEmpty()) {
            context.report(MISSING, element, context.getLocation(element),
                    "Missing name attribute in <string> declaration", null);
        } else {
            String name = attribute.getValue();

            Attr translatable = element.getAttributeNode(ATTR_TRANSLATABLE);
            if (translatable != null && !Boolean.valueOf(translatable.getValue())) {
                String l = LintUtils.getLocaleAndRegion(context.file.getParentFile().getName());
                if (l != null) {
                    context.report(EXTRA, translatable, context.getLocation(translatable),
                        "Non-translatable resources should only be defined in the base " +
                        "values/ folder", null);
                } else {
                    if (mNonTranslatable == null) {
                        mNonTranslatable = new HashSet<String>();
                    }
                    mNonTranslatable.add(name);
                }
                return;
            }

            if (element.getTagName().equals(TAG_STRING_ARRAY) &&
                    allItemsAreReferences(element)) {
                // No need to provide translations for string arrays where all
                // the children items are defined as translated string resources,
                // e.g.
                //    <string-array name="foo">
                //       <item>@string/item1</item>
                //       <item>@string/item2</item>
                //    </string-array>
                // However, we need to remember these names such that we don't consider
                // these arrays "extra" if one of the *translated* versions of the array
                // perform an inline translation of an array item
                if (mTranslatedArrays == null) {
                    mTranslatedArrays = new HashSet<String>();
                }
                mTranslatedArrays.add(name);
                return;
            }

            // Check for duplicate name definitions? No, because there can be
            // additional customizations like product=
            //if (mNames.contains(name)) {
            //    context.mClient.report(ISSUE, context.getLocation(attribute),
            //        String.format("Duplicate name %1$s, already defined earlier in this file",
            //            name));
            //}

            mNames.add(name);

            if (mNonTranslatable != null && mNonTranslatable.contains(name)) {
                String message = String.format("The resource string \"%1$s\" has been marked as " +
                        "translatable=\"false\"", name);
                context.report(EXTRA, attribute, context.getLocation(attribute), message, null);
            }

            // TBD: Also make sure that the strings are not empty or placeholders?
        }
    }

    private static boolean allItemsAreReferences(Element element) {
        assert element.getTagName().equals(TAG_STRING_ARRAY);
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node item = childNodes.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_ITEM.equals(item.getNodeName())) {
                NodeList itemChildren = item.getChildNodes();
                for (int j = 0, m = itemChildren.getLength(); j < m; j++) {
                    Node valueNode = itemChildren.item(j);
                    if (valueNode.getNodeType() == Node.TEXT_NODE) {
                        String value = valueNode.getNodeValue().trim();
                        if (!value.startsWith(ANDROID_PREFIX)
                                && !value.startsWith(STRING_PREFIX)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }
}
