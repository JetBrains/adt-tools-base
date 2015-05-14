package com.android.build.gradle.ndk.internal;

import com.android.build.gradle.internal.core.Toolchain;
import com.android.build.gradle.managed.ManagedString;
import com.android.build.gradle.managed.NdkConfig;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;

/**
 * Action to setup default values for NdkExtension.
 */
public class NdkExtensionConvention {

    public static final String DEFAULT_STL = "system";

    /**
     * Validate the NdkExtension and provide default values.
     */
    public static void setExtensionDefault(NdkConfig ndkConfig) {
        if (ndkConfig.getToolchain().isEmpty()) {
            ndkConfig.setToolchain(Toolchain.getDefault().getName());
        } else {
            if (!ndkConfig.getToolchain().equals("gcc") &&
                    !ndkConfig.getToolchain().equals("clang")) {
                throw new InvalidUserDataException(String.format(
                        "Invalid toolchain '%s'.  Supported toolchains are 'gcc' and 'clang'.",
                        ndkConfig.getToolchain()));
            }
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
