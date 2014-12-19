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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.internal.DefaultCSourceSet;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.internal.DefaultCppSourceSet;

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

    public AndroidComponentModelSourceSet (
            final Instantiator instantiator,
            ProjectSourceSet sources,
            final FileResolver fileResolver) {
        super(FunctionalSourceSet.class, instantiator);
        this.sources = sources;

        // Hardcoding registered language sets and default source sets for now.
        all(new Action<FunctionalSourceSet>() {
            @Override
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                functionalSourceSet.registerFactory(
                        AndroidLanguageSourceSet.class,
                        new NamedDomainObjectFactory<AndroidLanguageSourceSet>() {
                            @Override
                            public AndroidLanguageSourceSet create(String name) {
                                return (AndroidLanguageSourceSet) instantiator.newInstance(
                                        AndroidLanguageSourceSet.class,
                                        name,
                                        functionalSourceSet.getName(),
                                        fileResolver);
                            }
                        });
                functionalSourceSet.registerFactory(
                        CSourceSet.class,
                        new NamedDomainObjectFactory<CSourceSet>() {
                            @Override
                            public CSourceSet create(String name) {
                                return instantiator.newInstance(
                                        DefaultCSourceSet.class,
                                        name,
                                        functionalSourceSet.getName(),
                                        fileResolver);
                            }
                        });
                functionalSourceSet.registerFactory(
                        CppSourceSet.class,
                        new NamedDomainObjectFactory<CppSourceSet>() {
                            @Override
                            public CppSourceSet create(String name) {
                                return instantiator.newInstance(
                                        DefaultCppSourceSet.class,
                                        name,
                                        functionalSourceSet.getName(),
                                        fileResolver);
                            }
                        });
            }
        });

        addDefaultSourceSet("resources", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("java", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("manifest", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("res", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("assets", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("aidl", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("renderscript", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("jniLibs", AndroidLanguageSourceSet.class);
        addDefaultSourceSet("c", CSourceSet.class);
        addDefaultSourceSet("cpp", CppSourceSet.class);
    }

    @Override
    protected FunctionalSourceSet doCreate(String name) {
        return getInstantiator().newInstance(
                DefaultFunctionalSourceSet.class,
                name,
                getInstantiator(),
                sources);
    }

    private void addDefaultSourceSet(final String sourceSetName, final Class<? extends LanguageSourceSet> type) {
        all(new Action<FunctionalSourceSet>() {
            @Override
            public void execute(FunctionalSourceSet functionalSourceSet) {
                LanguageSourceSet sourceSet= functionalSourceSet.maybeCreate(sourceSetName, type);
            }
        });
    }

    /**
     * Set the default directory for each source sets if it is empty.
     */
    public void setDefaultSrcDir() {
        all(new Action<FunctionalSourceSet>() {
            @Override
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                functionalSourceSet.all(
                        new Action<LanguageSourceSet>() {
                            @Override
                            public void execute(LanguageSourceSet languageSourceSet) {
                                SourceDirectorySet source = languageSourceSet.getSource();
                                if (source.getSrcDirs().isEmpty()) {
                                    source.srcDir("src/" + functionalSourceSet.getName() + "/" + languageSourceSet.getName());
                                }
                            }
                        });
            }
        });
    }
}