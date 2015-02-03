/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.log.LogReceiver;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 *  A Device. It can be a physical device or an emulator.
 */
public interface IDevice extends IShellEnabledDevice {

    public static final String PROP_BUILD_VERSION = "ro.build.version.release";
    public static final String PROP_BUILD_API_LEVEL = "ro.build.version.sdk";
    public static final String PROP_BUILD_CODENAME = "ro.build.version.codename";
    public static final String PROP_DEVICE_MODEL = "ro.product.model";
    public static final String PROP_DEVICE_MANUFACTURER = "ro.product.manufacturer";
    public static final String PROP_DEVICE_CPU_ABI_LIST = "ro.product.cpu.abilist";
    public static final String PROP_DEVICE_CPU_ABI = "ro.product.cpu.abi";
    public static final String PROP_DEVICE_CPU_ABI2 = "ro.product.cpu.abi2";
    public static final String PROP_BUILD_CHARACTERISTICS = "ro.build.characteristics";
    public static final String PROP_DEVICE_DENSITY = "ro.sf.lcd_density";
    public static final String PROP_DEVICE_LANGUAGE = "persist.sys.language";
    public static final String PROP_DEVICE_REGION = "persist.sys.country";

    public static final String PROP_DEBUGGABLE = "ro.debuggable";

    /** Serial number of the first connected emulator. */
    public static final String FIRST_EMULATOR_SN = "emulator-5554"; //$NON-NLS-1$
    /** Device change bit mask: {@link DeviceState} change. */
    public static final int CHANGE_STATE = 0x0001;
    /** Device change bit mask: {@link Client} list change. */
    public static final int CHANGE_CLIENT_LIST = 0x0002;
    /** Device change bit mask: build info change. */
    public static final int CHANGE_BUILD_INFO = 0x0004;

    /** Device level software features. */
    public enum Feature {
        SCREEN_RECORD,      // screen recorder available?
        PROCSTATS,          // procstats service (dumpsys procstats) available
    };

    /** Device level hardware features. */
    public enum HardwareFeature {
        WATCH("watch");                   // supports feature watch

        private final String mCharacteristic;

        private HardwareFeature(String characteristic) {
            mCharacteristic = characteristic;
        }

        public String getCharacteristic() {
            return mCharacteristic;
        }
    }

    /** @deprecated Use {@link #PROP_BUILD_API_LEVEL}. */
    @Deprecated
    public static final String PROP_BUILD_VERSION_NUMBER = PROP_BUILD_API_LEVEL;

    public static final String MNT_EXTERNAL_STORAGE = "EXTERNAL_STORAGE"; //$NON-NLS-1$
    public static final String MNT_ROOT = "ANDROID_ROOT"; //$NON-NLS-1$
    public static final String MNT_DATA = "ANDROID_DATA"; //$NON-NLS-1$

    /**
     * The state of a device.
     */
    public static enum DeviceState {
        BOOTLOADER("bootloader"), //$NON-NLS-1$
        OFFLINE("offline"), //$NON-NLS-1$
        ONLINE("device"), //$NON-NLS-1$
        RECOVERY("recovery"), //$NON-NLS-1$
        UNAUTHORIZED("unauthorized"); //$NON-NLS-1$

        private String mState;

        DeviceState(String state) {
            mState = state;
        }

        /**
         * Returns a {@link DeviceState} from the string returned by <code>adb devices</code>.
         *
         * @param state the device state.
         * @return a {@link DeviceState} object or <code>null</code> if the state is unknown.
         */
        @Nullable
        public static DeviceState getState(String state) {
            for (DeviceState deviceState : values()) {
                if (deviceState.mState.equals(state)) {
                    return deviceState;
                }
            }
            return null;
        }
    }

    /**
     * Namespace of a Unix Domain Socket created on the device.
     */
    public static enum DeviceUnixSocketNamespace {
        ABSTRACT("localabstract"),      //$NON-NLS-1$
        FILESYSTEM("localfilesystem"),  //$NON-NLS-1$
        RESERVED("localreserved");      //$NON-NLS-1$

