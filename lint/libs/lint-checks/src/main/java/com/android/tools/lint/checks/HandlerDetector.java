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

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import java.util.Collections;
import java.util.List;

/**
 * Checks that Handler implementations are top level classes or static.
 * See the corresponding check in the android.os.Handler source code.
 */
public class HandlerDetector extends Detector implements JavaPsiScanner {

    /** Potentially leaking handlers */
    public static final Issue ISSUE = Issue.create(
            "HandlerLeak", //$NON-NLS-1$
            "Handler reference leaks",

            "Since this Handler is declared as an inner class, it may prevent the outer " +
            "class from being garbage collected. If the Handler is using a Looper or " +
            "MessageQueue for a thread other than the main thread, then there is no issue. " +
            "If the Handler is using the Looper or MessageQueue of the main thread, you " +
            "need to fix your Handler declaration, as follows: Declare the Handler as a " +
            "static class; In the outer class, instantiate a WeakReference to the outer " +
            "class and pass this object to your Handler when you instantiate the Handler; " +
            "Make all references to members of the outer class using the WeakReference object.",

            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(
                    HandlerDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String LOOPER_CLS = "android.os.Looper";
    private static final String HANDLER_CLS = "android.os.Handler";

    /** Constructs a new {@link HandlerDetector} */
    public HandlerDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HANDLER_CLS);
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass declaration) {
        // Only consider static inner classes
        if (context.getEvaluator().isStatic(declaration)) {
            return;
        }
        boolean isAnonymous = declaration instanceof PsiAnonymousClass;
        if (declaration.getContainingClass() == null && !isAnonymous) {
            return;
        }

        //// Only flag handlers using the default looper
        //noinspection unchecked
        PsiNewExpression invocation = getParentOfType(declaration, PsiNewExpression.class,
                true, PsiMethod.class);
        if (invocation != null) {
            PsiExpressionList argumentList = invocation.getArgumentList();
            if (argumentList != null && isAnonymous) {
                ((PsiAnonymousClass)declaration).getArgumentList();
                for (PsiExpression expression : argumentList.getExpressions()) {
                    PsiType type = expression.getType();
                    if (type instanceof PsiClassType
                            && LOOPER_CLS.equals(type.getCanonicalText())) {
                        return;
                    }
                }
            }
        } else if (hasLooperConstructorParameter(declaration)) {
            // This is an inner class which takes a Looper parameter:
            // possibly used correctly from elsewhere
            return;
        }

        PsiElement locationNode = JavaContext.findNameElement(declaration);
        if (locationNode == null) {
            locationNode = declaration;
        }
        Location location = context.getLocation(locationNode);
        String name;
        if (isAnonymous) {
            name = "anonymous " + ((PsiAnonymousClass)declaration).getBaseClassReference().getQualifiedName();
        } else {
            name = declaration.getQualifiedName();
        }

        //noinspection VariableNotUsedInsideIf
        context.report(ISSUE, locationNode, location, String.format(
                "This Handler class should be static or leaks might occur (%1$s)",
                name));
    }

    private static boolean hasLooperConstructorParameter(@NonNull PsiClass cls) {
        for (PsiMethod constructor : cls.getConstructors()) {
            for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                PsiType type = parameter.getType();
                if (LOOPER_CLS.equals(type.getCanonicalText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
