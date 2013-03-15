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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for cycles in style definitions
 */
public class StyleCycleDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "StyleCycle", //$NON-NLS-1$
            "Cycle in style definitions",
            "Looks for cycles in style definitions",
            "There should be no cycles in style definitions as this can lead to runtime " +
            "exceptions.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    StyleCycleDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo(
            "http://developer.android.com/guide/topics/ui/themes.html#Inheritance"); //$NON-NLS-1$

    /** Constructs a new {@link StyleCycleDetector} */
    public StyleCycleDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(TAG_STYLE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr parentNode = element.getAttributeNode(ATTR_PARENT);
        if (parentNode != null) {
            String parent = parentNode.getValue();
            String name = element.getAttribute(ATTR_NAME);
            if (parent.endsWith(name) &&
                    parent.equals(STYLE_RESOURCE_PREFIX + name)) {
                context.report(ISSUE, parentNode, context.getLocation(parentNode),
                        String.format("Style %1$s should not extend itself", name), null);
            } else if (parent.startsWith(STYLE_RESOURCE_PREFIX)
                    && parent.startsWith(name, STYLE_RESOURCE_PREFIX.length())
                    && parent.startsWith(".", STYLE_RESOURCE_PREFIX.length() + name.length())) {
                context.report(ISSUE, parentNode, context.getLocation(parentNode),
                        String.format("Potential cycle: %1$s is the implied parent of %2$s and " +
                                "this defines the opposite", name,
                                parent.substring(STYLE_RESOURCE_PREFIX.length())), null);
            }
        }
    }
}
