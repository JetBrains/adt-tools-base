/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

/**
 * Check which looks for potential errors in declarations of ConstraintLayout, such as
 * under specifying constraints
 */
public class ConstraintLayoutDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "MissingConstraints", //$NON-NLS-1$
            "Missing Constraints in ConstraintLayout",
            "The layout editor allows you to place widgets anywhere on the canvas, and it " +
            "records the current position with designtime attributes (such as " +
            "`layout_editor_absoluteX`.) These attributes are *not* applied at runtime, so if " +
            "you push your layout on a device, the widgets may appear in a different location " +
            "than shown in the editor. To fix this, make sure a widget has both horizontal and " +
            "vertical constraints by dragging from the edge connections.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    ConstraintLayoutDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String LAYOUT_CONSTRAINT_PREFIX = "layout_constraint";

    /** Constructs a new {@link ConstraintLayoutDetector} check */
    public ConstraintLayoutDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(CONSTRAINT_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element layout) {
        // Ensure that all the children have been constrained horizontally and vertically
        for (Node child = layout.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element)child;

            if (element.getTagName().equals(CLASS_CONSTRAINT_LAYOUT_GUIDELINE)) {
                continue;
            }

            boolean isConstrainedHorizontally = false;
            boolean isConstrainedVertically = false;

            // See if the layout doesn't use absoluteX/Y designtime positions for this
            // child; if it doesn't, no need to complain

            if (!element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)) {
                // Not technically a constraint, but we'll use this to not complain
                // about lacking constraints in this dimension
                isConstrainedHorizontally = true;
            }
            if (!element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)) {
                isConstrainedVertically = true;
                if (isConstrainedHorizontally) {
                    // Nothing to check
                    continue;
                }
            }


            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String name = attribute.getLocalName();
                if (name == null) {
                    continue;
                }
                if (!name.startsWith(LAYOUT_CONSTRAINT_PREFIX) || name.endsWith("_creator")) {
                    continue;
                }
                if (name.endsWith("toLeftOf")
                        || name.endsWith("toRightOf")
                        || name.endsWith("toStartOf")
                        || name.endsWith("toEndOf")
                        || name.endsWith("toCenterX")) {
                    isConstrainedHorizontally = true;
                    if (isConstrainedVertically) {
                        break;
                    }
                } else if (name.endsWith("toTopOf")
                        || name.endsWith("toBottomOf")
                        || name.endsWith("toCenterY")
                        || name.endsWith("toBaselineOf")) {
                    isConstrainedVertically = true;
                    if (isConstrainedHorizontally) {
                        break;
                    }
                }
            }

            if (!isConstrainedHorizontally || !isConstrainedVertically) {
                // Don't complain if the element doesn't specify absolute x/y - that's
                // when it gets confusing

                String message;
                if (isConstrainedVertically) {
                    message = "This view is not constrained horizontally: at runtime it will "
                            + "jump to the left unless you add a horizontal constraint";
                } else if (isConstrainedHorizontally) {
                    message = "This view is not constrained vertically: at runtime it will "
                            + "jump to the left unless you add a vertical constraint";
                } else {
                    message = "This view is not constrained, it only has designtime positions, "
                            + "so it will jump to (0,0) unless you add constraints";
                }
                context.report(ISSUE, context.getLocation(element), message);
            }
        }
    }
}
