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

import static com.android.SdkConstants.FORMAT_METHOD;
import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;

/**
 * Checks for errors related to locale handling
 */
public class LocaleDetector extends Detector implements JavaScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            LocaleDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Calling risky convenience methods */
    public static final Issue STRING_LOCALE = Issue.create(
            "DefaultLocale", //$NON-NLS-1$
            "Implied default locale in case conversion",

            "Calling `String#toLowerCase()` or `#toUpperCase()` *without specifying an " +
            "explicit locale* is a common source of bugs. The reason for that is that those " +
            "methods will use the current locale on the user's device, and even though the " +
            "code appears to work correctly when you are developing the app, it will fail " +
            "in some locales. For example, in the Turkish locale, the uppercase replacement " +
            "for `i` is *not* `I`.\n" +
            "\n" +
            "If you want the methods to just perform ASCII replacement, for example to convert " +
            "an enum name, call `String#toUpperCase(Locale.US)` instead. If you really want to " +
            "use the current locale, call `String#toUpperCase(Locale.getDefault())` instead.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo(
            "http://developer.android.com/reference/java/util/Locale.html#default_locale"); //$NON-NLS-1$

    /** Constructs a new {@link LocaleDetector} */
    public LocaleDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        if (LintClient.isStudio()) {
            // In the IDE, don't flag toUpperCase/toLowerCase; these
            // are already flagged by built-in IDE inspections, so we don't
            // want duplicate warnings.
            return Collections.singletonList(FORMAT_METHOD);
        } else {
            return Arrays.asList(
                    // Only when not running in the IDE
                    "toLowerCase", //$NON-NLS-1$
                    "toUpperCase", //$NON-NLS-1$
                    FORMAT_METHOD
            );
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation call) {
        ResolvedNode resolved = context.resolve(call);
        if (resolved instanceof ResolvedMethod) {
            ResolvedMethod method = (ResolvedMethod) resolved;
            if (method.getContainingClass().matches(TYPE_STRING)) {
                String name = method.getName();
                if (name.equals(FORMAT_METHOD)) {
                    checkFormat(context, method, call);
                } else if (method.getArgumentCount() == 0) {
                    Location location = context.getNameLocation(call);
                    String message = String.format(
                            "Implicitly using the default locale is a common source of bugs: " +
                                    "Use `%1$s(Locale)` instead", name);
                    context.report(STRING_LOCALE, call, location, message);
                }
            }
        }
    }

    /** Returns true if the given node is a parameter to a Logging call */
    private static boolean isLoggingParameter(
            @NonNull JavaContext context,
            @NonNull MethodInvocation node) {
        Node parent = node.getParent();
        if (parent instanceof MethodInvocation) {
            MethodInvocation call = (MethodInvocation)parent;
            String name = call.astName().astValue();
            if (name.length() == 1) { // "d", "i", "e" etc in Log
                ResolvedNode resolved = context.resolve(call);
                if (resolved instanceof ResolvedMethod) {
                    ResolvedMethod method = (ResolvedMethod) resolved;
                    if (method.getContainingClass().matches(LogDetector.LOG_CLS)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void checkFormat(
            @NonNull JavaContext context,
            @NonNull ResolvedMethod method,
            @NonNull MethodInvocation call) {
        // Only check the non-locale version of String.format
        if (method.getArgumentCount() == 0
                || !method.getArgumentType(0).matchesName(TYPE_STRING)
                || call.astArguments().isEmpty()) {
            return;
        }

        // Find the formatting string
        Expression first = call.astArguments().first();
        Object value = ConstantEvaluator.evaluate(context, first);
        if (!(value instanceof String)) {
            return;
        }

        String format = (String) value;
        if (StringFormatDetector.isLocaleSpecific(format)) {
            if (isLoggingParameter(context, call)) {
                return;
            }
            Location location = context.getLocation(call);
            String message =
                    "Implicitly using the default locale is a common source of bugs: " +
                            "Use `String.format(Locale, ...)` instead";
            context.report(STRING_LOCALE, call, location, message);
        }
    }
}
