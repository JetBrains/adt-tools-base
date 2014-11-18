package com.android.build.gradle.ndk.internal;


import com.android.build.gradle.ndk.NdkExtension;

import org.gradle.api.InvalidUserDataException;

/**
 * Action to setup default values for NdkExtension.
 */
public class NdkExtensionConvention {

    public static final String DEFAULT_TOOLCHAIN = "gcc";

    // Default toolchain version depends on the target ABI.  Setting it to "default" to allow
    // the version to be determined later.
    public static final String DEFAULT_TOOLCHAIN_VERSION = "default";

    public static final String DEFAULT_STL = "system";

    /**
     * Validate the NdkExtension and provide default values.
     */
    public static void setExtensionDefault(NdkExtension extension) {
        if (extension.getToolchain().isEmpty()) {
            extension.setToolchain(DEFAULT_TOOLCHAIN);
        } else {
            if (!extension.getToolchain().equals("gcc") &&
                    !extension.getToolchain().equals("clang")) {
                throw new InvalidUserDataException(String.format(
                        "Invalid toolchain '%s'.  Supported toolchains are 'gcc' and 'clang'.",
                        extension.getToolchain()));
            }
        }

        if (extension.getToolchainVersion().isEmpty()) {
            extension.setToolchainVersion(DEFAULT_TOOLCHAIN_VERSION);
        }

        if (extension.getCFilePattern().getIncludes().isEmpty()) {
            extension.getCFilePattern().include("**/*.c");
        }

        if (extension.getCppFilePattern().getIncludes().isEmpty()) {
            extension.getCppFilePattern().include("**/*.cpp");
            extension.getCppFilePattern().include("**/*.cc");
        }

        if (extension.getStl().isEmpty()) {
            extension.setStl(DEFAULT_STL);
        } else {
            StlConfiguration.checkStl(extension.getStl());
        }
    }
}