        private String mType;

        private DeviceUnixSocketNamespace(String type) {
            mType = type;
        }

        String getType() {
            return mType;
        }
    }

    /** Returns the serial number of the device. */
    @NonNull
    public String getSerialNumber();

    /**
     * Returns the name of the AVD the emulator is running.
     * <p/>This is only valid if {@link #isEmulator()} returns true.
     * <p/>If the emulator is not running any AVD (for instance it's running from an Android source
     * tree build), this method will return "<code>&lt;build&gt;</code>".
     *
     * @return the name of the AVD or <code>null</code> if there isn't any.
     */
    @Nullable
    public String getAvdName();

    /**
     * Returns the state of the device.
     */
    public DeviceState getState();

    /**
     * Returns the cached device properties. It contains the whole output of 'getprop'
     *
     * @deprecated use {@link #getSystemProperty(String)} instead
     */
    @Deprecated
    public Map<String, String> getProperties();

    /**
     * Returns the number of property for this device.
     *
     * @deprecated implementation detail
     */
    @Deprecated
    public int getPropertyCount();

    /**
     * Convenience method that attempts to retrieve a property via
     * {@link #getSystemProperty(String)} with minimal wait time, and swallows exceptions.
     *
     * @param name the name of the value to return.
     * @return the value or <code>null</code> if the property value was not immediately available
     */
    @Nullable
    public String getProperty(@NonNull String name);

    /**
     * Returns <code>true></code> if properties have been cached
     */
    public boolean arePropertiesSet();

    /**
     * A variant of {@link #getProperty(String)} that will attempt to retrieve the given
     * property from device directly, without using cache.
     * This method should (only) be used for any volatile properties.
     *
     * @param name the name of the value to return.
     * @return the value or <code>null</code> if the property does not exist
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     *             given time.
     * @throws IOException in case of I/O error on the connection.
     * @deprecated use {@link #getSystemProperty(String)}
     */
    @Deprecated
    public String getPropertySync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException;

    /**
     * A combination of {@link #getProperty(String)} and {@link #getPropertySync(String)} that
     * will attempt to retrieve the property from cache. If not found, will synchronously
     * attempt to query device directly and repopulate the cache if successful.
     *
     * @param name the name of the value to return.
     * @return the value or <code>null</code> if the property does not exist
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     *             given time.
     * @throws IOException in case of I/O error on the connection.
     * @deprecated use {@link #getSystemProperty(String)} instead
     */
    @Deprecated
    public String getPropertyCacheOrSync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException;

    /** Returns whether this device supports the given software feature. */
    boolean supportsFeature(@NonNull Feature feature);

    /** Returns whether this device supports the given hardware feature. */
    boolean supportsFeature(@NonNull HardwareFeature feature);

    /**
     * Returns a mount point.
     *
     * @param name the name of the mount point to return
     *
     * @see #MNT_EXTERNAL_STORAGE
     * @see #MNT_ROOT
     * @see #MNT_DATA
     */
    public String getMountPoint(String name);

    /**
     * Returns if the device is ready.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#ONLINE}.
     */
    public boolean isOnline();

    /**
     * Returns <code>true</code> if the device is an emulator.
     */
    public boolean isEmulator();

    /**
     * Returns if the device is offline.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#OFFLINE}.
     */
    public boolean isOffline();

    /**
     * Returns if the device is in bootloader mode.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#BOOTLOADER}.
     */
    public boolean isBootLoader();

    /**
     * Returns whether the {@link Device} has {@link Client}s.
     */
    public boolean hasClients();

    /**
     * Returns the array of clients.
     */
    public Client[] getClients();

    /**
     * Returns a {@link Client} by its application name.
     *
     * @param applicationName the name of the application
     * @return the <code>Client</code> object or <code>null</code> if no match was found.
     */
    public Client getClient(String applicationName);

