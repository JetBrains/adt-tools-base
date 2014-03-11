/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;

/**
 * Factory to create GroupableProductFlavorDsl object using an {@link Instantiator} to add
 * the DSL methods.
 */
public class GroupableProductFlavorFactory implements NamedDomainObjectFactory<GroupableProductFlavorDsl> {

    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private final FileResolver fileResolver;
    @NonNull
    private final Logger logger;

    public GroupableProductFlavorFactory(@NonNull Instantiator instantiator,
                                         @NonNull FileResolver fileResolver,
                                         @NonNull Logger logger) {
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
        this.logger = logger;
    }

    @Override
    public GroupableProductFlavorDsl create(String name) {
        return instantiator.newInstance(GroupableProductFlavorDsl.class,
                name, fileResolver, instantiator, logger);
    }
}
