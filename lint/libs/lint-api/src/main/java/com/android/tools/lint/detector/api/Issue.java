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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.Configuration;
import com.google.common.annotations.Beta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * An issue is a potential bug in an Android application. An issue is discovered
 * by a {@link Detector}, and has an associated {@link Severity}.
 * <p>
 * Issues and detectors are separate classes because a detector can discover
 * multiple different issues as it's analyzing code, and we want to be able to
 * different severities for different issues, the ability to suppress one but
 * not other issues from the same detector, and so on.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public final class Issue implements Comparable<Issue> {
    private static final String HTTP_PREFIX = "http://"; //$NON-NLS-1$

    private final String mId;
    private final String mBriefDescription;
    private final String mDescription;
    private final String mExplanation;
    private final Category mCategory;
    private final int mPriority;
    private final Severity mSeverity;
    private Object mMoreInfoUrls;
    private boolean mEnabledByDefault = true;
    private Implementation mImplementation;

    // Use factory methods
    private Issue(
            @NonNull String id,
            @NonNull String shortDescription,
            @NonNull String description,
            @NonNull String explanation,
            @NonNull Category category,
            int priority,
            @NonNull Severity severity,
            @NonNull Implementation implementation) {
        assert !shortDescription.isEmpty();
        assert !description.isEmpty();
        assert !explanation.isEmpty();

        mId = id;
        mBriefDescription = shortDescription;
        mDescription = description;
        mExplanation = explanation;
        mCategory = category;
        mPriority = priority;
        mSeverity = severity;
        mImplementation = implementation;
    }

    /**
     * Creates a new issue. The description strings can use some simple markup;
     * see the {@link #convertMarkup(String, boolean)} method for details.
     *
     * @param id the fixed id of the issue
     * @param briefDescription short summary (typically 5-6 words or less), typically
     *                         describing the <b>problem</b> rather than the <b>fix</b>
     *                         (e.g. "Missing minSdkVersion")
     * @param description the quick summary of the issue (one line), typically describing
     *                    what the detector looks for
     * @param explanation a full explanation of the issue, with suggestions for
     *            how to fix it
     * @param category the associated category, if any
     * @param priority the priority, a number from 1 to 10 with 10 being most
     *            important/severe
     * @param severity the default severity of the issue
     * @param implementation the default implementation for this issue
     * @return a new {@link Issue}
     */
    @NonNull
    public static Issue create(
            @NonNull String id,
            @NonNull String briefDescription,
            @NonNull String description,
            @NonNull String explanation,
            @NonNull Category category,
            int priority,
            @NonNull Severity severity,
            @NonNull Implementation implementation) {
        return new Issue(id, briefDescription, description, explanation, category, priority,
                severity, implementation);
    }

    /**
     * Returns the unique id of this issue. These should not change over time
     * since they are used to persist the names of issues suppressed by the user
     * etc. It is typically a single camel-cased word.
     *
     * @return the associated fixed id, never null and always unique
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Briefly (in a couple of words) describes these errors
     *
     * @return a brief summary of the issue, never null, never empty
     * @see #getDescription(OutputFormat)
     */
    @NonNull
    public String getBriefDescription(@NonNull OutputFormat format) {
        switch (format) {
            case TEXT:
                return convertMarkup(mBriefDescription, false /* not html = text */);
            case HTML:
                return convertMarkup(mBriefDescription, true /* html */);
            case RAW:
            default:
                return mBriefDescription;
        }
    }

    /**
     * Describes in a single line the kinds of checks performed by this rule
     *
     * @return a quick summary of the issue, never null, never empty
     * @see #getBriefDescription(OutputFormat)
     */
    @NonNull
    public String getDescription(@NonNull OutputFormat format) {
        switch (format) {
            case TEXT:
                return convertMarkup(mDescription, false /* not html = text */);
            case HTML:
                return convertMarkup(mDescription, true /* html */);
            case RAW:
            default:
                return mDescription;
        }
    }

    /**
     * Describes the error found by this rule, e.g.
     * "Buttons must define contentDescriptions". Preferably the explanation
     * should also contain a description of how the problem should be solved.
     * Additional info can be provided via {@link #getMoreInfo()}.
     *
     * @param format the format to write the format as
     * @return an explanation of the issue, never null, never empty
     */
    @NonNull
    public String getExplanation(@NonNull OutputFormat format) {
        switch (format) {
            case TEXT:
                return convertMarkup(mExplanation, false /* not html = text */);
            case HTML:
                return convertMarkup(mExplanation, true /* html */);
            case RAW:
            default:
                return mExplanation;
        }
    }

    /**
     * The primary category of the issue
     *
     * @return the primary category of the issue, never null
     */
    @NonNull
    public Category getCategory() {
        return mCategory;
    }

    /**
     * Returns a priority, in the range 1-10, with 10 being the most severe and
     * 1 the least
     *
     * @return a priority from 1 to 10
     */
    public int getPriority() {
        return mPriority;
    }

    /**
     * Returns the default severity of the issues found by this detector (some
     * tools may allow the user to specify custom severities for detectors).
     * <p>
     * Note that even though the normal way for an issue to be disabled is for
     * the {@link Configuration} to return {@link Severity#IGNORE}, there is a
     * {@link #isEnabledByDefault()} method which can be used to turn off issues
     * by default. This is done rather than just having the severity as the only
     * attribute on the issue such that an issue can be configured with an
     * appropriate severity (such as {@link Severity#ERROR}) even when issues
     * are disabled by default for example because they are experimental or not
     * yet stable.
     *
     * @return the severity of the issues found by this detector
     */
    @NonNull
    public Severity getDefaultSeverity() {
        return mSeverity;
    }

    /**
     * Returns a link (a URL string) to more information, or null
     *
     * @return a link to more information, or null
     */
    @NonNull
    public List<String> getMoreInfo() {
        if (mMoreInfoUrls == null) {
            return Collections.emptyList();
        } else if (mMoreInfoUrls instanceof String) {
            return Collections.singletonList((String) mMoreInfoUrls);
        } else {
            assert mMoreInfoUrls instanceof List;
            //noinspection unchecked
            return (List<String>) mMoreInfoUrls;
        }
    }

    /**
     * Adds a more info URL string
     *
     * @param moreInfoUrl url string
     * @return this, for constructor chaining
     */
    @NonNull
    public Issue addMoreInfo(@NonNull String moreInfoUrl) {
        // Nearly all issues supply at most a single URL, so don't bother with
        // lists wrappers for most of these issues
        if (mMoreInfoUrls == null) {
            mMoreInfoUrls = moreInfoUrl;
        } else if (mMoreInfoUrls instanceof String) {
            String existing = (String) mMoreInfoUrls;
            List<String> list = new ArrayList<String>(2);
            list.add(existing);
            list.add(moreInfoUrl);
            mMoreInfoUrls = list;
        } else {
            assert mMoreInfoUrls instanceof List;
            ((List<String>) mMoreInfoUrls).add(moreInfoUrl);
        }
        return this;
    }

    /**
     * Returns whether this issue should be enabled by default, unless the user
     * has explicitly disabled it.
     *
     * @return true if this issue should be enabled by default
     */
    public boolean isEnabledByDefault() {
        return mEnabledByDefault;
    }

    /**
     * Returns the implementation for the given issue
     *
     * @return the implementation for this issue
     */
    @NonNull
    public Implementation getImplementation() {
        return mImplementation;
    }

    /**
     * Sets the implementation for the given issue. This is typically done by
     * IDEs that can offer a replacement for a given issue which performs better
     * or in some other way works better within the IDE.
     *
     * @param implementation the new implementation to use
     */
    public void setImplementation(@NonNull Implementation implementation) {
        mImplementation = implementation;
    }

    /**
     * Sorts the detectors alphabetically by id. This is intended to make it
     * convenient to store settings for detectors in a fixed order. It is not
     * intended as the order to be shown to the user; for that, a tool embedding
     * lint might consider the priorities, categories, severities etc of the
     * various detectors.
     *
     * @param other the {@link Issue} to compare this issue to
     */
    @Override
    public int compareTo(Issue other) {
        return getId().compareTo(other.getId());
    }

    /**
     * Sets whether this issue is enabled by default.
     *
     * @param enabledByDefault whether the issue should be enabled by default
     * @return this, for constructor chaining
     */
    @NonNull
    public Issue setEnabledByDefault(boolean enabledByDefault) {
        mEnabledByDefault = enabledByDefault;
        return this;
    }

    @Override
    public String toString() {
        return mId;
    }

    /**
     * Converts the given markup text to HTML or text, depending on the.
     * <p>
     * This will recognize the following formatting conventions:
     * <ul>
     * <li>HTTP urls (http://...)
     * <li>Sentences immediately surrounded by * will be shown as bold.
     * <li>Sentences immediately surrounded by ` will be shown using monospace
     * fonts
     * </ul>
     * Furthermore, newlines are converted to br's when converting newlines.
     * Note: It does not insert {@code <html>} tags around the fragment for HTML output.
     * <p>
     * TODO: Consider switching to the restructured text format -
     *  http://docutils.sourceforge.net/docs/user/rst/quickstart.html
     *
     * @param text the text to be formatted
     * @param html whether to convert into HTML or text
     * @return the corresponding HTML or text properly formatted
     */
    @NonNull
    public static String convertMarkup(@NonNull String text, boolean html) {
        StringBuilder sb = new StringBuilder(3 * text.length() / 2);

        char prev = 0;
        int flushIndex = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if ((c == '*' || c == '`' && i < n - 1)) {
                // Scout ahead for range end
                if (!Character.isLetterOrDigit(prev)
                        && !Character.isWhitespace(text.charAt(i + 1))) {
                    // Found * or ~ immediately before a letter, and not in the middle of a word
                    // Find end
                    int end = text.indexOf(c, i + 1);
                    if (end != -1 && (end == n - 1 || !Character.isLetter(text.charAt(end + 1)))) {
                        if (i > flushIndex) {
                            appendEscapedText(sb, text, html, flushIndex, i);
                        }
                        if (html) {
                            String tag = c == '*' ? "b" : "code"; //$NON-NLS-1$ //$NON-NLS-2$
                            sb.append('<').append(tag).append('>');
                            appendEscapedText(sb, text, html, i + 1, end);
                            sb.append('<').append('/').append(tag).append('>');
                        } else {
                            appendEscapedText(sb, text, html, i + 1, end);
                        }
                        flushIndex = end + 1;
                        i = flushIndex - 1; // -1: account for the i++ in the loop
                    }
                }
            } else if (html && c == 'h' && i < n - 1 && text.charAt(i + 1) == 't'
                    && text.startsWith(HTTP_PREFIX, i) && !Character.isLetterOrDigit(prev)) {
                // Find url end
                int end = i + HTTP_PREFIX.length();
                while (end < n) {
                    char d = text.charAt(end);
                    if (Character.isWhitespace(d)) {
                        break;
                    }
                    end++;
                }
                char last = text.charAt(end - 1);
                if (last == '.' || last == ')' || last == '!') {
                    end--;
                }
                if (end > i + HTTP_PREFIX.length()) {
                    if (i > flushIndex) {
                        appendEscapedText(sb, text, html, flushIndex, i);
                    }

                    String url = text.substring(i, end);
                    sb.append("<a href=\"");        //$NON-NLS-1$
                    sb.append(url);
                    sb.append('"').append('>');
                    sb.append(url);
                    sb.append("</a>");              //$NON-NLS-1$

                    flushIndex = end;
                    i = flushIndex - 1; // -1: account for the i++ in the loop
                }
            }
            prev = c;
        }

        if (flushIndex < n) {
            appendEscapedText(sb, text, html, flushIndex, n);
        }

        return sb.toString();
    }

    private static void appendEscapedText(StringBuilder sb, String text, boolean html,
            int start, int end) {
        if (html) {
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c == '<') {
                    sb.append("&lt;");                                   //$NON-NLS-1$
                } else if (c == '&') {
                    sb.append("&amp;");                                  //$NON-NLS-1$
                } else if (c == '\n') {
                    sb.append("<br/>\n");
                } else {
                    if (c > 255) {
                        sb.append("&#");                                 //$NON-NLS-1$
                        sb.append(Integer.toString(c));
                        sb.append(';');
                    } else {
                        sb.append(c);
                    }
                }
            }
        } else {
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                sb.append(c);
            }
        }
    }

    /**
     * The format of output from the description methods
     * @see #getDescription(OutputFormat)
     * @see Issue#getExplanation(OutputFormat)
     * @see Issue#getBriefDescription(OutputFormat)
     * */
    public enum OutputFormat {
        /** Raw output format, including markup with asterisks for bold etc */
        RAW,
        /** Simple text output */
        TEXT,
        /** HTML formatted output */
        HTML;
    }
}
