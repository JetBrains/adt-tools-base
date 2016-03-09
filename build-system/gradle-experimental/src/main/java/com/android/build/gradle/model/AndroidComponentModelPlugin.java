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

package com.android.build.gradle.model;

import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.build.gradle.internal.ProductFlavorCombo;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.model.internal.AndroidBinaryInternal;
import com.android.build.gradle.model.internal.AndroidComponentSpecInternal;
import com.android.build.gradle.model.internal.DefaultAndroidBinary;
import com.android.build.gradle.model.internal.DefaultAndroidComponentSpec;
import com.android.build.gradle.model.internal.DefaultAndroidLanguageSourceSet;
import com.android.build.gradle.model.internal.DefaultJniLibsSourceSet;
import com.android.builder.Version;
import com.android.builder.core.BuilderConstants;
import com.android.repository.Revision;
import com.android.utils.StringHelper;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.Defaults;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.UnsupportedVersionException;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plugin to set up infrastructure for other android plugins.
 */
public class AndroidComponentModelPlugin implements Plugin<Project> {

    /**
     * The name of ComponentSpec created with android component model plugin.
     */
    public static final String COMPONENT_NAME = "android";

    public static final String GRADLE_ACCEPTABLE_VERSION = "2.14";

    private static final String GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "com.android.build.gradle.overrideVersionCheck";

    @Override
    public void apply(Project project) {
        checkPluginVersion();
        checkGradleVersion(project);
        project.getPlugins().apply(ComponentModelBasePlugin.class);
    }

    /**
     * Verify the plugin version.  If a newer version of gradle plugin is applied, then builder.jar
     * module will be resolved to a different version than the one this gradle-experimental plugin
     * is compiled with.  Throw an error and suggest to update this plugin.
     */
    public static void checkPluginVersion() {
        String actualGradlePluginVersion = Version.getAndroidGradleComponentPluginVersion();
        if(!actualGradlePluginVersion.equals(
                com.android.build.gradle.model.Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION)) {
            throw new UnsupportedVersionException(String.format("Plugin version mismatch.  "
                            + "'com.android.tools.build:gradle:%s' was applied, and it "
                            + "requires 'com.android.tools.build:gradle-experimental:%s'.  Current "
                            + "version is '%s'.  Please update to version '%s'.",
                    Version .getAndroidGradlePluginVersion(),
                    Version .getAndroidGradleComponentPluginVersion(),
                    com.android.build.gradle.model.Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION,
                    Version .getAndroidGradleComponentPluginVersion()));
        }
    }

    private static void checkGradleVersion(Project project) {
        String gradleVersion = project.getGradle().getGradleVersion();
        if (!gradleVersion.startsWith(GRADLE_ACCEPTABLE_VERSION)) {
            boolean allowNonMatching = Boolean.getBoolean(GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY);
            File file = new File("gradle" + File.separator + "wrapper" + File.separator +
                    "gradle-wrapper.properties");
            String errorMessage = String.format(
                    "Gradle version %s is required. Current version is %s. " +
                            "If using the gradle wrapper, try editing the distributionUrl in %s " +
                            "to gradle-%s-all.zip",
                    GRADLE_ACCEPTABLE_VERSION, gradleVersion, file.getAbsolutePath(),
                    GRADLE_ACCEPTABLE_VERSION);
            if (allowNonMatching) {
                project.getLogger().warn(errorMessage);
                project.getLogger().warn("As %s is set, continuing anyways.",
                        GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY);
            } else {
                throw new BuildException(errorMessage, null);
            }
        }
    }

    public static class Rules extends RuleSource {

        /**
         * Create "android" model block.
         */
        @Model("android")
        public void android(AndroidConfig androidModel) {
        }

        @Finalize
        public static void finalizeAndroidModel(AndroidConfig androidModel) {
            if (androidModel.getBuildToolsRevision() == null
                    && androidModel.getBuildToolsVersion() != null) {
                //The underlying Revision class has the maven artifact semantic,
                // so 20 is not the same as 20.0. For the build tools revision this
                // is not the desired behavior, so normalize e.g. to 20.0.0.
                androidModel.setBuildToolsRevision(
                        Revision.parseRevision(
                                androidModel.getBuildToolsVersion(),
                                Revision.Precision.MICRO));
            }

            if (androidModel.getCompileSdkVersion() != null
                    && !androidModel.getCompileSdkVersion().startsWith("android-")
                    && Ints.tryParse(androidModel.getCompileSdkVersion()) != null) {
                androidModel.setCompileSdkVersion("android-" + androidModel.getCompileSdkVersion());
            }

        }

        @Defaults
        public static void createDefaultBuildTypes(
                @Path("android.buildTypes") ModelMap<BuildType> buildTypes) {
            buildTypes.create(BuilderConstants.DEBUG, buildType -> {
                buildType.setDebuggable(true);
                buildType.setEmbedMicroApp(false);
            });
            buildTypes.create(BuilderConstants.RELEASE);
        }

