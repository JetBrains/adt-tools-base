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

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.TreeTraverser;

import java.util.Collections;
import java.util.List;

/**
 * {@link TreeTraverser} that finds all supertypes (both superclasses and interfaces) of types.
 */
public class TypeHierarchyTraverser<T> extends TreeTraverser<T> {

    private final ShrinkerGraph<T> mGraph;

    private final ShrinkerLogger mShrinkerLogger;

    private final boolean mIncludeInterfaces;

    private TypeHierarchyTraverser(
            ShrinkerGraph<T> graph,
            ShrinkerLogger shrinkerLogger,
            boolean includeInterfaces) {
        mGraph = graph;
        mShrinkerLogger = shrinkerLogger;
        mIncludeInterfaces = includeInterfaces;
    }

    public static <T> TypeHierarchyTraverser<T> superclassesAndInterfaces(ShrinkerGraph<T> graph,
            ShrinkerLogger shrinkerLogger) {
        return new TypeHierarchyTraverser<T>(graph, shrinkerLogger, true);
    }

    public static <T> TypeHierarchyTraverser<T> superclasses(ShrinkerGraph<T> graph,
            ShrinkerLogger shrinkerLogger) {
        return new TypeHierarchyTraverser<T>(graph, shrinkerLogger, false);
    }

    @Override
    public Iterable<T> children(@NonNull T klass) {
        List<T> result = Lists.newArrayList();
        try {
            T superclass = mGraph.getSuperclass(klass);
            if (superclass != null) {
                result.add(superclass);
            }
        } catch (ClassLookupException e) {
            mShrinkerLogger.invalidClassReference(mGraph.getClassName(klass), e.getClassName());
        }

        if (mIncludeInterfaces) {
            Collections.addAll(result, mGraph.getInterfaces(klass));
        }

        return result;
    }
}
