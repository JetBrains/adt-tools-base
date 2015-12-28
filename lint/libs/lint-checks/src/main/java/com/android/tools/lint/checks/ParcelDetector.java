/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import static com.android.tools.lint.client.api.JavaParser.ResolvedField;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.List;

import lombok.ast.ClassDeclaration;
import lombok.ast.Node;

/**
 * Looks for Parcelable classes that are missing a CREATOR field
 */
public class ParcelDetector extends Detector implements Detector.JavaScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator", //$NON-NLS-1$
            "Missing Parcelable `CREATOR` field",

            "According to the `Parcelable` interface documentation, " +
            "\"Classes implementing the Parcelable interface must also have a " +
            "static field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.\"",

            Category.CORRECTNESS,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("http://developer.android.com/reference/android/os/Parcelable.html");

    /** Constructs a new {@link com.android.tools.lint.checks.ParcelDetector} check */
    public ParcelDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @Nullable ClassDeclaration node,
            @NonNull Node declarationOrAnonymous, @NonNull ResolvedClass cls) {
        if (node == null) {
            // Anonymous classes aren't parcelable
            return;
        }
        // Only applies to concrete classes
        int flags = node.astModifiers().getExplicitModifierFlags();
        if ((flags & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0) {
            return;
        }

        // Parceling spans is handled in TextUtils#CHAR_SEQUENCE_CREATOR
        if (!cls.isImplementing("android.text.ParcelableSpan", false)) {
            ResolvedField field = cls.getField("CREATOR", false);
            if (field == null) {
                Location location = context.getLocation(node.astName());
                context.report(ISSUE, node, location,
                        "This class implements `Parcelable` but does not "
                                + "provide a `CREATOR` field");
            }
        }
    }
}
