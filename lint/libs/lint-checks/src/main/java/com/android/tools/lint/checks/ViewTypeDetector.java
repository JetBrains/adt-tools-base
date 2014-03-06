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

import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Joiner;

import org.w3c.dom.Attr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.ast.AstVisitor;
import lombok.ast.Cast;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.Select;
import lombok.ast.StrictListAccessor;

/** Detector for finding inconsistent usage of views and casts */
public class ViewTypeDetector extends ResourceXmlDetector implements Detector.JavaScanner {
    /** Mismatched view types */
    public static final Issue ISSUE = Issue.create(
            "WrongViewCast", //$NON-NLS-1$
            "Mismatched view type",
            "Looks for incorrect casts to views that according to the XML are of a different type",
            "Keeps track of the view types associated with ids and if it finds a usage of " +
            "the id in the Java code it ensures that it is treated as the same type.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            new Implementation(
                    ViewTypeDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    private final Map<String, Object> mIdToViewTag = new HashMap<String, Object>(50);

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        if (LintUtils.endsWith(file.getName(), DOT_JAVA)) {
            return true;
        }

        return super.appliesTo(context, file);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String view = attribute.getOwnerElement().getTagName();
        String value = attribute.getValue();
        String id = null;
        if (value.startsWith(ID_PREFIX)) {
            id = value.substring(ID_PREFIX.length());
        } else if (value.startsWith(NEW_ID_PREFIX)) {
            id = value.substring(NEW_ID_PREFIX.length());
        } // else: could be @android id

        if (id != null) {
            if (view.equals(VIEW_TAG)) {
                view = attribute.getOwnerElement().getAttribute(ATTR_CLASS);
            }

            Object existing = mIdToViewTag.get(id);
            if (existing == null) {
                mIdToViewTag.put(id, view);
            } else if (existing instanceof String) {
                String existingString = (String) existing;
                if (!existingString.equals(view)) {
                    // Convert to list
                    List<String> list = new ArrayList<String>(2);
                    list.add((String) existing);
                    list.add(view);
                    mIdToViewTag.put(id, list);
                }
            } else if (existing instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) existing;
                if (!list.contains(view)) {
                    list.add(view);
                }
            }
        }
    }

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("findViewById"); //$NON-NLS-1$
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        assert node.astName().astValue().equals("findViewById");
        if (node.getParent() instanceof Cast) {
            Cast cast = (Cast) node.getParent();
            String castType = cast.astTypeReference().getTypeName();
            StrictListAccessor<Expression, MethodInvocation> args = node.astArguments();
            if (args.size() == 1) {
                Expression first = args.first();
                // TODO: Do flow analysis as in the StringFormatDetector in order
                // to handle variable references too
                if (first instanceof Select) {
                    String resource = first.toString();
                    if (resource.startsWith("R.id.")) { //$NON-NLS-1$
                        String id = ((Select) first).astIdentifier().astValue();
                        Object types = mIdToViewTag.get(id);
                        if (types instanceof String) {
                            String layoutType = (String) types;
                            checkCompatible(context, castType, layoutType, null, cast);
                        } else if (types instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<String> layoutTypes = (List<String>) types;
                            checkCompatible(context, castType, null, layoutTypes, cast);
                        }
                    }
                }
            }
        }
    }

    /** Check if the view and cast type are compatible */
    private static void checkCompatible(JavaContext context, String castType, String layoutType,
            List<String> layoutTypes, Cast node) {
        assert layoutType == null || layoutTypes == null; // Should only specify one or the other
        boolean compatible = true;
        if (layoutType != null) {
            if (!layoutType.equals(castType)
                    && !context.getSdkInfo().isSubViewOf(castType, layoutType)) {
                compatible = false;
            }
        } else {
            compatible = false;
            assert layoutTypes != null;
            for (String type : layoutTypes) {
                if (type.equals(castType)
                        || context.getSdkInfo().isSubViewOf(castType, type)) {
                    compatible = true;
                    break;
                }
            }
        }

        if (!compatible) {
            if (layoutType == null) {
                layoutType = Joiner.on("|").join(layoutTypes);
            }
            String message = String.format(
                    "Unexpected cast to %1$s: layout tag was %2$s",
                    castType, layoutType);
            context.report(ISSUE, node, context.parser.getLocation(context, node), message,
                    null);
        }
    }
}
