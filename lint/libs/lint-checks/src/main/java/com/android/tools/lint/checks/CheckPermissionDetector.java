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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Arrays;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.ExpressionStatement;
import lombok.ast.MethodInvocation;

/**
 * Ensures that calls to check permission use the result (otherwise they probably meant to call the
 * <b>enforce</b> permission methods instead)
 */
public class CheckPermissionDetector extends Detector implements Detector.JavaScanner {
    /** Main issue checked by this detector */
    public static final Issue ISSUE = Issue.create("UseCheckPermission", //$NON-NLS-1$
        "Using the result of check permission calls",

        "You normally want to use the result of checking a permission; these methods " +
        "return whether the permission is held; they do not throw an error if the permission " +
        "is not granted. Code which does not do anything with the return value probably " +
        "meant to be calling the enforce methods instead, e.g. rather than " +
        "`Context#checkCallingPermission` it should call `Context#enforceCallingPermission`.",

        Category.SECURITY, 6, Severity.WARNING,
        new Implementation(CheckPermissionDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String CHECK_PERMISSION = "checkPermission";

    /**
     * Constructs a new {@link com.android.tools.lint.checks.CheckPermissionDetector} check
     */
    public CheckPermissionDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        if (node.getParent() instanceof ExpressionStatement) {
            String check = node.astName().astValue();
            if (CHECK_PERMISSION.equals(check) && !context.isContextMethod(node)) {
                return;
            }
            assert check.startsWith("check") : check;
            String enforce = "enforce" + check.substring("check".length());
            context.report(ISSUE, node, context.getLocation(node),
                    String.format(
                            "The result of `%1$s` is not used; did you mean to call `%2$s`?",
                            check, enforce));
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                CHECK_PERMISSION,
                "checkUriPermission",
                "checkCallingOrSelfPermission",
                "checkCallingPermission",
                "checkCallingUriPermission",
                "checkCallingOrSelfUriPermission"
        );
    }
}
