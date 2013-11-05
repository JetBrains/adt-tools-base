/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.log.LogReceiver;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A Device. It can be a physical device or an emulator.
 */
final class Device implements IDevice {
    private static final int INSTALL_TIMEOUT = 2*60*1000; //2min
    private static final int BATTERY_TIMEOUT = 2*1000; //2 seconds
    private static final int GETPROP_TIMEOUT = 2*1000; //2 seconds

    /** Emulator Serial Number regexp. */
    static final String RE_EMULATOR_SN = "emulator-(\\d+)"; //$NON-NLS-1$

    /** Serial number of the device */
    private String mSerialNumber = null;

    /** Name of the AVD */
    private String mAvdName = null;

    /** State of the device. */
    private DeviceState mState = null;

    /** Device properties. */
    private final Map<String, String> mProperties = new HashMap<String, String>();
    private final Map<String, String> mMountPoints = new HashMap<String, String>();

    @GuardedBy("mClients")
    private final List<Client> mClients = new ArrayList<Client>();

    /** Maps pid's of clients in {@link #mClients} to their package name. */
    private final Map<Integer, String> mClientInfo = new ConcurrentHashMap<Integer, String>();

    private DeviceMonitor mMonitor;

    private static final String LOG_TAG = "Device";
    private static final char SEPARATOR = '-';
    private static final String UNKNOWN_PACKAGE = "";   //$NON-NLS-1$

    /**
     * Socket for the connection monitoring client connection/disconnection.
     */
    private SocketChannel mSocketChannel;

    private boolean mArePropertiesSet = false;

    private Integer mLastBatteryLevel = null;
    private long mLastBatteryCheckTime = 0;

    /** Path to the screen recorder binary on the device. */
    private static final String SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord";

    /** Flag indicating whether the device has the screen recorder binary. */
    private Boolean mHasScreenRecorder;

    private int mApiLevel;
    private String mName;

    /**
     * Output receiver for "pm install package.apk" command line.
     */
    private static final class InstallReceiver extends MultiLineReceiver {

        private static final String SUCCESS_OUTPUT = "Success"; //$NON-NLS-1$
        private static final Pattern FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(.*)\\]"); //$NON-NLS-1$

        private String mErrorMessage = null;

