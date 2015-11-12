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

package com.android.build.gradle.internal.incremental;

/**
 * Changes to a class that cannot be hot swapped with the current InstantRun runtime
 */
public enum InstantRunVerifierStatus {

    // changes are compatible with current InstantRun features.
    COMPATIBLE,

    // the verifier did not run successfully.
    NOT_RUN,

    // InstantRun disabled on element like a method, class or package.
    INSTANT_RUN_DISABLED,

    // changes in the hierarchy
    PARENT_CLASS_CHANGED,
    IMPLEMENTED_INTERFACES_CHANGE,

    // class related changes.
    CLASS_ANNOTATION_CHANGE,
    STATIC_INITIALIZER_CHANGE,

    // changes in constructors,
    CONSTRUCTOR_SIGNATURE_CHANGE,

    // changes in method
    METHOD_SIGNATURE_CHANGE,
    METHOD_ANNOTATION_CHANGE,
    METHOD_DELETED,
    METHOD_ADDED,

    // changes in fields.
    FIELD_ADDED,
    FIELD_REMOVED,
    // change of field type or kind (static | instance)
    FIELD_TYPE_CHANGE,

    // reflection use
    REFLECTION_USED
}
