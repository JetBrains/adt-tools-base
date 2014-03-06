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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.List;

/**
 * Looks for Parcelable classes that are missing a CREATOR field
 */
public class ParcelDetector extends Detector implements Detector.ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator", //$NON-NLS-1$
            "Missing Parcelable `CREATOR` field",
            "Checks that classes implementing `Parcelable` also provide a `CREATOR` field",

            "According to the `Parcelable` interface documentation, " +
            "\"Classes implementing the Parcelable interface must also have a " +
            "static field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",

            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ParcelDetector.class,
                    Scope.CLASS_FILE_SCOPE))
            .addMoreInfo("http://developer.android.com/reference/android/os/Parcelable.html");

    /** Constructs a new {@link com.android.tools.lint.checks.ParcelDetector} check */
    public ParcelDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
      // Only applies to concrete classes
        if ((classNode.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0) {
            return;
        }
        List interfaces = classNode.interfaces;
        if (interfaces != null) {
            for (Object o : interfaces) {
                if ("android/os/Parcelable".equals(o)) {
                    if (!hasCreatorField(context, classNode)) {
                        Location location = context.getLocation(classNode);
                        context.report(ISSUE, location, "This class implements Parcelable but does not provide a CREATOR field", null);
                    }
                    break;
                }
            }
        }
    }

    private static boolean hasCreatorField(@NonNull ClassContext context,
            @NonNull ClassNode classNode) {
        @SuppressWarnings("unchecked")
        List<FieldNode> fields = classNode.fields;
        if (fields != null) {
            for (FieldNode field : fields) {
                if (field.name.equals("CREATOR")) {
                    // TODO: Make sure it has the right type
                    String desc = field.desc;
                    return true;
                }
            }
        }

        return false;
    }
}
