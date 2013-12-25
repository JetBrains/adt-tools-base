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
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;

/**
 * Factory to create AndroidSourceSet object using an {@link Instantiator} to add
 * the DSL methods.
 */
public class AndroidSourceSetFactory implements NamedDomainObjectFactory<AndroidSourceSet> {

    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private final FileResolver fileResolver;

    public AndroidSourceSetFactory(@NonNull Instantiator instantiator,
                                   @NonNull FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    @Override
    public AndroidSourceSet create(String name) {
        return instantiator.newInstance(DefaultAndroidSourceSet.class, name, fileResolver);
    }
}
