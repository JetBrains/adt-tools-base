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

package com.android.builder.shrinker;

/**
 * Type of the dependency (edge) between two nodes in the shrinker graph.
 *
 * <p>A node is considered "reachable" if it can be reached with a {@link #REQUIRED} edge, or
 * at least one {@link #IF_CLASS_KEPT} and at least one {@link #CLASS_IS_KEPT} edge.
 *
 * <p>The second case models the situation where a method implements an interface method. The only
 * case when it should be kept is if the interface method is called somewhere (1) AND the containing
 * class is used in some way (2). Otherwise the super-method can be removed from both the interface
 * and the class (not 1) or the whole implementation class can be removed (not 2).
 */
public enum DependencyType {
    REQUIRED,
    IF_CLASS_KEPT,
    CLASS_IS_KEPT,;
}
