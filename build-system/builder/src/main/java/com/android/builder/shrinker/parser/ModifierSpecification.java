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

package com.android.builder.shrinker.parser;

import com.android.annotations.NonNull;

import org.objectweb.asm.Opcodes;

/**
 * Modifier part of a ProGuard class specification.
 */
public class ModifierSpecification implements Matcher<Integer> {
    private static final int ACCESSIBILITY_FLAGS =
            Opcodes.ACC_PUBLIC
                    | Opcodes.ACC_PROTECTED
                    | Opcodes.ACC_PRIVATE;

    private int modifier = 0;

    private int modifierWithNegator;

    public void addModifier(int modifier, boolean hasNegator) {
        if (hasNegator) {
            this.modifierWithNegator |= modifier;
        } else {
            this.modifier |= modifier;
        }
    }

    @Override
    public boolean matches(@NonNull Integer t) {
        // Combining multiple flags is allowed (e.g. public static).
        // It means that both access flags have to be set (e.g. public and static),
        // except when they are conflicting, in which case at least one of them has
        // to be set (e.g. at least public or protected).
        int toCompare = t;
        int accessflags = toCompare & ACCESSIBILITY_FLAGS;
        int accessflagsSpec = modifier & ACCESSIBILITY_FLAGS;
        if (accessflagsSpec != 0) {
            if ((accessflags | accessflagsSpec) != accessflagsSpec) {
                return false;
            }
            // If the visibility is "package" but the specification isn't,
            // the modifier doesn't match
            if (accessflags == 0) {
                return false;
            }
        }

        int negatorAccessFlags = modifierWithNegator & ACCESSIBILITY_FLAGS;
        if (negatorAccessFlags != 0) {
            if ((accessflags & negatorAccessFlags) != 0) {
                return false;
            }
        }

        int otherFlags = toCompare & ~ACCESSIBILITY_FLAGS;
        int otherFlagsSpec = modifier & ~ACCESSIBILITY_FLAGS;
        if ((otherFlags & otherFlagsSpec) != otherFlagsSpec) {
            return false;
        }

        int otherFlagsSpecWithNegator = modifierWithNegator & ~ACCESSIBILITY_FLAGS;

        return otherFlagsSpecWithNegator == 0
                || ((otherFlagsSpecWithNegator & ~otherFlags) == otherFlagsSpecWithNegator);
    }
}
