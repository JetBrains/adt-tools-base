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

package com.android.build.gradle.internal;

import com.android.build.gradle.internal.dependency.AndroidNativeDependencySpecContainer;

import org.gradle.api.Action;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;

/**
 * LanguageSourceSet supporting native dependency on project with NDK component.
 */
public abstract class AbstractNativeDependentSourceSet extends BaseLanguageSourceSet
        implements LanguageSourceSet {

    private final AndroidNativeDependencySpecContainer dependencyContainer =
            new AndroidNativeDependencySpecContainer();

    @Override
    public boolean getMayHaveSources() {
        return super.getMayHaveSources() || !dependencyContainer.isEmpty();
    }

    public AndroidNativeDependencySpecContainer getDependencies() {
        return dependencyContainer;
    }

    @SuppressWarnings("unused")  // External API
    public AndroidNativeDependencySpecContainer dependencies(
            Action<AndroidNativeDependencySpecContainer> configureAction) {
        configureAction.execute(getDependencies());
        return getDependencies();
    }

}
