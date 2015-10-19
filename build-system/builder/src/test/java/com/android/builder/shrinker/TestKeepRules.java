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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

/**
 * Simple {@link KeepRules} implementation for testing.
 */
class TestKeepRules implements KeepRules {
    private final String mClassName;
    private final Set<String> mMethodNames;

    TestKeepRules(String className, String... methodNames) {
        mClassName = className;
        mMethodNames = ImmutableSet.copyOf(methodNames);
    }

    @Override
    public <T> Map<T, DependencyType> getSymbolsToKeep(T klass, ShrinkerGraph<T> graph) {
        Map<T, DependencyType> symbols = Maps.newHashMap();

        if (graph.getClassName(klass).endsWith(mClassName)) {
            for (T method : graph.getMethods(klass)) {
                String name = graph.getMethodNameAndDesc(method);
                for (String methodName : mMethodNames) {
                    if (name.equals(methodName)) {
                        symbols.put(method, DependencyType.REQUIRED);
                        symbols.put(klass, DependencyType.REQUIRED);
                    }
                }
            }
        }

        return symbols;
    }
}
