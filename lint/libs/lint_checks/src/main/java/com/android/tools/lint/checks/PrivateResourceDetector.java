/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Collection;

/**
 * Check which looks for access of private resources.
 */
public class PrivateResourceDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "PrivateResource", //$NON-NLS-1$
            "Using private resources",
            "Looks for references to private resources",

            "Private resources should not be referenced; the may not be present everywhere, and " +
            "even where they are they may disappear without notice.\n" +
            "\n" +
            "To fix this, copy the resource into your own project. You can find the platform " +
            "resources under `$ANDROID_SK/platforms/android-$VERSION/data/res/.`",
            Category.CORRECTNESS,
            3,
            Severity.FATAL,
            new Implementation(
                    PrivateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new detector */
    public PrivateResourceDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getNodeValue();
        if (value.startsWith("@*android:")) { //$NON-NLS-1$
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Illegal resource reference: @*android resources are private and " +
                    "not always present", null);
        }
    }
}