        @Model
        public static List<ProductFlavorCombo<ProductFlavor>> createProductFlavorCombo(
                @Path("android.productFlavors") ModelMap<ProductFlavor> productFlavors) {
            // TODO: Create custom product flavor container to manually configure flavor dimensions.
            Set<String> flavorDimensionList = productFlavors.values().stream()
                    .filter(flavor -> flavor.getDimension() != null)
                    .map(ProductFlavor::getDimension).collect(Collectors.toSet());

            return ProductFlavorCombo.createCombinations(
                    Lists.newArrayList(flavorDimensionList),
                    productFlavors.values());
        }

        @ComponentType
        public static void defineComponentType(TypeBuilder<AndroidComponentSpec> builder) {
            builder.defaultImplementation(DefaultAndroidComponentSpec.class);
            builder.internalView(AndroidComponentSpecInternal.class);
        }

        @Mutate
        public static void createAndroidComponents(
                ModelMap<AndroidComponentSpec> androidComponents) {
            androidComponents.create(COMPONENT_NAME);
        }

        /**
         * Create all source sets for each AndroidBinary.
         */
        @Mutate
        public static void createVariantSourceSet(
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                @Path("android.buildTypes") final ModelMap<BuildType> buildTypes,
                @Path("android.productFlavors") ModelMap<ProductFlavor> flavors,
                List<ProductFlavorCombo<ProductFlavor>> flavorGroups,
                ProjectSourceSet projectSourceSet) {

            // Create main source set.
            sources.create("main");
            sources.create(ANDROID_TEST.getPrefix());
            sources.create(UNIT_TEST.getPrefix());

            for (BuildType buildType : buildTypes.values()) {
                sources.create(buildType.getName());

                if (!flavors.isEmpty()) {
                    for (ProductFlavorCombo group : flavorGroups) {
                        if (!group.getFlavorList().isEmpty()) {
                            sources.create(
                                    group.getName() + StringHelper.capitalize(buildType.getName()));
                        }
                    }
                }
            }
            for (ProductFlavorCombo group: flavorGroups) {
                sources.create(group.getName());
            }
            if (flavorGroups.size() != flavors.size()) {
                // If flavorGroups and flavors are the same size, there is at most 1 flavor
                // dimension.  So we don't need to reconfigure the source sets for flavorGroups.
                for (ProductFlavor flavor: flavors.values()) {
                    sources.create(flavor.getName());
                }
            }
            sources.afterEach(functionalSourceSet -> functionalSourceSet.afterEach(
                    languageSourceSet -> {
                        SourceDirectorySet source = languageSourceSet.getSource();
                        if (source.getSrcDirs().isEmpty()) {
                            source.srcDir("src/" + languageSourceSet.getParentName()
                                    + "/" + languageSourceSet.getName());
                        }
                    }));

        }

        @ComponentType
        public static void defineBinaryType(TypeBuilder<AndroidBinary> builder) {
            builder.defaultImplementation(DefaultAndroidBinary.class);
            builder.internalView(AndroidBinaryInternal.class);
        }

        @ComponentBinaries
        public static void createBinaries(
                final ModelMap<AndroidBinary> binaries,
                @Path("android") final AndroidConfig androidConfig,
                @Path("android.buildTypes") final ModelMap<BuildType> buildTypes,
                final List<ProductFlavorCombo<ProductFlavor>> flavorCombos,
                @Path("android.sources") final ModelMap<FunctionalSourceSet> sources,
                final AndroidComponentSpec spec) {
            if (flavorCombos.isEmpty()) {
                flavorCombos.add(new ProductFlavorCombo<>());
            }

            for (final BuildType buildType : buildTypes.values()) {
                for (final ProductFlavorCombo<ProductFlavor> flavorCombo : flavorCombos) {
                    binaries.create(getBinaryName(buildType, flavorCombo),
                            androidBinary -> {
                                AndroidBinaryInternal binary = (AndroidBinaryInternal) androidBinary;
                                binary.setBuildType(buildType);
                                binary.setProductFlavors(flavorCombo.getFlavorList());

                                sourceBinary(binary, sources, "main");
                                sourceBinary(binary, sources, buildType.getName());
                                for (ProductFlavor flavor : flavorCombo.getFlavorList()) {
                                    sourceBinary(binary, sources, flavor.getName());
                                }
                                if (flavorCombo.getFlavorList().size() > 1) {
                                    sourceBinary(binary, sources, flavorCombo.getName());
                                }
                            });
                }
            }
        }

        /**
         * Add sources to the specified binary.
         */
        private static void sourceBinary(
                BinarySpec binary,
                ModelMap<FunctionalSourceSet> projectSourceSet,
                final String sourceSetName) {
            FunctionalSourceSet sourceSet = projectSourceSet.get(sourceSetName);
            if (sourceSet != null) {
                binary.getInputs().addAll(sourceSet.values());
            }
        }

        private static String getBinaryName(BuildType buildType, ProductFlavorCombo flavorCombo) {
            if (flavorCombo.getFlavorList().isEmpty()) {
                return buildType.getName();
            } else {
                return flavorCombo.getName() + StringHelper.capitalize(buildType.getName());
            }

        }
    }
}
