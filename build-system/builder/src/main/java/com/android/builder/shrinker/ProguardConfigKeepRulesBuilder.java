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

import static com.google.common.base.Preconditions.checkState;

import com.android.builder.shrinker.parser.ClassSpecification;
import com.android.builder.shrinker.parser.FieldSpecification;
import com.android.builder.shrinker.parser.Flags;
import com.android.builder.shrinker.parser.GrammarActions;
import com.android.builder.shrinker.parser.InheritanceSpecification;
import com.android.builder.shrinker.parser.MethodSpecification;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

import org.antlr.runtime.RecognitionException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Stub of a real parser. Only checks the most simple rules produced by AAPT.
 */
public class ProguardConfigKeepRulesBuilder {

    private final Flags mFlags = new Flags();
    private boolean done = false;

    public void parse(File configFile) throws IOException {
        checkState(!done, "getKeepRules() already called.");
        try {
            GrammarActions.parse(configFile.getPath(), ".", mFlags);
        } catch (RecognitionException e) {
            throw new RuntimeException(e);
        }
    }

    public KeepRules getKeepRules() {
        done = true;
        // TODO: check for -dontobfuscate and other flags.
        return new ProguardFlagsKeepRules(mFlags);
    }

    private static class ProguardFlagsKeepRules implements KeepRules {
        private final Flags mFlags;

        public ProguardFlagsKeepRules(Flags flags) {
            mFlags = flags;
        }

        @Override
        public <T> Set<T> getSymbolsToKeep(T klass, ShrinkerGraph<T> graph) {
            Set<T> result = Sets.newHashSet();

            if (!mFlags.getKeepClassesWithMembersSpecs().isEmpty()) {
                throw Shrinker.todo("-keepclasseswithmembers");
            }

            for (ClassSpecification spec : mFlags.getKeepClassSpecs()) {
                if (matchesClass(klass, spec, graph)) {
                    // TODO: Add <init>(), per spec.
                    result.add(klass);
                    addMembers(klass, spec, graph, result);
                }
            }

            for (ClassSpecification spec : mFlags.getKeepClassMembersSpecs()) {
                if (matchesClass(klass, spec, graph)) {
                    // This is not enough to keep the class. But if the class is kept, then calling
                    // getClassesToKeep on the graph will return it and then we will end up calling
                    // getMembersToKeep() and these methods will show up.
                    addMembers(klass, spec, graph, result);
                }
            }

            return result;
        }

        private static <T> void addMembers(T klass, ClassSpecification spec, ShrinkerGraph<T> graph,
                Set<T> result) {
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
        }

        private static <T> boolean matchesField(
                T field,
                FieldSpecification spec,
                ShrinkerGraph<T> graph) {
            if (spec.getAnnotations() != null) {
                throw Shrinker.todo("Annotations in class_spec");
            }
            return spec.getName().matches(graph.getFieldName(field))
                    && spec.getModifier().matches(graph.getMemberModifiers(field))
                    && (spec.getTypeSignature() == null
                            || spec.getTypeSignature().matches(graph.getFieldDesc(field)));
        }

        private static <T> boolean matchesMethod(
                T method,
                MethodSpecification spec,
                ShrinkerGraph<T> graph) {
            if (spec.getAnnotations() != null) {
                throw Shrinker.todo("Annotations in class_spec");
            }
            return spec.getName().matches(graph.getMethodName(method))
                    && (spec.getModifiers() == null
                            || spec.getModifiers().matches(graph.getMemberModifiers(method)));
        }

        private static <T> boolean matchesClass(
                T klass,
                ClassSpecification spec,
                ShrinkerGraph<T> graph) {
            if (spec.getInheritance() != null) {
                if (!matchesInheritance(klass, spec.getInheritance(), graph)) {
                    return false;
                }
            }

            if (spec.getAnnotation() != null) {
                throw Shrinker.todo("Annotations in class_spec");
            }

            return spec.getName().matches(graph.getClassName(klass))
                    && spec.getClassType().matches(graph.getClassModifiers(klass))
                    && (spec.getModifier() == null
                            || spec.getModifier().matches(graph.getClassModifiers(klass)))
                    && (spec.getInheritance() == null
                            || matchesInheritance(klass, spec.getInheritance(), graph));
        }
    }

    private static <T> boolean matchesInheritance(
            T klass,
            InheritanceSpecification spec,
            ShrinkerGraph<T> graph) {
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
