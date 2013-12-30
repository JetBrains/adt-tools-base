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

import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.SdkConstants.FRAGMENT;
import static com.android.SdkConstants.FRAGMENT_V4;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Checks that Fragment subclasses can be instantiated via
 * {link {@link Class#newInstance()}}: the class is public, static, and has
 * a public null constructor.
 * <p>
 * This helps track down issues like
 *   http://stackoverflow.com/questions/8058809/fragment-activity-crashes-on-screen-rotate
 * (and countless duplicates)
 */
public class FragmentDetector extends Detector implements ClassScanner {
    private static final String FRAGMENT_NAME_SUFFIX = "Fragment";              //$NON-NLS-1$

    /** Are fragment subclasses instantiatable? */
    public static final Issue ISSUE = Issue.create(
        "ValidFragment", //$NON-NLS-1$
        "Fragment not instantiatable",
        "Ensures that `Fragment` subclasses can be instantiated",

        "From the Fragment documentation:\n" +
        "*Every* fragment must have an empty constructor, so it can be instantiated when " +
        "restoring its activity's state. It is strongly recommended that subclasses do not " +
        "have other constructors with parameters, since these constructors will not be " +
        "called when the fragment is re-instantiated; instead, arguments can be supplied " +
        "by the caller with `setArguments(Bundle)` and later retrieved by the Fragment " +
        "with `getArguments()`.",

        Category.CORRECTNESS,
        6,
        Severity.FATAL,
        new Implementation(
                FragmentDetector.class,
                Scope.CLASS_FILE_SCOPE)
        ).addMoreInfo(
            "http://developer.android.com/reference/android/app/Fragment.html#Fragment()"); //$NON-NLS-1$


    /** Constructs a new {@link FragmentDetector} */
    public FragmentDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            // Ignore abstract classes since they are clearly (and by definition) not intended to
            // be instantiated. We're looking for accidental non-static or missing constructor
            // scenarios here.
            return;
        }

        LintDriver driver = context.getDriver();

        if (!(driver.isSubclassOf(classNode, FRAGMENT)
                || driver.isSubclassOf(classNode, FRAGMENT_V4))) {
            return;
        }

        if ((classNode.access & Opcodes.ACC_PUBLIC) == 0) {
            context.report(ISSUE, context.getLocation(classNode), String.format(
                    "This fragment class should be public (%1$s)",
                        ClassContext.createSignature(classNode.name, null, null)),
                    null);
            return;
        }

        if (classNode.name.indexOf('$') != -1 && !LintUtils.isStaticInnerClass(classNode)) {
            context.report(ISSUE, context.getLocation(classNode), String.format(
                    "This fragment inner class should be static (%1$s)",
                        ClassContext.createSignature(classNode.name, null, null)),
                    null);
            return;
        }

        boolean hasDefaultConstructor = false;
        @SuppressWarnings("rawtypes") // ASM API
        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            if (method.name.equals(CONSTRUCTOR_NAME)) {
                if (method.desc.equals("()V")) { //$NON-NLS-1$
                    // The constructor must be public
                    if ((method.access & Opcodes.ACC_PUBLIC) != 0) {
                        hasDefaultConstructor = true;
                    } else {
                        context.report(ISSUE, context.getLocation(method, classNode),
                                "The default constructor must be public",
                                null);
                        // Also mark that we have a constructor so we don't complain again
                        // below since we've already emitted a more specific error related
                        // to the default constructor
                        hasDefaultConstructor = true;
                    }
                } else if (!method.desc.contains("()")) { //$NON-NLS-1$
                    context.report(ISSUE, context.getLocation(method, classNode),
                            // TODO: Use separate issue for this which isn't an error
                        "Avoid non-default constructors in fragments: use a default constructor " +
                        "plus Fragment#setArguments(Bundle) instead",
                        null);
                }
            }
        }

        if (!hasDefaultConstructor) {
            context.report(ISSUE, context.getLocation(classNode), String.format(
                    "This fragment should provide a default constructor (a public " +
                    "constructor with no arguments) (%1$s)",
                        ClassContext.createSignature(classNode.name, null, null)),
                    null);
        }
    }
}
