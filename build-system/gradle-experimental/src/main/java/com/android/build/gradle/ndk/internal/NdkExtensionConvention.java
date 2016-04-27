package com.android.build.gradle.ndk.internal;

import com.android.build.gradle.internal.core.Toolchain;
import com.android.build.gradle.internal.ndk.Stl;
import com.android.build.gradle.managed.NdkConfig;
import com.google.common.primitives.Ints;

import org.gradle.api.InvalidUserDataException;

/**
 * Action to setup default values for NdkExtension.
 */
public class NdkExtensionConvention {

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

        if (ndkConfig.getStl().isEmpty()) {
            ndkConfig.setStl(Stl.DEFAULT.getId());
        } else {
            // Validate STL.  getById throws exception when STL is not valid.
            Stl.getById(ndkConfig.getStl());
        }

        if (ndkConfig.getPlatformVersion() != null
                && !ndkConfig.getPlatformVersion().startsWith("android-")
                && Ints.tryParse(ndkConfig.getPlatformVersion()) != null) {
            ndkConfig.setPlatformVersion("android-" + ndkConfig.getPlatformVersion());
        }
    }
}
