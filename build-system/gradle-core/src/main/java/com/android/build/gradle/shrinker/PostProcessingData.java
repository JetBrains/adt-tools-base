/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Data object that records all "tasks" that need to be done, once all the graph nodes are in place.
 *
 * <p>Many edges can only be added to the graph, once the whole class structure is known. That's why
 * we record nodes that need "additional attention" for later, when reading input classes.
 */
class PostProcessingData<T> {
    @NonNull
    private final Set<T> virtualMethods = Sets.newConcurrentHashSet();
    @NonNull
    private final Set<T> multipleInheritance = Sets.newConcurrentHashSet();
    @NonNull
    private final Set<T> interfaceInheritance = Sets.newConcurrentHashSet();
    @NonNull
    private final Set<UnresolvedReference<T>> unresolvedReferences = Sets.newConcurrentHashSet();

    @NonNull
    Set<T> getVirtualMethods() {
        return virtualMethods;
    }

    @NonNull
    Set<T> getMultipleInheritance() {
        return multipleInheritance;
    }

    @NonNull
    Set<T> getInterfaceInheritance() {
        return interfaceInheritance;
    }

    @NonNull
    Set<UnresolvedReference<T>> getUnresolvedReferences() {
        return unresolvedReferences;
    }

    static class UnresolvedReference<T> {
        @NonNull final T method;
        @NonNull final T target;
        final boolean invokespecial;
        @NonNull final DependencyType dependencyType;

        UnresolvedReference(@NonNull T method, @NonNull T target, boolean invokespecial) {
            this.method = method;
            this.target = target;
            this.invokespecial = invokespecial;
            this.dependencyType = DependencyType.REQUIRED_CODE_REFERENCE;
        }

        public UnresolvedReference(
                @NonNull T method,
                @NonNull T target,
                boolean invokespecial,
                @NonNull DependencyType dependencyType) {
            this.method = method;
            this.target = target;
            this.dependencyType = dependencyType;
            this.invokespecial = invokespecial;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("method", method)
                    .add("target", target)
                    .add("invokespecial", invokespecial)
                    .toString();
        }
    }
}
