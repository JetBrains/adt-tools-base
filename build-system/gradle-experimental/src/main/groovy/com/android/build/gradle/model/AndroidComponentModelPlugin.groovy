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
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.builder.core.BuilderConstants
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.registry.LanguageRegistry
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.Defaults
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.Binary
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.ComponentTypeBuilder
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder

import javax.inject.Inject

/**
 * Plugin to set up infrastructure for other android plugins.
 */
@CompileStatic
public class AndroidComponentModelPlugin implements Plugin<Project> {
    /**
     * The name of ComponentSpec created with android component model plugin.
     */
    public static final String COMPONENT_NAME = "android";

    private final ModelRegistry modelRegistry

    @Inject
    public AndroidComponentModelPlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry
    }

    @Override
    public void apply(Project project) {
        project.apply plugin: ComponentModelBasePlugin

        // Remove this when our models no longer depends on Project.
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("projectModel", Project), project)
                                .descriptor("Model of project.")
                                .build())
    }

    static class Rules extends RuleSource {
        @LanguageType
        void registerLanguage(LanguageTypeBuilder<AndroidLanguageSourceSet> builder) {
            builder.setLanguageName("android")
            builder.defaultImplementation(AndroidLanguageSourceSet)
        }

        @Model("android")
        void android(AndroidModel androidModel) {
        }

        // Initialize each component separately to ensure correct ordering.
        @Defaults
        void androidModelBuildTypes (
                AndroidModel androidModel,
                @Path("androidBuildTypes") NamedDomainObjectContainer<BuildType> buildTypes) {
            androidModel.buildTypes = buildTypes
        }

        @Defaults
        void androidModelProductFlavors (
                AndroidModel androidModel,
                @Path("androidProductFlavors") NamedDomainObjectContainer<GroupableProductFlavor> productFlavors) {
            androidModel.productFlavors = productFlavors
        }

        @Defaults
        void androidModelSources (
                AndroidModel androidModel,
                @Path("androidSources") AndroidComponentModelSourceSet sources) {
            androidModel.sources = sources
        }

        @Model
        NamedDomainObjectContainer<BuildType> androidBuildTypes(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def buildTypeContainer = project.container(BuildType,
                    new BuildTypeFactory(instantiator, project, project.getLogger()))

            // create default Objects, signingConfig first as its used by the BuildTypes.
            buildTypeContainer.create(BuilderConstants.DEBUG)
            buildTypeContainer.create(BuilderConstants.RELEASE)

            buildTypeContainer.whenObjectRemoved {
                throw new UnsupportedOperationException("Removing build types is not supported.")
            }
            return buildTypeContainer
        }

        @Model
        NamedDomainObjectContainer<GroupableProductFlavor> androidProductFlavors(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def productFlavorContainer = project.container(GroupableProductFlavor,
                    new GroupableProductFlavorFactory(instantiator, project, project.getLogger()))

            productFlavorContainer.whenObjectRemoved {
                throw new UnsupportedOperationException(
                        "Removing product flavors is not supported.")
            }

            return productFlavorContainer
        }

        @Model
        List<ProductFlavorCombo> createProductFlavorCombo (
                @Path("android.productFlavors") NamedDomainObjectContainer<GroupableProductFlavor> productFlavors) {
            // TODO: Create custom product flavor container to manually configure flavor dimensions.
            List<String> flavorDimensionList = productFlavors*.dimension.unique().asList()
            flavorDimensionList.removeAll([null])

            return ProductFlavorCombo.createCombinations(flavorDimensionList, productFlavors)
        }

        @ComponentType
        void defineComponentType(ComponentTypeBuilder<AndroidComponentSpec> builder) {
            builder.defaultImplementation(DefaultAndroidComponentSpec)
        }

        @Mutate
        void createAndroidComponents(CollectionBuilder<AndroidComponentSpec> androidComponents) {
            androidComponents.create(COMPONENT_NAME)
        }

        @Model
        AndroidComponentSpec androidComponentSpec(ComponentSpecContainer specs) {
            return (AndroidComponentSpec) specs.getByName(COMPONENT_NAME)
        }

        @Model
        AndroidComponentModelSourceSet androidSources (
                ServiceRegistry serviceRegistry,
                ProjectSourceSet projectSourceSet,
                LanguageRegistry languageRegistry) {
            def instantiator = serviceRegistry.get(Instantiator.class)
            def sources = new AndroidComponentModelSourceSet(instantiator, projectSourceSet)

            languageRegistry.each { languageRegistration ->
                sources.registerLanguage(languageRegistration)
            }

            // Create main source set.
            sources.create("main")

            return sources
        }

        /**
         * Create all source sets for each AndroidBinary.
         *
         * Need to ensure this is done before model mutation in build.gradle.
         */
        @Mutate
        void createVariantSourceSet(
                @Path("android.sources") AndroidComponentModelSourceSet sources,
                @Path("android.buildTypes") NamedDomainObjectContainer<BuildType> buildTypes,
                @Path("android.productFlavors") NamedDomainObjectContainer<GroupableProductFlavor> flavors,
                List<ProductFlavorCombo> flavorGroups) {
            buildTypes.each { buildType ->
                sources.maybeCreate(buildType.name)
            }
            flavorGroups.each { group ->
                sources.maybeCreate(group.name)
                if (!group.flavorList.isEmpty()) {
                    buildTypes.each { buildType ->
                        sources.maybeCreate(group.name + buildType.name.capitalize())
                    }
                }
            }
            if (flavorGroups.size() != flavors.size()) {
                // If flavorGroups and flavors are the same size, there is at most 1 flavor
                // dimension.  So we don't need to reconfigure the source sets for flavorGroups.
                flavors.each { flavor ->
                    sources.maybeCreate(flavor.name)
                }
            }
        }

        @Finalize
        void setDefaultSrcDir(@Path("android.sources") AndroidComponentModelSourceSet sourceSet) {
            sourceSet.setDefaultSrcDir()
        }

        @BinaryType
        void defineBinaryType(BinaryTypeBuilder<AndroidBinary> builder) {
            builder.defaultImplementation(DefaultAndroidBinary)
        }

        @ComponentBinaries
        void createBinaries(
                CollectionBuilder<AndroidBinary> binaries,
                @Path("android.buildTypes") NamedDomainObjectContainer<BuildType> buildTypes,
                List<ProductFlavorCombo> flavorCombos,
                AndroidComponentSpec spec) {
            if (flavorCombos.isEmpty()) {
                flavorCombos.add(new ProductFlavorCombo());
            }

            buildTypes.each { BuildType buildType ->
                flavorCombos.each { ProductFlavorCombo flavorCombo ->
                    binaries.create(getBinaryName(buildType, flavorCombo)) {
                        def binary = it as DefaultAndroidBinary
                        binary.buildType = buildType
                        binary.productFlavors = flavorCombo.flavorList
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

}
