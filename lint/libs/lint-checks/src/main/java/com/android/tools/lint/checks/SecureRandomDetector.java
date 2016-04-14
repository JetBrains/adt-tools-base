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
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.TypeDescriptor;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TypeEvaluator;

import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;

/**
 * Checks for hardcoded seeds with random numbers.
 */
public class SecureRandomDetector extends Detector implements JavaScanner {
    /** Unregistered activities and services */
    public static final Issue ISSUE = Issue.create(
            "SecureRandom", //$NON-NLS-1$
            "Using a fixed seed with `SecureRandom`",

            "Specifying a fixed seed will cause the instance to return a predictable sequence " +
            "of numbers. This may be useful for testing but it is not appropriate for secure use.",

            Category.SECURITY,
            9,
            Severity.WARNING,
            new Implementation(
                    SecureRandomDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("http://developer.android.com/reference/java/security/SecureRandom.html");

    private static final String SET_SEED = "setSeed"; //$NON-NLS-1$
    public static final String JAVA_SECURITY_SECURE_RANDOM = "java.security.SecureRandom";
    public static final String JAVA_UTIL_RANDOM = "java.util.Random";

    /** Constructs a new {@link SecureRandomDetector} */
    public SecureRandomDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SET_SEED);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation call) {
        ResolvedNode resolved = context.resolve(call);
        if (!(resolved instanceof ResolvedMethod)) {
            return;
        }
        ResolvedMethod method = (ResolvedMethod) resolved;
        Expression seedArgument = call.astArguments().first();
        if (seedArgument == null) {
            return;
        }
        ResolvedClass containingClass = method.getContainingClass();
        if (containingClass.matches(JAVA_SECURITY_SECURE_RANDOM) ||
                containingClass.isSubclassOf(JAVA_UTIL_RANDOM, false)
                        && isSecureRandomReceiver(context, call)) {
            // Called with a fixed seed?
            Object seed = ConstantEvaluator.evaluate(context, seedArgument);
            //noinspection VariableNotUsedInsideIf
            if (seed != null) {
                context.report(ISSUE, call, context.getLocation(call),
                        "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                                "it is not secure. Use `getSeed()`.");
            } else {
                // Called with a simple System.currentTimeMillis() seed or something like that?
                ResolvedNode resolvedArgument = context.resolve(seedArgument);
                if (resolvedArgument instanceof ResolvedMethod) {
                    ResolvedMethod seedMethod = (ResolvedMethod) resolvedArgument;
                    String methodName = seedMethod.getName();
                    if (methodName.equals("currentTimeMillis") || methodName
                            .equals("nanoTime")) {
                        context.report(ISSUE, call, context.getLocation(call),
                                "It is dangerous to seed `SecureRandom` with the current "
                                        + "time because that value is more predictable to "
                                        + "an attacker than the default seed.");
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given invocation is assigned a SecureRandom type
     */
    private static boolean isSecureRandomReceiver(@NonNull JavaContext context,
            @NonNull MethodInvocation call) {
        Expression operand = call.astOperand();
        return operand != null && isSecureRandomType(context, operand);
    }

    /**
     * Returns true if the node evaluates to an instance of type SecureRandom
     */
    private static boolean isSecureRandomType(@NonNull JavaContext context, @NonNull Node node) {
        TypeDescriptor type = TypeEvaluator.evaluate(context, node);
        return type != null && type.matchesName(JAVA_SECURITY_SECURE_RANDOM);

    }
}
