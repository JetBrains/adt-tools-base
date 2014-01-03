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

import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_DRAW;
import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_LAYOUT;
import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_MEASURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for cases where the wrong call is being made
 */
public class WrongCallDetector extends Detector implements ClassScanner {
    /** Calling the wrong method */
    public static final Issue ISSUE = Issue.create(
            "WrongCall", //$NON-NLS-1$
            "Using wrong draw/layout method",
            "Finds cases where the wrong call is made, such as calling `onMeasure` " +
            "instead of `measure`",

            "Custom views typically need to call `measure()` on their children, not `onMeasure`. " +
            "Ditto for onDraw, onLayout, etc.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    WrongCallDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    /** Constructs a new {@link WrongCallDetector} */
    public WrongCallDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return Arrays.asList(
                ON_DRAW,
                ON_MEASURE,
                ON_LAYOUT
        );
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        String name = call.name;
        // Call is only allowed if it is both only called on the super class (invoke special)
        // as well as within the same overriding method (e.g. you can't call super.onLayout
        // from the onMeasure method)
        if (call.getOpcode() != Opcodes.INVOKESPECIAL || !name.equals(method.name)) {
            String suggestion = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            String message = String.format(
                  "Suspicious method call; should probably call \"%1$s\" rather than \"%2$s\"",
                  suggestion, name);
            context.report(ISSUE, method, call, context.getLocation(call), message, null);
        }
    }
}
