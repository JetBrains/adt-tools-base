/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.util.Arrays;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.MethodInvocation;

public class UnsafeNativeCodeDetector extends Detector
        implements Detector.JavaScanner {

    private static final Implementation IMPLEMENTATION_JAVA = new Implementation(
            UnsafeNativeCodeDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue LOAD = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "`load` used to dynamically load code",
            "Dynamically loading code from locations other than the application's library " +
            "directory or the Android platform's built-in library directories is dangerous, " +
            "as there is an increased risk that the code could have been tampered with. " +
            "Applications should use `loadLibrary` when possible, which provides increased " +
            "assurance that libraries are loaded from one of these safer locations. " +
            "Application developers should use the features of their development " +
            "environment to place application native libraries into the lib directory " +
            "of their compiled APKs.",
            Category.SECURITY,
            4,
            Severity.WARNING,
            IMPLEMENTATION_JAVA);

    private static final String RUNTIME_CLASS = "java.lang.Runtime"; //$NON-NLS-1$
    private static final String SYSTEM_CLASS = "java.lang.System"; //$NON-NLS-1$

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        // Identify calls to Runtime.load() and System.load()
        return Arrays.asList("load");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        ResolvedNode resolved = context.resolve(node);
        if (resolved instanceof ResolvedMethod) {
            String methodName = node.astName().astValue();
            ResolvedClass resolvedClass = ((ResolvedMethod) resolved).getContainingClass();
            if ((resolvedClass.isSubclassOf(RUNTIME_CLASS, false)) ||
                    (resolvedClass.matches(SYSTEM_CLASS))) {
                // Report calls to Runtime.load() and System.load()
                if ("load".equals(methodName)) {
                    context.report(LOAD, node, context.getLocation(node),
                            "Dynamically loading code using `load` is risky, please use " +
                                    "`loadLibrary` instead when possible");
                    return;
                }
            }
        }
    }
}
