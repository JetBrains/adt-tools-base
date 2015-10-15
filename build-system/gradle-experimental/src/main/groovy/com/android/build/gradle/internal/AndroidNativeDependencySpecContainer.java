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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.gradle.api.Action;
import org.gradle.platform.base.DependencySpecContainer;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Container for {@link AndroidNativeDependencySpec}.
 * Code is based on {@link DependencySpecContainer}.  It should be removed when Gradle support more
 * flexibility in defining dependency specs.
 */
public class AndroidNativeDependencySpecContainer {

    private final List<AndroidNativeDependencySpec.Builder> builders =
            new LinkedList<AndroidNativeDependencySpec.Builder>();

    public AndroidNativeDependencySpec.Builder project(final String value) {
        return doCreate(new Action<AndroidNativeDependencySpec.Builder>() {
            @Override
            public void execute(AndroidNativeDependencySpec.Builder builder) {
                builder.project(value);
            }
        });
    }

    public AndroidNativeDependencySpec.Builder library(final File value) {
        return doCreate(new Action<AndroidNativeDependencySpec.Builder>() {
            @Override
            public void execute(AndroidNativeDependencySpec.Builder builder) {
                builder.library(value);
            }
        });
    }

    public AndroidNativeDependencySpec.Builder buildType(final String value) {
        return doCreate(new Action<AndroidNativeDependencySpec.Builder>() {
            @Override
            public void execute(AndroidNativeDependencySpec.Builder builder) {
                builder.buildType(value);
            }
        });
    }

    public AndroidNativeDependencySpec.Builder productFlavor(final String value) {
        return doCreate(new Action<AndroidNativeDependencySpec.Builder>() {
            @Override
            public void execute(AndroidNativeDependencySpec.Builder builder) {
                builder.productFlavor(value);
            }
        });
    }

    public AndroidNativeDependencySpec.Builder abi(final String value) {
        return doCreate(new Action<AndroidNativeDependencySpec.Builder>() {
            @Override
            public void execute(AndroidNativeDependencySpec.Builder builder) {
                builder.abi(value);
            }
        });
    }

    public Collection<AndroidNativeDependencySpec> getDependencies() {
        if (builders.isEmpty()) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(Lists.transform(builders,
                new Function<AndroidNativeDependencySpec.Builder, AndroidNativeDependencySpec>() {
                    @Override
                    public AndroidNativeDependencySpec apply(AndroidNativeDependencySpec.Builder builder) {
                        return builder.build();
                    }
                }));
    }

    private AndroidNativeDependencySpec.Builder doCreate(
            Action<? super AndroidNativeDependencySpec.Builder> action) {
        AndroidNativeDependencySpec.Builder builder = new AndroidNativeDependencySpec.Builder();
        action.execute(builder);
        builders.add(builder);
        return builder;
    }

    public boolean isEmpty() {
        return builders.isEmpty();
    }
}
