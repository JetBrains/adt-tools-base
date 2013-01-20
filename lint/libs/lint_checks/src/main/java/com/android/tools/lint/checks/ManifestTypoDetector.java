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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_LIBRARY;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_SDK;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Maps;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks for typos in AndroidManifest files.
 */
public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final String REPORT_FORMAT
            = "<%1$s> looks like a typo; did you mean <%2$s> ?";

    /* The match pattern for <uses-sdk> */
    private static final Pattern PATTERN_USES_SDK
            = Pattern.compile("^use.*sdk"); //$NON-NLS-1$

    /* The match pattern for <uses-permission> */
    private static final Pattern PATTERN_USES_PERMISSION
            = Pattern.compile("^use.*permission"); //$NON-NLS-1$

    /* The match pattern for <uses-feature> */
    private static final Pattern PATTERN_USES_FEATURE
            = Pattern.compile("^use.*feature"); //$NON-NLS-1$

    /* The match pattern for <uses-library> */
    private static final Pattern PATTERN_USES_LIBRARY
            = Pattern.compile("^use.*library"); //$NON-NLS-1$

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestTypo", //$NON-NLS-1$
            "Checks for manifest typos",

            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            ManifestTypoDetector.class,
            Scope.MANIFEST_SCOPE);

    /** Constructs a new {@link ManifestTypoDetector} check */
    public ManifestTypoDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return file.getName().equals(ANDROID_MANIFEST_XML);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (!tag.startsWith("use")) { //$NON-NLS-1$
            return;
        }

        if (PATTERN_USES_SDK.matcher(tag).find() && !TAG_USES_SDK.equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    String.format(REPORT_FORMAT, tag, TAG_USES_SDK), null);
        }

        if (PATTERN_USES_PERMISSION.matcher(tag).find() && !TAG_USES_PERMISSION.equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    String.format(REPORT_FORMAT, tag, TAG_USES_PERMISSION), null);
        }

        if (PATTERN_USES_FEATURE.matcher(tag).find() && !TAG_USES_FEATURE.equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    String.format(REPORT_FORMAT, tag, TAG_USES_FEATURE), null);
        }

        if (PATTERN_USES_LIBRARY.matcher(tag).find() && !TAG_USES_LIBRARY.equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    String.format(REPORT_FORMAT, tag, TAG_USES_LIBRARY), null);
        }
    }
}
