package com.android.tools.build.test.multidevice;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.utils.ILogger;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A device provider that uses the device pool.
 *
 * <p>It has two modes of operation:
 * <ol>
 *     <li>Reserve and return any single available device.</li>
 *     <li>Return the devices as specified in the comma-separated com.android.test.devicepool.serial
 *         list. The devices should have already been reserved.</li>
 * </ol>
 * {@link com.android.tools.build.test.multidevice.DevicePoolPlugin}
 */
public class AdbPoolDeviceProvider extends DeviceProvider {

    private final DeviceProvider deviceProvider;

    /** Comma separated list of devices to be returned. {@link AdbPoolDeviceProvider}. */
    @Nullable
    private final String serialsString;

    @NonNull
    private final String projectPath;

    @Nullable
    private String serial = null;

    public AdbPoolDeviceProvider(@NonNull Project project) {
        File adb = new File(System.getenv("ANDROID_HOME") + "/platform-tools/adb");
        ILogger logger = new LoggerWrapper(project.getLogger());
        deviceProvider = new ConnectedDeviceProvider(adb, 40000, logger);
        Object serialProperty = project.getProperties().get("com.android.test.devicepool.serial");
        serialsString = serialProperty == null ? null : serialProperty.toString();
        projectPath = project.getProjectDir().getAbsolutePath();
    }

    @Override
    @NonNull
    public String getName() {
        return "devicePool";
    }

    @Override
    public void init() throws DeviceException {
        deviceProvider.init();
    }

    @Override
    public void terminate() throws DeviceException {
        deviceProvider.terminate();
        if (serial != null) {
            try {
                DevicePoolClient.returnDevices(ImmutableList.of(serial), projectPath);
            } catch (IOException e) {
                throw new DeviceException(e);
            }
            serial = null;
        }
    }

    @Override
    @NonNull
    public List<? extends DeviceConnector> getDevices() {

        List<? extends DeviceConnector> allDevices = deviceProvider.getDevices();

        if (!Strings.isNullOrEmpty(serialsString)) {
            // Devices have already been reserved.
            ImmutableList.Builder<DeviceConnector> builder = ImmutableList.builder();
            Set<String> filteredSerials = ImmutableSet.copyOf(
                    Splitter.on(',').split(serialsString));
            for (DeviceConnector connector : allDevices) {
                if (filteredSerials.contains(connector.getSerialNumber())) {
                    builder.add(connector);
                }
            }
            return builder.build();

        } else {
            List<String> candidateSerials = new ArrayList<>();
            for (DeviceConnector connector : allDevices) {
                // Only use an L MR1 or M device by default.
                if (connector.getApiLevel() >= 22 && connector.getApiCodeName() == null) {
                    candidateSerials.add(connector.getSerialNumber());
                }
            }
            try {
                serial = DevicePoolClient.reserveDevice(candidateSerials, projectPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (DeviceConnector connector : allDevices) {
                if (connector.getSerialNumber().equals(serial)) {
                    return ImmutableList.of(connector);
                }
            }

            throw new RuntimeException(
                    "Tried to find device with serial " + serial + " but had disappeared.");
        }


    }

    @Override
    public int getTimeoutInMs() {
        return 0;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

}