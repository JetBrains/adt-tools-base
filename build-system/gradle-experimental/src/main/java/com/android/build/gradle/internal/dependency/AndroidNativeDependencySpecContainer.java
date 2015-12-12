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

package com.android.build.gradle.internal.dependency;

import org.gradle.model.internal.core.UnmanagedStruct;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Collection;

/**
 * Container for {@link AndroidNativeDependencySpec}.
 *
 * Code is based on {@link DependencySpecContainer}.  It should be removed when Gradle support more
 * flexibility in defining dependency specs.
 */
@UnmanagedStruct
public interface AndroidNativeDependencySpecContainer {

    AndroidNativeDependencySpec.Builder project(final String value);

    AndroidNativeDependencySpec.Builder library(final String value);

    AndroidNativeDependencySpec.Builder buildType(final String value);

    AndroidNativeDependencySpec.Builder productFlavor(final String value);

    Collection<AndroidNativeDependencySpec> getDependencies();

    boolean isEmpty();
}
