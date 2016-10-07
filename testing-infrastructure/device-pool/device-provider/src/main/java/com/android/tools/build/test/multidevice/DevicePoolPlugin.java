package com.android.tools.build.test.multidevice;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceProvider;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;

import java.lang.reflect.Method;

/**
 * Allows the connected integration tests to be run in parallel.
 *
 * This plugin is automatically applied to all of the test projects, and registers a {@link
 * DeviceProvider} which will return one of the connected devices.
 *
 * The {@link AdbPoolDeviceProvider} block test execution until a device is available in the global
 * device pool, which is defined in buildSrc, and will return the device to the pool once {@link
 * DeviceProvider#terminate()} is called.
 *
 * The other way to reserve devices in the pool is to use the Adb test rule.
 */
public class DevicePoolPlugin implements Plugin<Project> {

    @Override
    public void apply(@NonNull  Project project) {
        PluginContainer plugins = project.getPlugins();
        if (plugins.hasPlugin("android") || plugins.hasPlugin("android-library") ||
                plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
            Object androidExtension = project.getExtensions().getByName("android");
            Class<?> clazz= androidExtension.getClass();
            try {
                Method m = clazz.getMethod("deviceProvider", DeviceProvider.class);
                m.invoke(androidExtension, new AdbPoolDeviceProvider(project));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (project.getPlugins().hasPlugin("com.android.model.application") ||
                project.getPlugins().hasPlugin("com.android.model.library")) {
            // Uses things only present in gradle-experimental, so we include it indirectly.
            project.getPluginManager().apply(DevicePoolComponentModelPlugin.class);

        } else {
            throw new GradleException(
                    "The android or android-library plugin has not been applied yet");
        }
    }
}