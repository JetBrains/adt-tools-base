package com.android.tools.build.test.multidevice;

import com.android.build.gradle.managed.AndroidConfig;
import com.android.builder.testing.api.DeviceProvider;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;

import java.util.Collections;

/** @see DevicePoolPlugin */
public class DevicePoolComponentModelPlugin extends RuleSource {
    @Mutate
    public static void addDeviceProvider(AndroidConfig config, Project project) {
        config.setDeviceProviders(
                Collections.singletonList(new AdbPoolDeviceProvider(project)));
    }
}