        public InstallReceiver() {
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (!line.isEmpty()) {
                    if (line.startsWith(SUCCESS_OUTPUT)) {
                        mErrorMessage = null;
                    } else {
                        Matcher m = FAILURE_PATTERN.matcher(line);
                        if (m.matches()) {
                            mErrorMessage = m.group(1);
                        } else {
                            mErrorMessage = "Unknown failure";
                        }
                    }
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        public String getErrorMessage() {
            return mErrorMessage;
        }
    }

    /**
     * Output receiver for "dumpsys battery" command line.
     */
    private static final class BatteryReceiver extends MultiLineReceiver {
        private static final Pattern BATTERY_LEVEL = Pattern.compile("\\s*level: (\\d+)");
        private static final Pattern SCALE = Pattern.compile("\\s*scale: (\\d+)");

        private Integer mBatteryLevel = null;
        private Integer mBatteryScale = null;

        /**
         * Get the parsed percent battery level.
         * @return
         */
        public Integer getBatteryLevel() {
            if (mBatteryLevel != null && mBatteryScale != null) {
                return (mBatteryLevel * 100) / mBatteryScale;
            }
            return null;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher batteryMatch = BATTERY_LEVEL.matcher(line);
                if (batteryMatch.matches()) {
                    try {
                        mBatteryLevel = Integer.parseInt(batteryMatch.group(1));
                    } catch (NumberFormatException e) {
                        Log.w(LOG_TAG, String.format("Failed to parse %s as an integer",
                                batteryMatch.group(1)));
                    }
                }
                Matcher scaleMatch = SCALE.matcher(line);
                if (scaleMatch.matches()) {
                    try {
                        mBatteryScale = Integer.parseInt(scaleMatch.group(1));
                    } catch (NumberFormatException e) {
                        Log.w(LOG_TAG, String.format("Failed to parse %s as an integer",
                                batteryMatch.group(1)));
                    }
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * Output receiver for "cat /sys/class/power_supply/.../capacity" command line.
     */
    static final class SysFsBatteryLevelReceiver extends MultiLineReceiver {

        private static final Pattern BATTERY_LEVEL = Pattern.compile("^(\\d+)[.\\s]*");
        private Integer mBatteryLevel = null;

        /**
         * Get the parsed battery level.
         * @return battery level or <code>null</code> if it cannot be determined
         */
        @Nullable
        public Integer getBatteryLevel() {
            return mBatteryLevel;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher batteryMatch = BATTERY_LEVEL.matcher(line);
                if (batteryMatch.matches()) {
                    if (mBatteryLevel == null) {
                        mBatteryLevel = Integer.parseInt(batteryMatch.group(1));
                    } else {
                        // multiple matches, check if they are different
                        Integer tmpLevel = Integer.parseInt(batteryMatch.group(1));
                        if (!mBatteryLevel.equals(tmpLevel)) {
                            Log.w(LOG_TAG, String.format(
                                    "Multiple lines matched with different value; " +
                                    "Original: %s, Current: %s (keeping original)",
                                    mBatteryLevel.toString(), tmpLevel.toString()));
                        }
                    }
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSerialNumber()
     */
    @Override
    public String getSerialNumber() {
        return mSerialNumber;
    }

    @Override
    public String getAvdName() {
        return mAvdName;
    }

    /**
     * Sets the name of the AVD
     */
    void setAvdName(String avdName) {
        if (!isEmulator()) {
            throw new IllegalArgumentException(
                    "Cannot set the AVD name of the device is not an emulator");
        }

        mAvdName = avdName;
    }

    @Override
    public String getName() {
        if (mName != null) {
            return mName;
        }

        if (isOnline()) {
            // cache name only if device is online
            mName = constructName();
            return mName;
        } else {
            return constructName();
        }
    }

    private String constructName() {
        if (isEmulator()) {
            String avdName = getAvdName();
            if (avdName != null) {
                return String.format("%s [%s]", avdName, getSerialNumber());
            } else {
                return getSerialNumber();
            }
        } else {
            String manufacturer = null;
            String model = null;

            try {
                manufacturer = cleanupStringForDisplay(
                    getPropertyCacheOrSync(PROP_DEVICE_MANUFACTURER));
                model = cleanupStringForDisplay(
                    getPropertyCacheOrSync(PROP_DEVICE_MODEL));
            } catch (Exception e) {
                // If there are exceptions thrown while attempting to get these properties,
                // we can just use the serial number, so ignore these exceptions.
            }

            StringBuilder sb = new StringBuilder(20);

            if (manufacturer != null) {
                sb.append(manufacturer);
                sb.append(SEPARATOR);
            }

            if (model != null) {
                sb.append(model);
                sb.append(SEPARATOR);
            }

            sb.append(getSerialNumber());
            return sb.toString();
        }
    }

    private String cleanupStringForDisplay(String s) {
        if (s == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getState()
     */
    @Override
    public DeviceState getState() {
        return mState;
    }

    /**
     * Changes the state of the device.
     */
    void setState(DeviceState state) {
        mState = state;
    }


    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperties()
     */
    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(mProperties);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getPropertyCount()
     */
    @Override
    public int getPropertyCount() {
        return mProperties.size();
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperty(java.lang.String)
     */
    @Override
    public String getProperty(String name) {
        return mProperties.get(name);
    }

    @Override
    public boolean arePropertiesSet() {
        return mArePropertiesSet;
    }

    @Override
    public String getPropertyCacheOrSync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        if (mArePropertiesSet) {
            return getProperty(name);
        } else {
            return getPropertySync(name);
        }
    }

    @Override
    public String getPropertySync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        executeShellCommand(String.format("getprop '%s'", name), receiver, GETPROP_TIMEOUT);
        try {
            latch.await(GETPROP_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }

        String value = receiver.getOutput().trim();
        if (value.isEmpty()) {
            return null;
        }

        return value;
    }

    @Override
    public boolean supportsFeature(Feature feature) {
        switch (feature) {
            case SCREEN_RECORD:
                if (getApiLevel() < 19) {
                    return false;
                }
                if (mHasScreenRecorder == null) {
                    mHasScreenRecorder = hasBinary(SCREEN_RECORDER_DEVICE_PATH);
                }
                return mHasScreenRecorder;
            case PROCSTATS:
                return getApiLevel() >= 19;
            default:
                return false;
        }
    }

    private int getApiLevel() {
        if (mApiLevel > 0) {
            return mApiLevel;
        }

        try {
            mApiLevel = Integer.parseInt(getPropertyCacheOrSync(PROP_BUILD_API_LEVEL));
            return mApiLevel;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean hasBinary(String path) {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            executeShellCommand("ls " + path, receiver);
        } catch (Exception e) {
            return false;
        }

        try {
            latch.await(GETPROP_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }

        String value = receiver.getOutput().trim();
        return !value.endsWith("No such file or directory");
    }

    @Override
    public String getMountPoint(String name) {
        return mMountPoints.get(name);
    }


    @Override
    public String toString() {
        return mSerialNumber;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOnline()
     */
    @Override
    public boolean isOnline() {
        return mState == DeviceState.ONLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isEmulator()
     */
    @Override
    public boolean isEmulator() {
        return mSerialNumber.matches(RE_EMULATOR_SN);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOffline()
     */
    @Override
    public boolean isOffline() {
        return mState == DeviceState.OFFLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isBootLoader()
     */
    @Override
    public boolean isBootLoader() {
        return mState == DeviceState.BOOTLOADER;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSyncService()
     */
    @Override
    public SyncService getSyncService()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        SyncService syncService = new SyncService(AndroidDebugBridge.getSocketAddress(), this);
        if (syncService.openSync()) {
            return syncService;
         }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getFileListingService()
     */
    @Override
    public FileListingService getFileListingService() {
        return new FileListingService(this);
    }

    @Override
    public RawImage getScreenshot()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this);
    }

    @Override
    public void startScreenRecorder(String remoteFilePath, ScreenRecorderOptions options,
            IShellOutputReceiver receiver) throws TimeoutException, AdbCommandRejectedException,
            IOException, ShellCommandUnresponsiveException {
        executeShellCommand(getScreenRecorderCommand(remoteFilePath, options), receiver, 0, null);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static String getScreenRecorderCommand(@NonNull String remoteFilePath,
            @NonNull ScreenRecorderOptions options) {
        StringBuilder sb = new StringBuilder();

        sb.append("screenrecord");
        sb.append(' ');

        if (options.width > 0 && options.height > 0) {
            sb.append("--size ");
            sb.append(options.width);
            sb.append('x');
            sb.append(options.height);
            sb.append(' ');
        }

        if (options.bitrateMbps > 0) {
            sb.append("--bit-rate ");
            sb.append(options.bitrateMbps * 1000000);
            sb.append(' ');
        }

        if (options.timeLimit > 0) {
            sb.append("--time-limit ");
            long seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits);
            if (seconds > 180) {
                seconds = 180;
            }
            sb.append(seconds);
            sb.append(' ');
        }

        sb.append(remoteFilePath);

        return sb.toString();
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
                receiver, DdmPreferences.getTimeOut());
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            int maxTimeToOutputResponse)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
                receiver, maxTimeToOutputResponse);
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
                receiver, maxTimeToOutputResponse, maxTimeUnits);
    }

    @Override
    public void runEventLogService(LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runEventLogService(AndroidDebugBridge.getSocketAddress(), this, receiver);
    }

    @Override
    public void runLogService(String logname, LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runLogService(AndroidDebugBridge.getSocketAddress(), this, logname, receiver);
    }

    @Override
    public void createForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("tcp:%d", remotePort));   //$NON-NLS-1$
    }

    @Override
    public void createForward(int localPort, String remoteSocketName,
            DeviceUnixSocketNamespace namespace) throws TimeoutException,
            AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("%s:%s", namespace.getType(), remoteSocketName));   //$NON-NLS-1$
    }

    @Override
    public void removeForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("tcp:%d", remotePort));   //$NON-NLS-1$
    }

    @Override
    public void removeForward(int localPort, String remoteSocketName,
            DeviceUnixSocketNamespace namespace) throws TimeoutException,
            AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("%s:%s", namespace.getType(), remoteSocketName));   //$NON-NLS-1$
    }

    Device(DeviceMonitor monitor, String serialNumber, DeviceState deviceState) {
        mMonitor = monitor;
        mSerialNumber = serialNumber;
        mState = deviceState;
    }

    DeviceMonitor getMonitor() {
        return mMonitor;
    }

    @Override
    public boolean hasClients() {
        synchronized (mClients) {
            return !mClients.isEmpty();
        }
    }

    @Override
    public Client[] getClients() {
        synchronized (mClients) {
            return mClients.toArray(new Client[mClients.size()]);
        }
    }

    @Override
    public Client getClient(String applicationName) {
        synchronized (mClients) {
            for (Client c : mClients) {
                if (applicationName.equals(c.getClientData().getClientDescription())) {
                    return c;
                }
            }
        }

        return null;
    }

    void addClient(Client client) {
        synchronized (mClients) {
            mClients.add(client);
        }

        addClientInfo(client);
    }

    List<Client> getClientList() {
        return mClients;
    }

    void clearClientList() {
        synchronized (mClients) {
            mClients.clear();
        }

        clearClientInfo();
    }

    /**
     * Removes a {@link Client} from the list.
     * @param client the client to remove.
     * @param notify Whether or not to notify the listeners of a change.
     */
    void removeClient(Client client, boolean notify) {
        mMonitor.addPortToAvailableList(client.getDebuggerListenPort());
        synchronized (mClients) {
            mClients.remove(client);
        }
        if (notify) {
            mMonitor.getServer().deviceChanged(this, CHANGE_CLIENT_LIST);
        }

        removeClientInfo(client);
    }

    /**
     * Sets the client monitoring socket.
     * @param socketChannel the sockets
     */
    void setClientMonitoringSocket(SocketChannel socketChannel) {
        mSocketChannel = socketChannel;
    }

    /**
     * Returns the client monitoring socket.
     */
    SocketChannel getClientMonitoringSocket() {
        return mSocketChannel;
    }

    void update(int changeMask) {
        if ((changeMask & CHANGE_BUILD_INFO) != 0) {
            mArePropertiesSet = true;
        }
        mMonitor.getServer().deviceChanged(this, changeMask);
    }

    void update(Client client, int changeMask) {
        mMonitor.getServer().clientChanged(client, changeMask);
        updateClientInfo(client, changeMask);
    }

    void addProperty(String label, String value) {
        mProperties.put(label, value);
    }

    void setMountingPoint(String name, String value) {
        mMountPoints.put(name, value);
    }

    private void addClientInfo(Client client) {
        ClientData cd = client.getClientData();
        setClientInfo(cd.getPid(), cd.getClientDescription());
    }

    private void updateClientInfo(Client client, int changeMask) {
        if ((changeMask & Client.CHANGE_NAME) == Client.CHANGE_NAME) {
            addClientInfo(client);
        }
    }

    private void removeClientInfo(Client client) {
        int pid = client.getClientData().getPid();
        mClientInfo.remove(pid);
    }

    private void clearClientInfo() {
        mClientInfo.clear();
    }

    private void setClientInfo(int pid, String pkgName) {
        if (pkgName == null) {
            pkgName = UNKNOWN_PACKAGE;
        }

        mClientInfo.put(pid, pkgName);
    }

    @Override
    public String getClientName(int pid) {
        String pkgName = mClientInfo.get(pid);
        return pkgName == null ? UNKNOWN_PACKAGE : pkgName;
    }

    @Override
    public void pushFile(String local, String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String targetFileName = getFileName(local);

            Log.d(targetFileName, String.format("Uploading %1$s onto device '%2$s'",
                    targetFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(local, remote, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    @Override
    public void pullFile(String remote, String local)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String targetFileName = getFileName(remote);

            Log.d(targetFileName, String.format("Downloading %1$s from device '%2$s'",
                    targetFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Downloading file from device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    @Override
    public String installPackage(String packageFilePath, boolean reinstall, String... extraArgs)
            throws InstallException {
        try {
            String remoteFilePath = syncPackageToDevice(packageFilePath);
            String result = installRemotePackage(remoteFilePath, reinstall, extraArgs);
            removeRemotePackage(remoteFilePath);
            return result;
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (SyncException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String syncPackageToDevice(String localFilePath)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String packageFileName = getFileName(localFilePath);
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName); //$NON-NLS-1$

            Log.d(packageFileName, String.format("Uploading %1$s onto device '%2$s'",
                    packageFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    /**
     * Helper method to retrieve the file name given a local file path
     * @param filePath full directory path to file
     * @return {@link String} file name
     */
    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    @Override
    public String installRemotePackage(String remoteFilePath, boolean reinstall,
            String... extraArgs) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            StringBuilder optionString = new StringBuilder();
            if (reinstall) {
                optionString.append("-r ");
            }
            for (String arg : extraArgs) {
                optionString.append(arg);
                optionString.append(' ');
            }
            String cmd = String.format("pm install %1$s \"%2$s\"", optionString.toString(),
                    remoteFilePath);
            executeShellCommand(cmd, receiver, INSTALL_TIMEOUT);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void removeRemotePackage(String remoteFilePath) throws InstallException {
        try {
            executeShellCommand(String.format("rm \"%1$s\"", remoteFilePath),
                    new NullOutputReceiver(), INSTALL_TIMEOUT);
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String uninstallPackage(String packageName) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand("pm uninstall " + packageName, receiver, INSTALL_TIMEOUT);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#reboot()
     */
    @Override
    public void reboot(String into)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this);
    }

    @Override
    public Integer getBatteryLevel() throws TimeoutException, AdbCommandRejectedException,
            IOException, ShellCommandUnresponsiveException {
        // use default of 5 minutes
        return getBatteryLevel(5 * 60 * 1000);
    }

    @Override
    public Integer getBatteryLevel(long freshnessMs) throws TimeoutException,
            AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {
        if (mLastBatteryLevel != null
                && mLastBatteryCheckTime > (System.currentTimeMillis() - freshnessMs)) {
            return mLastBatteryLevel;
        }
        // first try to get it from sysfs
        SysFsBatteryLevelReceiver sysBattReceiver = new SysFsBatteryLevelReceiver();
        executeShellCommand("cat /sys/class/power_supply/*/capacity",
                sysBattReceiver, BATTERY_TIMEOUT);
        mLastBatteryLevel = sysBattReceiver.getBatteryLevel();
        if (mLastBatteryLevel != null) {
            mLastBatteryCheckTime = System.currentTimeMillis();
            return mLastBatteryLevel;
        }
        // now try dumpsys
        BatteryReceiver receiver = new BatteryReceiver();
        executeShellCommand("dumpsys battery", receiver, BATTERY_TIMEOUT);
        mLastBatteryLevel = receiver.getBatteryLevel();
        mLastBatteryCheckTime = System.currentTimeMillis();
        return mLastBatteryLevel;
    }
}
