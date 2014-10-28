package com.android.build.gradle.ndk.internal;


import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.ndk.NdkExtension;
import com.android.builder.core.BuilderConstants;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

/**
 * Action to setup default values for NdkExtension.
 */
public class NdkExtensionConventionAction implements Action<Project> {

    private static final String DEFAULT_TOOLCHAIN = "gcc";

    private static final String DEFAULT_GCC_VERSION = "4.6";

    private static final String DEFAULT_GCC_L_VERSION = "4.9";  // Default for r10 is 4.9.

    private static final String DEFAULT_CLANG_VERSION = "3.4";

    private static final String DEFAULT_STL = "system";

    private NdkExtension extension;

    public NdkExtensionConventionAction(NdkExtension extension) {
        this.extension = extension;
    }

    @Override
    public void execute(Project project) {
        if (extension.getModuleName() == null || extension.getModuleName().isEmpty()) {
            throw new InvalidUserDataException("moduleName must be set for Android NDK plugin.");
        }

        if (extension.getCompileSdkVersion() == null) {
            // Retrieve compileSdkVersion from Android plugin if it is not set for the NDK plugin.
            BasePlugin androidPlugin = project.getPlugins().findPlugin(AppPlugin.class);
            if (androidPlugin == null) {
                androidPlugin = project.getPlugins().findPlugin(LibraryPlugin.class);
            }

            if (androidPlugin != null) {
                extension.setCompileSdkVersion(androidPlugin.getExtension().getCompileSdkVersion());
            } else {
                throw new InvalidUserDataException(
                        "compileSdkVersion must be set for Android NDK plugin.");
            }
        }
        setExtensionDefault(extension);
    }

    public static void setExtensionDefault(NdkExtension extension) {
        if (extension.getToolchain() == null) {
            extension.setToolchain(DEFAULT_TOOLCHAIN);
        }

        if (extension.getToolchainVersion() == null) {
            // Supports gcc and clang.
            extension.setToolchainVersion(
                    extension.getToolchain().equals("gcc")
                            ? (extension.getCompileSdkVersion().equals("android-L")
                                    ? DEFAULT_GCC_L_VERSION
                                    : DEFAULT_GCC_VERSION)
                            : DEFAULT_CLANG_VERSION);
        }

        if (extension.getCFilePattern().getIncludes().isEmpty()) {
            extension.getCFilePattern().include("**/*.c");
        }

        if (extension.getCppFilePattern().getIncludes().isEmpty()) {
            extension.getCppFilePattern().include("**/*.cpp");
            extension.getCppFilePattern().include("**/*.cc");
        }

        if (extension.getStl() == null) {
            extension.setStl(DEFAULT_STL);
        } else {
            StlConfiguration.checkStl(extension.getStl());
        }
    }
}
