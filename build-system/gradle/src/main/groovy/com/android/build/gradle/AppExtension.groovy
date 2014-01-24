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
package com.android.build.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.dsl.PackagingOptionsImpl
import com.android.builder.DefaultBuildType
import com.android.builder.DefaultProductFlavor
import com.android.builder.model.SigningConfig
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for 'application' project.
 */
public class AppExtension extends BaseExtension {

    final NamedDomainObjectContainer<DefaultProductFlavor> productFlavors
    final NamedDomainObjectContainer<DefaultBuildType> buildTypes
    final NamedDomainObjectContainer<SigningConfig> signingConfigs


    private final DefaultDomainObjectSet<ApplicationVariant> applicationVariantList =
        new DefaultDomainObjectSet<ApplicationVariant>(ApplicationVariant.class)

    List<String> flavorGroupList
    String testBuildType = "debug"

    AppExtension(AppPlugin plugin, ProjectInternal project, Instantiator instantiator,
                 NamedDomainObjectContainer<DefaultBuildType> buildTypes,
                 NamedDomainObjectContainer<DefaultProductFlavor> productFlavors,
                 NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        super(plugin, project, instantiator)
        this.buildTypes = buildTypes
        this.productFlavors = productFlavors
        this.signingConfigs = signingConfigs
    }

    void buildTypes(Action<? super NamedDomainObjectContainer<DefaultBuildType>> action) {
        plugin.checkTasksAlreadyCreated();
        action.execute(buildTypes)
    }

    void productFlavors(Action<? super NamedDomainObjectContainer<DefaultProductFlavor>> action) {
        plugin.checkTasksAlreadyCreated();
        action.execute(productFlavors)
    }

    void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfig>> action) {
        plugin.checkTasksAlreadyCreated();
        action.execute(signingConfigs)
    }

    public void flavorGroups(String... groups) {
        plugin.checkTasksAlreadyCreated();
        flavorGroupList = Arrays.asList(groups)
    }

    public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return applicationVariantList
    }

    void addApplicationVariant(ApplicationVariant applicationVariant) {
        applicationVariantList.add(applicationVariant)
    }
}
