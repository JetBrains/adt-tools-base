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
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.StringLiteral;

/**
 * Checks for errors related to TextView#setText and internationalization
 */
public class SetTextDetector extends Detector implements JavaScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            SetTextDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Constructing SimpleDateFormat without an explicit locale */
    public static final Issue SET_TEXT_I18N = Issue.create(
            "SetTextI18n", //$NON-NLS-1$
            "TextView Internationalization",

            "When calling `TextView#setText`\n"  +
            "* Never call `Number#toString()` to format numbers; it will not handle fraction " +
            "separators and locale-specific digits properly. Consider using `String#format` " +
            "with proper format specifications (`%d` or `%f`) instead.\n" +
            "* Do not pass a string literal (e.g. \"Hello\") to display text. Hardcoded " +
            "text can not be properly translated to other languages. Consider using Android " +
            "resource strings instead.\n" +
            "* Do not build messages by concatenating text chunks. Such messages can not be " +
            "properly translated.",

            Category.I18N,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo("http://developer.android.com/guide/topics/resources/localization.html");


    private static final String METHOD_NAME = "setText";
    private static final String TO_STRING_NAME = "toString";
    private static final String CHAR_SEQUENCE_CLS = "java.lang.CharSequence";
    private static final String NUMBER_CLS = "java.lang.Number";
    private static final String TEXT_VIEW_CLS = "android.widget.TextView";

    // Pattern to match string literal that require translation. These are those having word
    // characters in it.
    private static final String WORD_PATTERN = ".*\\w{2,}.*";

    /** Constructs a new {@link SetTextDetector} */
    public SetTextDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(METHOD_NAME);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation call) {
        ResolvedMethod method = (ResolvedMethod) context.resolve(call);
        if (method != null && method.getContainingClass().matches(TEXT_VIEW_CLS)
                && method.matches(METHOD_NAME)
                && method.getArgumentCount() > 0
                && method.getArgumentType(0).matchesSignature(CHAR_SEQUENCE_CLS)) {
            checkNode(context, call.astArguments().first());
        }
    }

    private static void checkNode(JavaContext context, Node node) {
        if (node instanceof StringLiteral) {
            if (((StringLiteral) node).astValue().matches(WORD_PATTERN)) {
                context.report(SET_TEXT_I18N, node, context.getLocation(node),
                        "String literal in `setText` can not be translated. Use Android "
                                + "resources instead.");
            }
        } else if (node instanceof MethodInvocation) {
            ResolvedMethod rm = (ResolvedMethod) context.resolve(node);
            if (rm != null && rm.getName().matches(TO_STRING_NAME)) {
                ResolvedClass superClass = rm.getContainingClass().getSuperClass();
                if (superClass != null && superClass.matches(NUMBER_CLS)) {
                    context.report(SET_TEXT_I18N, node, context.getLocation(node),
                            "Number formatting does not take into account locale settings. " +
                                    "Consider using `String.format` instead.");
                }
            }
        } else if (node instanceof BinaryExpression) {
            BinaryExpression expression = (BinaryExpression) node;
            if (expression.astOperator() == BinaryOperator.PLUS) {
                context.report(SET_TEXT_I18N, node, context.getLocation(node),
                    "Do not concatenate text displayed with `setText`. "
                            + "Use resource string with placeholders.");
            }
            checkNode(context, expression.astLeft());
            checkNode(context, expression.astRight());
        }
    }
}
