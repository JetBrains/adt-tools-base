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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Collections;
import java.util.List;

import lombok.ast.Assert;
import lombok.ast.AstVisitor;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Node;

/**
 * Looks for assertion usages.
 */
public class AssertDetector extends Detector implements Detector.JavaScanner {
    /** Using assertions */
    public static final Issue ISSUE = Issue.create(
            "Assert", //$NON-NLS-1$
            "Assertions",
            "Looks for usages of the assert keyword",

            "Assertions are not checked at runtime. There are ways to request that they be used " +
            "by Dalvik (`adb shell setprop debug.assert 1`), but the property is ignored in " +
            "many places and can not be relied upon. Instead, perform conditional checking " +
            "inside `if (BuildConfig.DEBUG) { }` blocks. That constant is a static final boolean " +
            "which is true in debug builds and false in release builds, and the Java compiler " +
            "completely removes all code inside the if-body from the app.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AssertDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo(
            "https://code.google.com/p/android/issues/detail?id=65183"); //$NON-NLS-1$

    /** Constructs a new {@link com.android.tools.lint.checks.AssertDetector} check */
    public AssertDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(Assert.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
        return new ForwardingAstVisitor() {
            @Override
            public boolean visitAssert(Assert node) {
                String message = "Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead.";
                context.report(ISSUE, node, context.getLocation(node), message, null);
                return false;
            }
        };
    }
}
