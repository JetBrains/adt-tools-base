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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.shrinker.parser.AnnotationSpecification;
import com.android.builder.shrinker.parser.ClassSpecification;
import com.android.builder.shrinker.parser.FieldSpecification;
import com.android.builder.shrinker.parser.Flags;
import com.android.builder.shrinker.parser.InheritanceSpecification;
import com.android.builder.shrinker.parser.Matcher;
import com.android.builder.shrinker.parser.MethodSpecification;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TODO: Document.
 */
class ProguardFlagsKeepRules implements KeepRules {

    private final Flags mFlags;

    public ProguardFlagsKeepRules(Flags flags) {
        mFlags = flags;
    }

    @Override
    public <T> Map<T, DependencyType> getSymbolsToKeep(T klass, ShrinkerGraph<T> graph) {
        Map<T, DependencyType> result = Maps.newHashMap();

        for (ClassSpecification spec : mFlags.getKeepClassSpecs()) {
            if (matchesClass(klass, spec, graph)) {
                result.put(klass, DependencyType.REQUIRED);
                result.put(graph.getMemberReference(graph.getClassName(klass), "<init>", "()V"), DependencyType.REQUIRED);
                for (T member : findMatchingMembers(klass, spec, graph)) {
                    result.put(member, DependencyType.REQUIRED);
                }
            }
        }

        for (ClassSpecification spec : mFlags.getKeepClassMembersSpecs()) {
            if (matchesClass(klass, spec, graph)) {
                for (T member : findMatchingMembers(klass, spec, graph)) {
                    result.put(member, DependencyType.IF_CLASS_KEPT);
                    graph.addDependency(klass, member, DependencyType.CLASS_IS_KEPT);
                }
            }
        }

        for (ClassSpecification spec : mFlags.getKeepClassesWithMembersSpecs()) {
            if (matchesClass(klass, spec, graph)) {
                for (T t : handleKeepClassesWithMembers(spec, klass, graph)) {
                    result.put(t, DependencyType.REQUIRED);
                }
            }
        }

        return result;
    }

    private static <T> List<T> handleKeepClassesWithMembers(
            ClassSpecification classSpec,
            T klass,
            ShrinkerGraph<T> graph) {
        List<T> result = Lists.newArrayList();

        for (MethodSpecification methodSpec : classSpec.getMethodSpecifications()) {
            boolean found = false;
            for (T method : graph.getMethods(klass)) {
                if (matchesMethod(method, methodSpec, graph)) {
                    found = true;
                    result.add(method);
                }
            }

            if (!found) {
                return Collections.emptyList();
            }
        }

        for (FieldSpecification fieldSpec : classSpec.getFieldSpecifications()) {
            boolean found = false;
            for (T method : graph.getMethods(klass)) {
                if (matchesField(method, fieldSpec, graph)) {
                    found = true;
                    result.add(method);
                }
            }

            if (!found) {
                return Collections.emptyList();
            }
        }

        // If we're here, then all member specs have matched something.
        result.add(klass);
        return result;
    }

    private static <T> List<T> findMatchingMembers(
            T klass,
            ClassSpecification spec,
            ShrinkerGraph<T> graph) {
        List<T> result = Lists.newArrayList();
        for (T method : graph.getMethods(klass)) {
            for (MethodSpecification methodSpec : spec.getMethodSpecifications()) {
                if (matchesMethod(method, methodSpec, graph)) {
                    result.add(method);
                }
            }
        }

        for (T field : graph.getFields(klass)) {
            for (FieldSpecification fieldSpecification : spec.getFieldSpecifications()) {
                if (matchesField(field, fieldSpecification, graph)) {
                    result.add(field);
                }
            }
        }

        return result;
    }

    private static <T> boolean matchesField(
            T field,
            FieldSpecification spec,
            ShrinkerGraph<T> graph) {
        return matches(spec.getName(), graph.getFieldName(field))
                && matches(spec.getModifier(), graph.getMemberModifiers(field))
                && matches(spec.getTypeSignature(), graph.getFieldDesc(field))
                && matchesAnnotations(field, spec.getAnnotations(), graph);
    }

    private static <T> boolean matchesMethod(
            T method,
            MethodSpecification spec,
            ShrinkerGraph<T> graph) {
        return matches(spec.getName(), graph.getMethodNameAndDesc(method))
                && matches(spec.getModifiers(), graph.getMemberModifiers(method))
                && matchesAnnotations(method, spec.getAnnotations(), graph);
    }

    private static <T> boolean matchesClass(
            T klass,
            ClassSpecification spec,
            ShrinkerGraph<T> graph) {
        int classModifiers = graph.getClassModifiers(klass);
        return matches(spec.getName(), graph.getClassName(klass))
                && matches(spec.getClassType(), classModifiers)
                && matches(spec.getModifier(), classModifiers)
                && matchesAnnotations(klass, spec.getAnnotation(), graph)
                && matchesInheritance(klass, spec.getInheritance(), graph);
    }

    private static <U> boolean matches(@Nullable Matcher<U> matcher, @NonNull U value) {
        return matcher == null || matcher.matches(value);
    }

    private static <T> boolean matchesAnnotations(
            @NonNull T classOrMember,
            @Nullable AnnotationSpecification annotation,
            @NonNull ShrinkerGraph<T> graph) {
        if (annotation == null) {
            return true;
        }

        for (String annotationName : graph.getAnnotations(classOrMember)) {
            if (annotation.getName().matches(annotationName)) {
                return true;
            }
        }

        return false;
    }

    private static <T> boolean matchesInheritance(
            @NonNull  T klass,
            @Nullable InheritanceSpecification spec,
            @NonNull ShrinkerGraph<T> graph) {
        if (spec == null) {
            return true;
        }
        // TODO: annotations.

        FluentIterable<T> superTypes = new TypeHierarchyTraverser<T>(graph)
                .preOrderTraversal(klass)
                .skip(1); // Skip the class itself.

        for (T superType : superTypes) {
            String name = graph.getClassName(superType);
            if (spec.getNameSpec().matches(name)) {
                return true;
            }
        }
        return false;
    }
}
