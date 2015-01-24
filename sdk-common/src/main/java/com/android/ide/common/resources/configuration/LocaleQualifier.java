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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.utils.Pair;
import com.google.common.base.Splitter;

import java.util.Iterator;
import java.util.Locale;

/**
 * Resource Qualifier for a BCP-47 locale tag. The BCP-47 tag
 * uses + instead of - as separators, and have the prefix b+.
 * Therefore, the BCP-47 tag "zh-Hans-CN" would be written
 * as "b+zh+Hans+CN" instead.
 */
public final class LocaleQualifier extends ResourceQualifier {
    public static final String NAME = "Locale";
    public static final String PREFIX = "b+"; //$NON-NLS-1$

    private String mValue;

    /**
     * Creates and returns a qualifier from the given folder segment. If the segment is incorrect,
     * <code>null</code> is returned.
     * @param segment the folder segment from which to create a qualifier.
     * @return a new {@link LocaleQualifier} object or <code>null</code>
     */
    @Nullable
    public static LocaleQualifier getQualifier(@NonNull String segment) {
        if (segment.startsWith(PREFIX)) {
            LocaleQualifier qualifier = new LocaleQualifier();
            qualifier.mValue = normalizeCase(segment);

            return qualifier;
        }
        return null;
    }

    /** Given a BCP-47 string, normalizes the case to the recommended casing */
    @NonNull
    public static String normalizeCase(@NonNull String segment) {
        /* According to the BCP-47 spec:
           o  [ISO639-1] recommends that language codes be written in lowercase
              ('mn' Mongolian).

           o  [ISO15924] recommends that script codes use lowercase with the
              initial letter capitalized ('Cyrl' Cyrillic).

           o  [ISO3166-1] recommends that country codes be capitalized ('MN'
              Mongolia).


           An implementation can reproduce this format without accessing the
           registry as follows.  All subtags, including extension and private
           use subtags, use lowercase letters with two exceptions: two-letter
           and four-letter subtags that neither appear at the start of the tag
           nor occur after singletons.  Such two-letter subtags are all
           uppercase (as in the tags "en-CA-x-ca" or "sgn-BE-FR") and four-
           letter subtags are titlecase (as in the tag "az-Latn-x-latn").
         */
        if (isNormalizedCase(segment)) {
            return segment;
        }

        StringBuilder sb = new StringBuilder(segment.length());
        sb.append(PREFIX);
        assert segment.startsWith(PREFIX);
        int segmentBegin = PREFIX.length();
        int segmentLength = segment.length();
        int start = segmentBegin;

        int lastLength = -1;
        while (start < segmentLength) {
            if (start != segmentBegin) {
                sb.append('+');
            }
            int end = segment.indexOf('+', start);
            if (end == -1) {
                end = segmentLength;
            }
            int length = end - start;
            if ((length != 2 && length != 4) || start == segmentBegin || lastLength == 1) {
                for (int i = start; i < end; i++) {
                    sb.append(Character.toLowerCase(segment.charAt(i)));
                }
            } else if (length == 2) {
                for (int i = start; i < end; i++) {
                    sb.append(Character.toUpperCase(segment.charAt(i)));
                }
            } else {
                assert length == 4 : length;
                sb.append(Character.toUpperCase(segment.charAt(start)));
                for (int i = start + 1; i < end; i++) {
                    sb.append(Character.toLowerCase(segment.charAt(i)));
                }
            }

            lastLength = length;
            start = end + 1;
        }

        return sb.toString();
    }

    /**
     * Given a BCP-47 string, determines whether the string is already
     * capitalized correctly (where "correct" means for readability; all strings
     * should be compared case insensitively)
     */
    @VisibleForTesting
    static boolean isNormalizedCase(@NonNull String segment) {
        assert segment.startsWith(PREFIX);
        int segmentBegin = PREFIX.length();
        int segmentLength = segment.length();
        int start = segmentBegin;

        int lastLength = -1;
        while (start < segmentLength) {
            int end = segment.indexOf('+', start);
            if (end == -1) {
                end = segmentLength;
            }
            int length = end - start;
            if ((length != 2 && length != 4) || start == segmentBegin || lastLength == 1) {
                if (isNotLowerCase(segment, start, end)) {
                    return false;
                }
            } else if (length == 2) {
                if (isNotUpperCase(segment, start, end)) {
                    return false;
                }
            } else {
                assert length == 4 : length;
                if (isNotUpperCase(segment, start, start + 1)) {
                    return false;
                }
                if (isNotLowerCase(segment, start + 1, end)) {
                    return false;
                }
            }

            lastLength = length;
            start = end + 1;
        }

        return true;
    }

