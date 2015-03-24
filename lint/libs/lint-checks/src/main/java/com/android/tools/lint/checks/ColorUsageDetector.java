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

import static com.android.SdkConstants.RESOURCE_CLZ_COLOR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.io.File;

import lombok.ast.AstVisitor;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.Select;

/**
 * Looks for cases where the code attempts to set a resource id, rather than
 * a resolved color, as the RGB int.
 */
public class ColorUsageDetector extends Detector implements Detector.JavaScanner {
    /** Attempting to set a resource id as a color */
    public static final Issue ISSUE = Issue.create(
            "ResourceAsColor", //$NON-NLS-1$
            "Should pass resolved color instead of resource id",

            "Methods that take a color in the form of an integer should be passed " +
            "an RGB triple, not the actual color resource id. You must call " +
            "`getResources().getColor(resource)` to resolve the actual color value first.",

            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(
                    ColorUsageDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    /** Constructs a new {@link ColorUsageDetector} check */
    public ColorUsageDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements JavaScanner ----

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull Node select, @NonNull String type, @NonNull String name, boolean isFramework) {
        if (type.equals(RESOURCE_CLZ_COLOR)) {
            while (select.getParent() instanceof Select) {
                select = select.getParent();
            }

            Node current = select.getParent();
            while (current != null) {
                if (current.getClass() == MethodInvocation.class) {
                    MethodInvocation call = (MethodInvocation) current;
                    String methodName = call.astName().astValue();
                    if (methodName.endsWith("Color")              //$NON-NLS-1$
                            && methodName.startsWith("set")) {    //$NON-NLS-1$
                        if ("setProgressBackgroundColor".equals(methodName)) {
                            // Special exception: SwipeRefreshLayout does not follow the normal
                            // naming convention: its setProgressBackgroundColor does *not* take
                            // an ARGB color integer, it takes a resource id.
                            // This method name is unique across the framework and support
                            // libraries.
                            return;
                        }
                        context.report(
                                ISSUE, select, context.getLocation(select), String.format(
                                    "Should pass resolved color instead of resource id here: " +
                                    "`getResources().getColor(%1$s)`", select.toString()));
                    }
                    break;
                } else if (current.getClass() == MethodDeclaration.class) {
                    break;
                }
                current = current.getParent();
            }
        }
    }
}