    /**
     * Returns a {@link SyncService} object to push / pull files to and from the device.
     *
     * @return <code>null</code> if the SyncService couldn't be created. This can happen if adb
     *            refuse to open the connection because the {@link IDevice} is invalid
     *            (or got disconnected).
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException if the connection with adb failed.
     */
    public SyncService getSyncService()
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Returns a {@link FileListingService} for this device.
     */
    public FileListingService getFileListingService();

    /**
     * Takes a screen shot of the device and returns it as a {@link RawImage}.
     *
     * @return the screenshot as a <code>RawImage</code> or <code>null</code> if something
     *            went wrong.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    RawImage getScreenshot() throws TimeoutException, AdbCommandRejectedException, IOException;

    RawImage getScreenshot(long timeout, TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Initiates screen recording on the device if the device supports {@link Feature#SCREEN_RECORD}.
     */
    public void startScreenRecorder(@NonNull String remoteFilePath,
            @NonNull ScreenRecorderOptions options, @NonNull IShellOutputReceiver receiver) throws
            TimeoutException, AdbCommandRejectedException, IOException,
            ShellCommandUnresponsiveException;

    /**
     * @deprecated Use {@link #executeShellCommand(String, IShellOutputReceiver, long, java.util.concurrent.TimeUnit)}.
     */
    @Deprecated
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            int maxTimeToOutputResponse)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException;

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>
     * <p/>This is similar to calling
     * <code>executeShellCommand(command, receiver, DdmPreferences.getTimeOut())</code>.
     *
     * @param command the shell command to execute
     * @param receiver the {@link IShellOutputReceiver} that will receives the output of the shell
     *            command
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output
     *            for a given time.
     * @throws IOException in case of I/O error on the connection.
     *
     * @see #executeShellCommand(String, IShellOutputReceiver, int)
     * @see DdmPreferences#getTimeOut()
     */
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException;

    /**
     * Runs the event log service and outputs the event log to the {@link LogReceiver}.
     * <p/>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the
     * timeout happens during setup. Once logs start being received, no timeout will occur as it's
     * not possible to detect a difference between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void runEventLogService(LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Runs the log service for the given log and outputs the log to the {@link LogReceiver}.
     * <p/>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     *
     * @param logname the logname of the log to read from.
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the
     *            timeout happens during setup. Once logs start being received, no timeout will
     *            occur as it's not possible to detect a difference between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void runLogService(String logname, LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Creates a port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @param remotePort the remote port.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void createForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Creates a port forwarding between a local TCP port and a remote Unix Domain Socket.
     *
     * @param localPort the local port to forward
     * @param remoteSocketName name of the unix domain socket created on the device
     * @param namespace namespace in which the unix domain socket was created
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void createForward(int localPort, String remoteSocketName,
            DeviceUnixSocketNamespace namespace)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Removes a port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @param remotePort the remote port.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void removeForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Removes an existing port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @param remoteSocketName the remote unix domain socket name.
     * @param namespace namespace in which the unix domain socket was created
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public void removeForward(int localPort, String remoteSocketName,
            DeviceUnixSocketNamespace namespace)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Returns the name of the client by pid or <code>null</code> if pid is unknown
     * @param pid the pid of the client.
     */
    public String getClientName(int pid);

    /**
     * Push a single file.
     * @param local the local filepath.
     * @param remote The remote filepath.
     *
     * @throws IOException in case of I/O error on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException if file could not be pushed
     */
    public void pushFile(String local, String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException;

    /**
     * Pulls a single file.
     *
     * @param remote the full path to the remote file
     * @param local The local destination.
     *
     * @throws IOException in case of an IO exception.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException in case of a sync exception.
     */
    public void pullFile(String remote, String local)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException;

    /**
     * Installs an Android application on device. This is a helper method that combines the
     * syncPackageToDevice, installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws InstallException if the installation fails.
     */
    public String installPackage(String packageFilePath, boolean reinstall, String... extraArgs)
            throws InstallException;

    /**
     * Installs an Android application made of serveral APK files (one main and 0..n split packages)
     *
     * @param apkFilePaths list of absolute file system path to files on local host to install
     * @param timeOutInMs
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @throws InstallException if the installation fails.
     */

    public void installPackages(List<String> apkFilePaths, int timeOutInMs,
            boolean reinstall, String... extraArgs) throws InstallException;
    /**
     * Pushes a file to device
     *
     * @param localFilePath the absolute path to file on local host
     * @return {@link String} destination path on device for file
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     * @throws SyncException if an error happens during the push of the package on the device.
     */
    public String syncPackageToDevice(String localFilePath)
            throws TimeoutException, AdbCommandRejectedException, IOException, SyncException;

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @throws InstallException if the installation fails.
     */
    public String installRemotePackage(String remoteFilePath, boolean reinstall,
            String... extraArgs) throws InstallException;

    /**
     * Removes a file from device.
     *
     * @param remoteFilePath path on device of file to remove
     * @throws InstallException if the installation fails.
     */
    public void removeRemotePackage(String remoteFilePath) throws InstallException;

    /**
     * Uninstalls an package from the device.
     *
     * @param packageName the Android application package name to uninstall
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws InstallException if the uninstallation fails.
     */
    public String uninstallPackage(String packageName) throws InstallException;

    /**
     * Reboot the device.
     *
     * @param into the bootloader name to reboot into, or null to just reboot the device.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException
     */
    public void reboot(String into)
            throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p/>
     * The battery level may be cached. Only queries the device for its
     * battery level if 5 minutes have expired since the last successful query.
     *
     * @return the battery level or <code>null</code> if it could not be retrieved
     * @deprecated use {@link #getBattery()}
     */
    @Deprecated
    public Integer getBatteryLevel() throws TimeoutException,
            AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException;

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p/>
     * The battery level may be cached. Only queries the device for its
     * battery level if <code>freshnessMs</code> ms have expired since the last successful query.
     *
     * @param freshnessMs
     * @return the battery level or <code>null</code> if it could not be retrieved
     * @throws ShellCommandUnresponsiveException
     * @deprecated use {@link #getBattery(long, TimeUnit))}
     */
    @Deprecated
    public Integer getBatteryLevel(long freshnessMs) throws TimeoutException,
            AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException;

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p/>
     * The battery level may be cached. Only queries the device for its
     * battery level if 5 minutes have expired since the last successful query.
     *
     * @return a {@link Future} that can be used to query the battery level. The Future will return
     * a {@link ExecutionException} if battery level could not be retrieved.
     */
    @NonNull
    public Future<Integer> getBattery();

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p/>
     * The battery level may be cached. Only queries the device for its
     * battery level if <code>freshnessTime</code> has expired since the last successful query.
     *
     * @param freshnessTime the desired recency of battery level
     * @param timeUnit the {@link TimeUnit} of freshnessTime
     * @return a {@link Future} that can be used to query the battery level. The Future will return
     * a {@link ExecutionException} if battery level could not be retrieved.
     */
    @NonNull
    public Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit);


    /**
     * Returns the ABIs supported by this device. The ABIs are sorted in preferred order, with the
     * first ABI being the most preferred.
     * @return the list of ABIs.
     */
    @NonNull
    public List<String> getAbis();

    /**
     * Returns the density bucket of the device screen.
     *
     * @return the density, or 0 if it's unknown.
     */
    public int getDensity();

    /**
     * Returns the user's language.
     *
     * @return the user's language, or null if it's unknown
     */
    public String getLanguage();

    /**
     * Returns the user's region.
     *
     * @return the user's region, or null if it's unknown
     */
    public String getRegion();
}
