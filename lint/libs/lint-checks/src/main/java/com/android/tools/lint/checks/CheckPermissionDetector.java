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
import com.android.tools.lint.detector.api.*;

import lombok.ast.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ensures that calls to check permission use the result (otherwise they probably meant to call the
 * <b>enforce</b> permission methods instead)
 */
public class CheckPermissionDetector extends Detector implements Detector.JavaScanner {
    /** Main issue checked by this detector */
    public static final Issue ISSUE = Issue.create("UseCheckPermission", //$NON-NLS-1$
        "Using the result of check permission calls",
        "Ensures that the return value of check permission calls are used",

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
            if (CHECK_PERMISSION.equals(check)) {
                if (!ensureContextMethod(context, node)) {
                    return;
                }
            }
            assert check.startsWith("check") : check;
            String enforce = "enforce" + check.substring("check".length());
            context.report(ISSUE, node, context.getLocation(node),
                    String.format(
                            "The result of %1$s is not used; did you mean to call %2$s?",
                            check, enforce), null);
        }
    }

    private static boolean ensureContextMethod(
            @NonNull JavaContext context,
            @NonNull MethodInvocation node) {
        // Method name used in many other contexts where it doesn't have the
        // same semantics; only use this one if we can resolve types
        // and we're certain this is the Context method
        Node resolved = context.parser.resolve(context, node);
        if (resolved instanceof MethodDeclaration) {
            ClassDeclaration declaration = JavaContext.findSurroundingClass(resolved);
            if (declaration != null && declaration.astName() != null) {
                String className = declaration.astName().astValue();
                if ("ContextWrapper".equals(className) || "Context".equals(className)) {
                    return true;
                }
            }
        }
        return false;
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
