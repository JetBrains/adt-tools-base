/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.TypeDescriptor;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.ConstructorInvocation;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Node;

/**
 * Checks for errors related to Date Formats
 */
public class DateFormatDetector extends Detector implements JavaScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            DateFormatDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Constructing SimpleDateFormat without an explicit locale */
    public static final Issue DATE_FORMAT = Issue.create(
            "SimpleDateFormat", //$NON-NLS-1$
            "Implied locale in date format",

            "Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or " +
            "`getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable " +
            "for the user's locale. The main reason you'd create an instance this class " +
            "directly is because you need to format/parse a specific machine-readable format, " +
            "in which case you almost certainly want to explicitly ask for US to ensure that " +
            "you get ASCII digits (rather than, say, Arabic digits).\n" +
            "\n" +
            "Therefore, you should either use the form of the SimpleDateFormat constructor " +
            "where you pass in an explicit locale, such as Locale.US, or use one of the " +
            "get instance methods, or suppress this error if really know what you are doing.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo(
            "http://developer.android.com/reference/java/text/SimpleDateFormat.html"); //$NON-NLS-1$

    public static final String LOCALE_CLS = "java.util.Locale";                       //$NON-NLS-1$
    public static final String SIMPLE_DATE_FORMAT_CLS = "java.text.SimpleDateFormat"; //$NON-NLS-1$

    /** Constructs a new {@link DateFormatDetector} */
    public DateFormatDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(ConstructorInvocation.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new ConstructorVisitor(context);
    }

    private static class ConstructorVisitor extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public ConstructorVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitConstructorInvocation(ConstructorInvocation node) {
            ResolvedMethod constructor = findDateFormatConstructor(node);
            if (constructor != null && !specifiesLocale(constructor)) {
                Location location = mContext.getLocation(node);
                String message =
                    "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, " +
                    "or `getTimeInstance()`, or use `new SimpleDateFormat(String template, " +
                    "Locale locale)` with for example `Locale.US` for ASCII dates.";
                mContext.report(DATE_FORMAT, node, location, message);
            }

            return super.visitConstructorInvocation(node);
        }

        private static boolean specifiesLocale(@NonNull ResolvedMethod method) {
            for (int i = 0, n = method.getArgumentCount(); i < n; i++) {
                TypeDescriptor argumentType = method.getArgumentType(i);
                if (argumentType.matchesSignature(LOCALE_CLS)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        private ResolvedMethod findDateFormatConstructor(@NonNull ConstructorInvocation node) {
            String type = node.astTypeReference().astParts().last().astIdentifier().astValue();
            if (type.endsWith("SimpleDateFormat")) {
                ResolvedNode resolved = mContext.resolve(node);
                if (resolved instanceof ResolvedMethod) {
                    ResolvedMethod method = (ResolvedMethod) resolved;
                    if (method.getContainingClass().matches(SIMPLE_DATE_FORMAT_CLS)) {
                        return method;
                    }
                }
            }

            return null;
        }
    }
}
