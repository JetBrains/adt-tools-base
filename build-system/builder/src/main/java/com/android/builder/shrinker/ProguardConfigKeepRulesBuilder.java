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
import com.android.builder.shrinker.parser.Flags;
import com.android.builder.shrinker.parser.GrammarActions;
import com.android.builder.shrinker.parser.MethodSpecification;
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

            for (ClassSpecification spec : mFlags.getKeepClassSpecs()) {
                if (!matchesClass(klass, spec, graph)) {
                    continue;
                }
                // TODO: Add klass to result?

                for (T method : graph.getMethods(klass)) {
                    for (MethodSpecification methodSpec : spec.getMethodSpecifications()) {
                        if (matchesMethod(method, methodSpec, graph)) {
                            result.add(method);
                        }
                    }
                }

                // TODO: fields etc.
            }

            if (!mFlags.getKeepClassesWithMembersSpecs().isEmpty()) {
                throw Shrinker.todo("keepClassesWithMembers");
            }

            if (!mFlags.getKeepClassMembersSpecs().isEmpty()) {
                throw Shrinker.todo("keepClassesMembers");
            }

            return result;
        }

        private static <T> boolean matchesMethod(
                T method,
                MethodSpecification methodSpec,
                ShrinkerGraph<T> graph) {
            // TODO: finish.
            return methodSpec.getNameSpecification().matches(graph.getMethodName(method));
        }

        private static <T> boolean matchesClass(
                T klass,
                ClassSpecification spec,
                ShrinkerGraph<T> graph) {
            // TODO: finish.
            return spec.getNameSpec().matches(graph.getClassName(klass));
        }
    }
}
