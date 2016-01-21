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

package com.android.build.gradle.shrinker.parser;

import static com.google.common.base.Preconditions.checkState;
import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ACC_ENUM;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;

/**
 * Represents the "class type" part of a ProGuard class specification.
 */
public class ClassTypeSpecification extends MatcherWithNegator<Integer> {

    private static final int CLASS_TYPE_FLAGS = ACC_INTERFACE | ACC_ENUM;

    private final int mSpec;

    public ClassTypeSpecification(int spec) {
        checkState((spec & (CLASS_TYPE_FLAGS | ACC_ANNOTATION)) == spec);
        mSpec = spec;
    }

    @Override
    protected boolean matchesWithoutNegator(Integer toCheck) {
        int modifiers = toCheck;

        //noinspection SimplifiableIfStatement
        if (((mSpec & ACC_ANNOTATION) != 0) && ((modifiers & ACC_ANNOTATION) == 0)) {
            // Only look at the annotation bit if the keep rule mentioned annotations.
            return false;
        }

        if ((mSpec & CLASS_TYPE_FLAGS) == 0) {
            // "The class keyword refers to any interface or class."
            return true;
        }

        return (modifiers & CLASS_TYPE_FLAGS) == (mSpec & CLASS_TYPE_FLAGS);
    }
}
