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

import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dsl.BuildTypeDsl
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavorDsl
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.test.PluginHolder
import com.android.builder.DefaultBuildType
import com.android.builder.model.SigningConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

import static com.android.builder.BuilderConstants.DEBUG
import static com.android.builder.BuilderConstants.RELEASE
/**
 * Gradle plugin class for 'application' projects.
 */
class AppPlugin extends BasePlugin implements Plugin<Project> {
    static PluginHolder pluginHolder;

    AppExtension extension
    VariantManager variantManager

    @Inject
    public AppPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    public AppExtension getExtension() {
        return extension
    }

    @Override
    public VariantManager getVariantManager() {
        return variantManager
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        // This is for testing.
        if (pluginHolder != null) {
            pluginHolder.plugin = this;
        }

        def buildTypeContainer = project.container(DefaultBuildType,
                new BuildTypeFactory(instantiator,  project.fileResolver, project.getLogger()))
        def productFlavorContainer = project.container(GroupableProductFlavorDsl,
                new GroupableProductFlavorFactory(instantiator, project.fileResolver, project.getLogger()))
        def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))

        extension = project.extensions.create('android', AppExtension,
                this, (ProjectInternal) project, instantiator,
                buildTypeContainer, productFlavorContainer, signingConfigContainer)
        setBaseExtension(extension)

        variantManager = new VariantManager(project, this, extension)

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded { SigningConfig signingConfig ->
            variantManager.addSigningConfig((SigningConfigDsl) signingConfig)
        }

        buildTypeContainer.whenObjectAdded { DefaultBuildType buildType ->
            variantManager.addBuildType((BuildTypeDsl) buildType)
        }

        productFlavorContainer.whenObjectAdded { GroupableProductFlavorDsl productFlavor ->
            variantManager.addProductFlavor(productFlavor)
        }

        // create default Objects, signingConfig first as its used by the BuildTypes.
        signingConfigContainer.create(DEBUG)
        buildTypeContainer.create(DEBUG)
        buildTypeContainer.create(RELEASE)

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not supported.")
        }
        productFlavorContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not supported.")
        }
    }

    /**
     * Task creation entry point.
     */
    @Override
    protected void doCreateAndroidTasks() {
        variantManager.createAndroidTasks()
    }
}