    private static boolean isNotLowerCase(@NonNull String segment, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isUpperCase(segment.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNotUpperCase(@NonNull String segment, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isLowerCase(segment.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the folder name segment for the given value. This is equivalent to calling
     * {@link #toString()} on a {@link LocaleQualifier} object.
     * @param value the value of the qualifier, as returned by {@link #getValue()}.
     */
    @Nullable
    public static String getFolderSegment(@NonNull String value) {
        String segment = value.toLowerCase(Locale.US);
        if (segment.startsWith(PREFIX)) {
            return segment;
        }

        return null;
    }

    public LocaleQualifier() {

    }

    public LocaleQualifier(@NonNull String value) {
        mValue = value;
    }

    @NonNull
    public String getValue() {
        if (mValue != null) {
            return mValue;
        }

        return ""; //$NON-NLS-1$
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getShortName() {
        return NAME;
    }

    @Override
    public int since() {
        // This was added in Lollipop, but you can for example write b+en+US and aapt handles it
        // compatibly so we don't want to normalize this in normalize() to append -v21 etc
        return 1;
    }

    @Override
    public boolean isValid() {
        return mValue != null;
    }

    @Override
    public boolean hasFakeValue() {
        return mValue == null || mValue.isEmpty();
    }

    @Override
    public boolean checkAndSet(@NonNull String value, @NonNull FolderConfiguration config) {
        LocaleQualifier qualifier = getQualifier(value);
        if (qualifier != null) {
            config.setLocaleQualifier(qualifier);
            return true;
        }

        return false;
    }

    @Override
    public boolean equals(Object qualifier) {
        if (qualifier instanceof LocaleQualifier) {
            if (mValue == null) {
                return ((LocaleQualifier)qualifier).mValue == null;
            }
            return mValue.equals(((LocaleQualifier)qualifier).mValue);
        }

        return false;
    }

    @Override
    public int hashCode() {
        if (mValue != null) {
            return mValue.hashCode();
        }

        return 0;
    }

    /**
     * Returns the string used to represent this qualifier in the folder name.
     */
    @Override
    public String getFolderSegment() {
        if (mValue != null) {
            return normalizeCase(mValue);
        }

        return ""; //$NON-NLS-1$
    }

    @Override
    public String getShortDisplayValue() {
        if (mValue != null) {
            return mValue;
        }

        return ""; //$NON-NLS-1$
    }

    @Override
    public String getLongDisplayValue() {
        if (mValue != null) {
            return String.format("Locale %s", mValue);
        }

        return ""; //$NON-NLS-1$
    }

    /**
     * Parse an Android BCP-47 string (which differs from BCP-47 in that
     * it has the prefix "b+" and the separator character has been changed from
     * - to +.
     *
     * @param qualifier the folder name to parse
     * @return a pair of language and region strings. The region may be null. The pair
     *     is never null
     */
    @Nullable
    public static Pair<String, String> parseBcp47(@NonNull String qualifier) {
        if (qualifier.startsWith(PREFIX)) {
            Iterator<String> iterator = Splitter.on('+').split(qualifier).iterator();
            // Skip b+ prefix, already checked above
            iterator.next();

            if (iterator.hasNext()) {
                String language = iterator.next();
                String region = null;
                if (language.length() >= 2 && language.length() <= 3) {
                    if (iterator.hasNext()) {
                        String next = iterator.next();
                        if (next.length() == 4) {
                            // Script specified; look for next
                            if (iterator.hasNext()) {
                                next = iterator.next();
                            }
                        } else if (next.length() >= 5) {
                            // Pst region: specifying a variant
                            return Pair.of(language, null);
                        }
                        if (next.length() >= 2 && next.length() <= 3) {
                            region = next;
                        }
                    }
                    return Pair.of(language, region);
                }
            }
        }

        return null;
    }

    @Nullable
    public LanguageQualifier getLanguageQualifier() {
        if (mValue != null && !mValue.isEmpty()) {
            assert mValue.startsWith(PREFIX);
            Pair<String, String> codes = parseBcp47(mValue);
            if (codes != null) {
                String languageCode = codes.getFirst();
                if (languageCode != null) {
                    return new LanguageQualifier(languageCode);
                }
            }
        }

        return null;
    }

    @Nullable
    public RegionQualifier getRegionQualifier() {
        if (mValue != null && !mValue.isEmpty()) {
            assert mValue.startsWith(PREFIX);
            Pair<String, String> codes = parseBcp47(mValue);
            if (codes != null) {
                String regionCode = codes.getSecond();
                if (regionCode != null) {
                    return new RegionQualifier(regionCode);
                }
            }
        }
        return null;
    }
}
