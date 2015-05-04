package com.android.build.gradle.ndk.internal;

import com.android.build.gradle.managed.ManagedString;
import com.android.build.gradle.ndk.managed.NdkConfig;

import org.gradle.api.Action;
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
    public static void setExtensionDefault(NdkConfig ndkConfig) {
        if (!ndkConfig.getCompileSdkVersion().isEmpty()) {
            try {
                int version = Integer.parseInt(ndkConfig.getCompileSdkVersion());
                ndkConfig.setCompileSdkVersion("android-" + ndkConfig.getCompileSdkVersion());
            } catch (NumberFormatException ignored) {
            }
        }


        if (ndkConfig.getToolchain().isEmpty()) {
            ndkConfig.setToolchain(DEFAULT_TOOLCHAIN);
        } else {
            if (!ndkConfig.getToolchain().equals("gcc") &&
                    !ndkConfig.getToolchain().equals("clang")) {
                throw new InvalidUserDataException(String.format(
                        "Invalid toolchain '%s'.  Supported toolchains are 'gcc' and 'clang'.",
                        ndkConfig.getToolchain()));
            }
        }

        if (ndkConfig.getToolchainVersion().isEmpty()) {
            ndkConfig.setToolchainVersion(DEFAULT_TOOLCHAIN_VERSION);
        }

        ndkConfig.getCFilePattern().getIncludes().create(
                new Action<ManagedString>() {
            @Override
            public void execute(ManagedString managedString) {
                        managedString.setValue("**/*.c");
                }
            });

        ndkConfig.getCppFilePattern().getIncludes().create(
                new Action<ManagedString>() {
                    @Override
                    public void execute(ManagedString managedString) {
                        managedString.setValue("**/*.cpp");
                    }
                });
        ndkConfig.getCppFilePattern().getIncludes().create(
                new Action<ManagedString>() {
            @Override
            public void execute(ManagedString managedString) {
                        managedString.setValue("**/*.cc");
                }
            });

        if (ndkConfig.getStl().isEmpty()) {
            ndkConfig.setStl(DEFAULT_STL);
        } else {
            StlConfiguration.checkStl(ndkConfig.getStl());
        }
    }
}
