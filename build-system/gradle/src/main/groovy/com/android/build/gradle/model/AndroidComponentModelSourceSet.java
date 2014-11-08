/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.model;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;

/**
 * Collection of source sets for each build type, product flavor or variant.
 *
 * Until Gradle provide a way to create and store source sets to use between multiple binaries, we
 * need to create a container for such source sets.
 */
// TODO: Remove dependencies on internal Gradle class.
public class AndroidComponentModelSourceSet
        extends AbstractNamedDomainObjectContainer<FunctionalSourceSet>
        implements NamedDomainObjectContainer<FunctionalSourceSet> {
    ProjectSourceSet sources;

    public AndroidComponentModelSourceSet (Instantiator instantiator, ProjectSourceSet sources) {
        super(FunctionalSourceSet.class, instantiator);
        this.sources = sources;
    }

    @Override
    protected FunctionalSourceSet doCreate(String name) {
        return getInstantiator().newInstance(
                DefaultFunctionalSourceSet.class,
                name,
                getInstantiator(),
                sources);
    }
}