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

package com.android.build.gradle.model

import com.android.build.gradle.internal.ProductFlavorCombo
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.BuildType
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.ComponentTypeBuilder

/**
 * Plugin to set up infrastructure for other android plugins.
 */
public class AndroidComponentModelPlugin implements Plugin<Project> {
    /**
     * The name of ComponentSpec created with android component model plugin.
     */
    public static final String COMPONENT_NAME = "android";

    @Override
    public void apply(Project project) {
        // Remove this when our models no longer depends on Project.
        project.modelRegistry.create(
                ModelCreators.of(ModelReference.of("projectModel", Project), project)
                        .simpleDescriptor("Model of project.")
                        .build())
    }

    @RuleSource
    static class Rules {

        @Model("android.buildTypes")
        NamedDomainObjectContainer<DefaultBuildType> createBuildTypes(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def buildTypeContainer = project.container(DefaultBuildType,
                    new BuildTypeFactory(instantiator, project, project.getLogger()))

            // create default Objects, signingConfig first as its used by the BuildTypes.
            buildTypeContainer.create(BuilderConstants.DEBUG)
            buildTypeContainer.create(BuilderConstants.RELEASE)

            buildTypeContainer.whenObjectRemoved {
                throw new UnsupportedOperationException("Removing build types is not supported.")
            }
            return buildTypeContainer
        }

        @Model("android.productFlavors")
        NamedDomainObjectContainer<GroupableProductFlavorDsl> createProductFlavors(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def productFlavorContainer = project.container(GroupableProductFlavorDsl,
                    new GroupableProductFlavorFactory(instantiator, project, project.getLogger()))

            productFlavorContainer.whenObjectRemoved {
                throw new UnsupportedOperationException(
                        "Removing product flavors is not supported.")
            }

            return productFlavorContainer
        }

        @Model
        List<ProductFlavorCombo> createProductFlavorCombo (
                NamedDomainObjectContainer<GroupableProductFlavorDsl> productFlavors) {
            // TODO: Create custom product flavor container to manually configure flavor dimensions.
            List<String> flavorDimensionList = productFlavors*.flavorDimension.unique();
            flavorDimensionList.removeAll([null])

            return  ProductFlavorCombo.createCombinations(flavorDimensionList, productFlavors);
        }


        @ComponentType
        void defineComponentType(ComponentTypeBuilder<AndroidComponentSpec> builder) {
            builder.defaultImplementation(DefaultAndroidComponentSpec)
        }

        @Mutate
        void createAndroidComponents(
                CollectionBuilder<AndroidComponentSpec> androidComponents) {
            androidComponents.create(COMPONENT_NAME)
        }

        @BinaryType
        void defineBinaryType(BinaryTypeBuilder<AndroidBinary> builder) {
            builder.defaultImplementation(DefaultAndroidBinary)
        }

        @Mutate
        void createBinaries(
                BinaryContainer binaries,
                NamedDomainObjectContainer<DefaultBuildType> buildTypes,
                List<ProductFlavorCombo> flavorCombos,
                ComponentSpecContainer specs) {
            AndroidComponentSpec spec = (AndroidComponentSpec) specs.getByName(COMPONENT_NAME)
            if (flavorCombos.isEmpty()) {
                flavorCombos.add(new ProductFlavorCombo());
            }

            for (def buildType : buildTypes) {
                for (def flavorCombo : flavorCombos) {
                    DefaultAndroidBinary binary = (DefaultAndroidBinary) binaries.create(
                            getBinaryName(buildType, flavorCombo), AndroidBinary)
                    binary.buildType = buildType
                    binary.productFlavors = flavorCombo.flavorList
                    spec.binaries.add(binary)
                }
            }
        }
    }

    private static String getBinaryName(BuildType buildType, ProductFlavorCombo flavorCombo) {
        if (flavorCombo.flavorList.isEmpty()) {
            return  buildType.name
        } else {
            return  flavorCombo.name + buildType.name.capitalize()
        }
    }
}
