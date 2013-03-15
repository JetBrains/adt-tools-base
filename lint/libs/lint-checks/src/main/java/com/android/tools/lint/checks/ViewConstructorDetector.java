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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.List;

/**
 * Looks for custom views that do not define the view constructors needed by UI builders
 */
public class ViewConstructorDetector extends Detector implements Detector.ClassScanner {
    private static final String SIG1 =
            "(Landroid/content/Context;)V"; //$NON-NLS-1$
    private static final String SIG2 =
            "(Landroid/content/Context;Landroid/util/AttributeSet;)V"; //$NON-NLS-1$
    private static final String SIG3 =
            "(Landroid/content/Context;Landroid/util/AttributeSet;I)V"; //$NON-NLS-1$

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ViewConstructor", //$NON-NLS-1$
            "Missing View constructors for XML inflation",
            "Checks that custom views define the expected constructors",

            "Some layout tools (such as the Android layout editor for Eclipse) needs to " +
            "find a constructor with one of the following signatures:\n" +
            "* `View(Context context)`\n" +
            "* `View(Context context, AttributeSet attrs)`\n" +
            "* `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does not apply when " +
            "used in a layout editor, you can surround the given code with a check to " +
            "see if `View#isInEditMode()` is false, since that method will return `false` " +
            "at runtime but true within a user interface editor.",

            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    /** Constructs a new {@link ViewConstructorDetector} check */
    public ViewConstructorDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (classNode.name.indexOf('$') != -1
            && (classNode.access & Opcodes.ACC_STATIC) == 0) {
            // Ignore inner classes that aren't static: we can't create these
            // anyway since we'd need the outer instance
            return;
        }

        // Ignore abstract classes
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        if (isViewClass(context, classNode)) {
            checkConstructors(context, classNode);
        }
    }

    private static boolean isViewClass(ClassContext context, ClassNode node) {
        String superName = node.superName;
        while (superName != null) {
            if (superName.equals("android/view/View")                //$NON-NLS-1$
                    || superName.equals("android/view/ViewGroup")    //$NON-NLS-1$
                    || superName.startsWith("android/widget/")       //$NON-NLS-1$
                    && !((superName.endsWith("Adapter")              //$NON-NLS-1$
                            || superName.endsWith("Controller")      //$NON-NLS-1$
                            || superName.endsWith("Service")         //$NON-NLS-1$
                            || superName.endsWith("Provider")        //$NON-NLS-1$
                            || superName.endsWith("Filter")))) {     //$NON-NLS-1$
                return true;
            }

            superName = context.getDriver().getSuperClass(superName);
        }

        return false;
    }

    private static void checkConstructors(ClassContext context, ClassNode classNode) {
        // Look through constructors
        @SuppressWarnings("rawtypes")
        List methods = classNode.methods;
        for (Object methodObject : methods) {
            MethodNode method = (MethodNode) methodObject;
            if (method.name.equals(CONSTRUCTOR_NAME)) {
                String desc = method.desc;
                if (desc.equals(SIG1) || desc.equals(SIG2) || desc.equals(SIG3)) {
                    return;
                }
            }
        }

        // If we get this far, none of the expected constructors were found.

        // Use location of one of the constructors?
        String message = String.format(
                "Custom view %1$s is missing constructor used by tools: " +
                "(Context) or (Context,AttributeSet) or (Context,AttributeSet,int)",
                classNode.name);
        File sourceFile = context.getSourceFile();
        Location location = Location.create(sourceFile != null
                ? sourceFile : context.file);
        context.report(ISSUE, location, message, null /*data*/);
    }
}
