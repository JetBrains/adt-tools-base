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

import static com.android.SdkConstants.CLASS_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.ast.AstVisitor;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.Super;

/**
 * Makes sure that methods call super when overriding methods.
 * <p>
 * TODO: We should drive this off of annotation metadata!
 */
public class CallSuperDetector extends Detector implements Detector.JavaScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            CallSuperDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Missing call to super */
    public static final Issue ISSUE = Issue.create(
            "MissingSuperCall", //$NON-NLS-1$
            "Missing Super Call",

            "Some methods, such as `View#onDetachedFromWindow`, require that you also " +
            "call the super implementation as part of your method.",

            Category.CORRECTNESS,
            9,
            Severity.WARNING,
            IMPLEMENTATION);

    static final String ON_DETACHED_FROM_WINDOW = "onDetachedFromWindow";   //$NON-NLS-1$
    static final String ON_VISIBILITY_CHANGED = "onVisibilityChanged";      //$NON-NLS-1$

    /** Constructs a new {@link CallSuperDetector} check */
    public CallSuperDetector() {
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
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(MethodDeclaration.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new PerformanceVisitor(context);
    }

    private static class PerformanceVisitor extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public PerformanceVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitMethodDeclaration(MethodDeclaration node) {
            // TODO: Check methods in Activity that require super as well
            String methodName = node.astMethodName().astValue();
            if (methodName.equals(ON_DETACHED_FROM_WINDOW) &&
                    node.astParameters() != null && node.astParameters().isEmpty()) {
                if (!callsSuper(node, ON_DETACHED_FROM_WINDOW)) {
                    // Make sure the current class extends View, if type information is
                    // available
                    boolean isView = true; // Don't know without type information
                    ResolvedNode resolved = mContext.resolve(node);
                    if (resolved instanceof ResolvedMethod) {
                        ResolvedMethod method = (ResolvedMethod) resolved;
                        isView = method.getContainingClass().isSubclassOf(CLASS_VIEW, false);
                    }
                    if (isView) {
                        report(node, ON_DETACHED_FROM_WINDOW);
                    }
                }
            }

            // Implementations of WatchFaceServices must call super.onVisibilityChanged
            // if overriding that method!
            if (methodName.equals(ON_VISIBILITY_CHANGED) &&
                    node.astParameters() != null && node.astParameters().size() == 1 &&
                    node.astParameters().first().astTypeReference() != null &&
                    node.astParameters().first().astTypeReference().isBoolean()) {
                if (!callsSuper(node, ON_VISIBILITY_CHANGED)) {
                    ResolvedNode resolved = mContext.resolve(node);
                    if (resolved instanceof ResolvedMethod) {
                        ResolvedMethod method = (ResolvedMethod) resolved;
                        //noinspection SpellCheckingInspection
                        if (method.getContainingClass().isSubclassOf(
                                "android.support.wearable.watchface.WatchFaceService.Engine",
                                false)) {
                            report(node, ON_VISIBILITY_CHANGED);
                        }
                    }
                }
            }

            return super.visitMethodDeclaration(node);
        }

        private void report(MethodDeclaration node, String methodName) {
            String message = "Overriding method should call `super."
                    + methodName + "`";
            Location location = mContext.getLocation(node.astMethodName());
            mContext.report(ISSUE, node, location, message);
        }

        private boolean callsSuper(MethodDeclaration node, final String methodName) {
            final AtomicBoolean result = new AtomicBoolean();
            node.accept(new ForwardingAstVisitor() {
                @Override
                public boolean visitMethodInvocation(MethodInvocation node) {
                    if (node.astName().astValue().equals(methodName) &&
                            node.astOperand() instanceof Super) {
                        result.set(true);
                    }
                    return super.visitMethodInvocation(node);
                }
            });

            return result.get();
        }
    }
}
