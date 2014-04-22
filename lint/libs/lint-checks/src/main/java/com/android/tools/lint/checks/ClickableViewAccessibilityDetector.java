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

import static com.android.SdkConstants.ANDROID_VIEW_VIEW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.ListIterator;

/**
 * Checks that views that override View#onTouchEvent also implement View#performClick
 * and call performClick when click detection occurs.
 */
public class ClickableViewAccessibilityDetector extends Detector implements Detector.ClassScanner {
    // TODO Extend this check to catch views that use OnTouchListener.

    public static final Issue ISSUE = Issue.create(
            "ClickableViewAccessibility", //$NON-NLS-1$
            "Accessibility in Custom Views",
            "Checks that custom views handle accessibility on click events",
            "If a `View` that overrides `onTouchEvent` does not also implement `performClick` and "
                    + "call it, the `View` may not handle accessibility actions properly.",
            Category.A11Y,
            6,
            Severity.WARNING,
            new Implementation(
                    ClickableViewAccessibilityDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String ON_TOUCH_EVENT = "onTouchEvent"; //$NON-NLS-1$
    private static final String ON_TOUCH_EVENT_SIG = "(Landroid/view/MotionEvent;)Z"; //$NON-NLS-1$
    private static final String PERFORM_CLICK = "performClick"; //$NON-NLS-1$
    private static final String PERFORM_CLICK_SIG = "()Z"; //$NON-NLS-1$


    /** Constructs a new {@link ClickableViewAccessibilityDetector} */
    public ClickableViewAccessibilityDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @SuppressWarnings("unchecked") // ASM API
    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Ignore abstract classes.
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Ignore classes that aren't Views.
        if (!context.getDriver().isSubclassOf(classNode, ANDROID_VIEW_VIEW)) {
            return;
        }

        MethodNode onTouchEvent = findMethod(classNode.methods, ON_TOUCH_EVENT, ON_TOUCH_EVENT_SIG);
        MethodNode performClick = findMethod(classNode.methods, PERFORM_CLICK, PERFORM_CLICK_SIG);

        // Check if we override onTouchEvent.
        if (onTouchEvent != null) {
            // Ensure that we also override performClick.
            //noinspection VariableNotUsedInsideIf
            if (performClick == null) {
                String message = String.format(
                        "Custom view %1$s overrides onTouchEvent but not performClick",
                        classNode.name);
                context.report(ISSUE, context.getLocation(onTouchEvent, classNode), message, null);
            } else {
                // If we override performClick, ensure that it is called inside onTouchEvent.
                AbstractInsnNode performClickInOnTouchEventInsnNode = findMethodCallInstruction(
                        onTouchEvent.instructions,
                        classNode.name,
                        PERFORM_CLICK,
                        PERFORM_CLICK_SIG);
                if (performClickInOnTouchEventInsnNode == null) {
                    String message = String.format(
                            "%1$s#onTouchEvent should call %1$s#performClick",
                            classNode.name);
                    context.report(ISSUE, context.getLocation(onTouchEvent, classNode), message,
                            null);
                }
            }
        }

        // Ensure that, if performClick is implemented, performClick calls super.performClick.
        if (performClick != null) {
            AbstractInsnNode superPerformClickInPerformClickInsnNode = findMethodCallInstruction(
                    performClick.instructions,
                    classNode.superName,
                    PERFORM_CLICK,
                    PERFORM_CLICK_SIG);
            if (superPerformClickInPerformClickInsnNode == null) {
                String message = String.format(
                        "%1$s#performClick should call super#performClick",
                        classNode.name);
                context.report(ISSUE, context.getLocation(performClick, classNode), message, null);
            }
        }
    }

    @Nullable
    private static MethodNode findMethod(
            @NonNull List<MethodNode> methods,
            @NonNull String name,
            @NonNull String desc) {
        for (MethodNode method : methods) {
            if (name.equals(method.name)
                    && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked") // ASM API
    @Nullable
    private static AbstractInsnNode findMethodCallInstruction(
            @NonNull InsnList instructions,
            @NonNull String owner,
            @NonNull String name,
            @NonNull String desc) {
        ListIterator<AbstractInsnNode> iterator = instructions.iterator();

        while (iterator.hasNext()) {
            AbstractInsnNode insnNode = iterator.next();
            if (insnNode.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if ((methodInsnNode.owner.equals(owner))
                        && (methodInsnNode.name.equals(name))
                        && (methodInsnNode.desc.equals(desc))) {
                    return methodInsnNode;
                }
            }
        }

        return null;
    }
}
