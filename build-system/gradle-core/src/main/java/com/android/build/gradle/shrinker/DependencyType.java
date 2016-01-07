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

package com.android.build.gradle.shrinker;

/**
 * Type of the dependency (edge) between two nodes in the shrinker graph.
 *
 * <p>A node is considered "reachable" if:
 * <ul>
 * <li>it can be reached with a required edge, or
 * <li>at least one {@link #IF_CLASS_KEPT} and at least one {@link #CLASS_IS_KEPT} edge, or
 * <li>at least one {@link #SUPERINTERFACE_KEPT} and at least one {@link #INTERFACE_IMPLEMENTED}
 *     edge.
 * </ul>
 *
 * <p>The second case models the situation where a method implements an interface method. The only
 * case when it should be kept is if the interface method is called somewhere (1) AND the containing
 * class is used in some way (2). Otherwise the super-method can be removed from both the interface
 * and the class (not 1) or the whole implementation class can be removed (not 2).
 */
public enum DependencyType {
    /** Target is referenced from an opcode. */
    REQUIRED_CODE_REFERENCE,

    /** Target type is referenced in class declaration. */
    REQUIRED_CLASS_STRUCTURE,

    /**
     * Target member should be kept, assuming its owner class is kept.
     * @see #CLASS_IS_KEPT
     */
    IF_CLASS_KEPT,

    /**
     * Target member's owner class is kept.
     * @see #IF_CLASS_KEPT
     */
    CLASS_IS_KEPT,

    /**
     * Superinterface of the target interface is kept. If the target is implemented by some class,
     * it should be kept as well.
     *
     * @see #INTERFACE_IMPLEMENTED
     */
    SUPERINTERFACE_KEPT,

    /**
     * Target interface is implemented (directly or indirectly) by a kept class, so it may need to
     * be kept if a superinterface is kept as well.
     *
     * @see #SUPERINTERFACE_KEPT
     */
    INTERFACE_IMPLEMENTED,
    ;
}
