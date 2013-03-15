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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_COLUMN_COUNT;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_ROW_COUNT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

/**
 * Check which looks for potential errors in declarations of GridLayouts, such as specifying
 * row/column numbers outside the declared dimensions of the grid.
 */
public class GridLayoutDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "GridLayout", //$NON-NLS-1$
            "GridLayout validation",
            "Checks for potential GridLayout errors like declaring rows and columns outside " +
            "the declared grid dimensions",
            "Declaring a layout_row or layout_column that falls outside the declared size " +
            "of a GridLayout's `rowCount` or `columnCount` is usually an unintentional error.",
            Category.CORRECTNESS,
            4,
            Severity.FATAL,
            new Implementation(
                    GridLayoutDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link GridLayoutDetector} check */
    public GridLayoutDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(
                "GridLayout" //$NON-NLS-1$
        );
    }

    private static int getInt(Element element, String attribute, int defaultValue) {
        String valueString = element.getAttributeNS(ANDROID_URI, attribute);
        if (valueString != null && !valueString.isEmpty()) {
            try {
                return Integer.decode(valueString);
            } catch (NumberFormatException nufe) {
                // Ignore - error in user's XML
            }
        }

        return defaultValue;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int declaredRowCount = getInt(element, ATTR_ROW_COUNT, -1);
        int declaredColumnCount = getInt(element, ATTR_COLUMN_COUNT, -1);

        if (declaredColumnCount != -1 || declaredRowCount != -1) {
            for (Element child : LintUtils.getChildren(element)) {
                if (declaredColumnCount != -1) {
                    int column = getInt(child, ATTR_LAYOUT_COLUMN, -1);
                    if (column >= declaredColumnCount) {
                        Attr node = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_COLUMN);
                        context.report(ISSUE, node, context.getLocation(node),
                                String.format("Column attribute (%1$d) exceeds declared grid column count (%2$d)",
                                        column, declaredColumnCount), null);
                    }
                }
                if (declaredRowCount != -1) {
                    int row = getInt(child, ATTR_LAYOUT_ROW, -1);
                    if (row > declaredRowCount) {
                        Attr node = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_ROW);
                        context.report(ISSUE, node, context.getLocation(node),
                                String.format("Row attribute (%1$d) exceeds declared grid row count (%2$d)",
                                        row, declaredRowCount), null);
                    }
                }
            }
        }
    }
}
