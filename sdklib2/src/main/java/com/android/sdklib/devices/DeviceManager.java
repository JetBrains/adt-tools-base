/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.devices;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.ILogger;

import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Manager class for interacting with {@link Device}s within the SDK
 */
public class DeviceManager {

    private static final String  DEVICE_PROFILES_PROP = "DeviceProfiles";
    private static final Pattern PATH_PROPERTY_PATTERN =
        Pattern.compile("^" + PkgProps.EXTRA_PATH + "=" + DEVICE_PROFILES_PROP + "$");
    private ILogger mLog;
    private List<Device> mVendorDevices;
    private List<Device> mUserDevices;
    private List<Device> mDefaultDevices;
    private final Object mLock = new Object();
    private final List<DevicesChangedListener> sListeners =
                                        new ArrayList<DevicesChangedListener>();
    private final String mOsSdkPath;

    /** getDevices() flag to list user devices. */
    public static final int USER_DEVICES    = 1;
    /** getDevices() flag to list default devices. */
    public static final int DEFAULT_DEVICES = 2;
    /** getDevices() flag to list vendor devices. */
    public static final int VENDOR_DEVICES  = 4;
    /** getDevices() flag to list all devices. */
    public static final int ALL_DEVICES  = USER_DEVICES | DEFAULT_DEVICES | VENDOR_DEVICES;

    public static enum DeviceStatus {
        /**
         * The device exists unchanged from the given configuration
         */
        EXISTS,
        /**
         * A device exists with the given name and manufacturer, but has a different configuration
         */
        CHANGED,
        /**
         * There is no device with the given name and manufacturer
         */
        MISSING;
    }

    /**
     * Creates a new instance of DeviceManager.
     *
     * @param osSdkPath Path to the current SDK. If null or invalid, vendor devices are ignored.
     * @param log SDK logger instance. Should be non-null.
     */
    public static DeviceManager createInstance(@Nullable String osSdkPath, @NonNull ILogger log) {
        // TODO consider using a cache and reusing the same instance of the device manager
        // for the same manager/log combo.
        return new DeviceManager(osSdkPath, log);
    }

    /**
     * Creates a new instance of DeviceManager.
     *
     * @param osSdkPath Path to the current SDK. If null or invalid, vendor devices are ignored.
     * @param log SDK logger instance. Should be non-null.
     */
    private DeviceManager(@Nullable String osSdkPath, @NonNull ILogger log) {
        mOsSdkPath = osSdkPath;
        mLog = log;
    }

    /**
     * Interface implemented by objects which want to know when changes occur to the {@link Device}
     * lists.
     */
    public static interface DevicesChangedListener {
        /**
         * Called after one of the {@link Device} lists has been updated.
         */
        public void onDevicesChanged();
    }

    /**
     * Register a listener to be notified when the device lists are modified.
     *
     * @param listener The listener to add. Ignored if already registered.
     */
    public void registerListener(DevicesChangedListener listener) {
        if (listener != null) {
            synchronized (sListeners) {
                if (!sListeners.contains(listener)) {
                    sListeners.add(listener);
                }
            }
        }
    }

    /**
     * Removes a listener from the notification list such that it will no longer receive
     * notifications when modifications to the {@link Device} list occur.
     *
     * @param listener The listener to remove.
     */
    public boolean unregisterListener(DevicesChangedListener listener) {
        synchronized (sListeners) {
            return sListeners.remove(listener);
        }
    }

    public DeviceStatus getDeviceStatus(String name, String manufacturer, int hashCode) {
        Device d = getDevice(name, manufacturer);
        if (d == null) {
            return DeviceStatus.MISSING;
        } else {
            return d.hashCode() == hashCode ? DeviceStatus.EXISTS : DeviceStatus.CHANGED;
        }
    }

    public Device getDevice(String name, String manufacturer) {
        initDevicesLists();
        for (List<?> devices :
                new List<?>[] { mUserDevices, mDefaultDevices, mVendorDevices } ) {
            if (devices != null) {
                @SuppressWarnings("unchecked") List<Device> devicesList = (List<Device>) devices;
                for (Device d : devicesList) {
                    if (d.getName().equals(name) && d.getManufacturer().equals(manufacturer)) {
                        return d;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the known {@link Device} list.
     *
     * @param deviceFilter A combination of USER_DEVICES, VENDOR_DEVICES and DEFAULT_DEVICES
     *                     or the constant ALL_DEVICES.
     * @return A copy of the list of {@link Device}s. Can be empty but not null.
     */
    public List<Device> getDevices(int deviceFilter) {
        initDevicesLists();
        List<Device> devices = new ArrayList<Device>();
        if (mUserDevices != null && (deviceFilter & USER_DEVICES) != 0) {
            devices.addAll(mUserDevices);
        }
        if (mDefaultDevices != null && (deviceFilter & DEFAULT_DEVICES) != 0) {
            devices.addAll(mDefaultDevices);
        }
        if (mVendorDevices != null && (deviceFilter & VENDOR_DEVICES) != 0) {
            devices.addAll(mVendorDevices);
        }
        return Collections.unmodifiableList(devices);
    }

    private void initDevicesLists() {
        boolean changed = initDefaultDevices();
        changed |= initVendorDevices();
        changed |= initUserDevices();
        if (changed) {
            notifyListeners();
        }
    }

    /**
     * Initializes the {@link Device}s packaged with the SDK.
     * @return True if the list has changed.
     */
    private boolean initDefaultDevices() {
        synchronized (mLock) {
            if (mDefaultDevices == null) {
                try {
                    mDefaultDevices = DeviceParser.parse(
                            DeviceManager.class.getResourceAsStream(SdkConstants.FN_DEVICES_XML));
                    return true;
                } catch (IllegalStateException e) {
                    // The device builders can throw IllegalStateExceptions if
                    // build gets called before everything is properly setup
                    mLog.error(e, null);
                    mDefaultDevices = new ArrayList<Device>();
                } catch (Exception e) {
                    mLog.error(null, "Error reading default devices");
                    mDefaultDevices = new ArrayList<Device>();
                }
            }
        }
        return false;
    }

    /**
     * Initializes all vendor-provided {@link Device}s.
     * @return True if the list has changed.
     */
    private boolean initVendorDevices() {
        synchronized (mLock) {
            if (mVendorDevices == null) {
                mVendorDevices = new ArrayList<Device>();

                if (mOsSdkPath != null) {
                    // Load devices from tools folder
                    File toolsDevices = new File(mOsSdkPath,
                            SdkConstants.OS_SDK_TOOLS_LIB_FOLDER +
                            File.separator +
                            SdkConstants.FN_DEVICES_XML);
                    if (toolsDevices.isFile()) {
                        mVendorDevices.addAll(loadDevices(toolsDevices));
                    }

                    // Load devices from vendor extras
                    File extrasFolder = new File(mOsSdkPath, SdkConstants.FD_EXTRAS);
                    List<File> deviceDirs = getExtraDirs(extrasFolder);
                    for (File deviceDir : deviceDirs) {
                        File deviceXml = new File(deviceDir, SdkConstants.FN_DEVICES_XML);
                        if (deviceXml.isFile()) {
                            mVendorDevices.addAll(loadDevices(deviceXml));
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Initializes all user-created {@link Device}s
     * @return True if the list has changed.
     */
    private boolean initUserDevices() {
        synchronized (mLock) {
            if (mUserDevices == null) {
                // User devices should be saved out to
                // $HOME/.android/devices.xml
                mUserDevices = new ArrayList<Device>();
                File userDevicesFile = null;
                try {
                    userDevicesFile = new File(
                            AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    if (userDevicesFile.exists()) {
                        mUserDevices.addAll(DeviceParser.parse(userDevicesFile));
                        return true;
                    }
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't load user devices: %1$s", e.getMessage());
                } catch (SAXException e) {
                    // Probably an old config file which we don't want to overwrite.
                    if (userDevicesFile != null) {
                        String base = userDevicesFile.getAbsoluteFile() + ".old";
                        File renamedConfig = new File(base);
                        int i = 0;
                        while (renamedConfig.exists()) {
                            renamedConfig = new File(base + '.' + (i++));
                        }
                        mLog.error(null, "Error parsing %1$s, backing up to %2$s",
                                userDevicesFile.getAbsolutePath(),
                                renamedConfig.getAbsolutePath());
                        userDevicesFile.renameTo(renamedConfig);
                    }
                } catch (ParserConfigurationException e) {
                    mLog.error(null, "Error parsing %1$s",
                            userDevicesFile == null ? "(null)" : userDevicesFile.getAbsolutePath());
                } catch (IOException e) {
                    mLog.error(null, "Error parsing %1$s",
                            userDevicesFile == null ? "(null)" : userDevicesFile.getAbsolutePath());
                }
            }
        }
        return false;
    }

    public void addUserDevice(Device d) {
        boolean changed = false;
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
                assert mUserDevices != null;
            }
            if (mUserDevices != null) {
                mUserDevices.add(d);
            }
            changed = true;
        }
        if (changed) {
            notifyListeners();
        }
    }

    public void removeUserDevice(Device d) {
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
                assert mUserDevices != null;
            }
            if (mUserDevices != null) {
                Iterator<Device> it = mUserDevices.iterator();
                while (it.hasNext()) {
                    Device userDevice = it.next();
                    if (userDevice.getName().equals(d.getName())
                            && userDevice.getManufacturer().equals(d.getManufacturer())) {
                        it.remove();
                        notifyListeners();
                        return;
                    }

                }
            }
        }
    }

    public void replaceUserDevice(Device d) {
        synchronized (mLock) {
            if (mUserDevices == null) {
                initUserDevices();
            }
            removeUserDevice(d);
            addUserDevice(d);
        }
    }

    /**
     * Saves out the user devices to {@link SdkConstants#FN_DEVICES_XML} in
     * {@link AndroidLocation#getFolder()}.
     */
    public void saveUserDevices() {
        if (mUserDevices == null) {
            return;
        }

        File userDevicesFile = null;
        try {
            userDevicesFile = new File(AndroidLocation.getFolder(),
                    SdkConstants.FN_DEVICES_XML);
        } catch (AndroidLocationException e) {
            mLog.warning("Couldn't find user directory: %1$s", e.getMessage());
            return;
        }

        if (mUserDevices.size() == 0) {
            userDevicesFile.delete();
            return;
        }

        synchronized (mLock) {
            if (mUserDevices.size() > 0) {
                try {
                    DeviceWriter.writeToXml(new FileOutputStream(userDevicesFile), mUserDevices);
                } catch (FileNotFoundException e) {
                    mLog.warning("Couldn't open file: %1$s", e.getMessage());
                } catch (ParserConfigurationException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerFactoryConfigurationError e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                }
            }
        }
    }

    /**
     * Returns hardware properties (defined in hardware.ini) as a {@link Map}.
     *
     * @param s The {@link State} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(State s) {
        Hardware hw = s.getHardware();
        Map<String, String> props = new HashMap<String, String>();
        props.put(HardwareProperties.HW_MAINKEYS,
                getBooleanVal(hw.getButtonType().equals(ButtonType.HARD)));
        props.put(HardwareProperties.HW_TRACKBALL,
                getBooleanVal(hw.getNav().equals(Navigation.TRACKBALL)));
        props.put(HardwareProperties.HW_KEYBOARD,
                getBooleanVal(hw.getKeyboard().equals(Keyboard.QWERTY)));
        props.put(HardwareProperties.HW_DPAD,
                getBooleanVal(hw.getNav().equals(Navigation.DPAD)));

        Set<Sensor> sensors = hw.getSensors();
        props.put(HardwareProperties.HW_GPS, getBooleanVal(sensors.contains(Sensor.GPS)));
        props.put(HardwareProperties.HW_BATTERY,
                getBooleanVal(hw.getChargeType().equals(PowerType.BATTERY)));
        props.put(HardwareProperties.HW_ACCELEROMETER,
                getBooleanVal(sensors.contains(Sensor.ACCELEROMETER)));
        props.put(HardwareProperties.HW_ORIENTATION_SENSOR,
                getBooleanVal(sensors.contains(Sensor.GYROSCOPE)));
        props.put(HardwareProperties.HW_AUDIO_INPUT, getBooleanVal(hw.hasMic()));
        props.put(HardwareProperties.HW_SDCARD, getBooleanVal(hw.getRemovableStorage().size() > 0));
        props.put(HardwareProperties.HW_LCD_DENSITY,
                Integer.toString(hw.getScreen().getPixelDensity().getDpiValue()));
        props.put(HardwareProperties.HW_PROXIMITY_SENSOR,
                getBooleanVal(sensors.contains(Sensor.PROXIMITY_SENSOR)));
        return props;
    }

    /**
     * Returns the hardware properties defined in
     * {@link AvdManager#HARDWARE_INI} as a {@link Map}.
     *
     * @param d The {@link Device} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(Device d) {
        Map<String, String> props = getHardwareProperties(d.getDefaultState());
        for (State s : d.getAllStates()) {
            if (s.getKeyState().equals(KeyboardState.HIDDEN)) {
                props.put("hw.keyboard.lid", getBooleanVal(true));
            }
        }
        props.put(AvdManager.AVD_INI_DEVICE_HASH, Integer.toString(d.hashCode()));
        props.put(AvdManager.AVD_INI_DEVICE_NAME, d.getName());
        props.put(AvdManager.AVD_INI_DEVICE_MANUFACTURER, d.getManufacturer());
        return props;
    }

    /**
     * Takes a boolean and returns the appropriate value for
     * {@link HardwareProperties}
     *
     * @param bool The boolean value to turn into the appropriate
     *            {@link HardwareProperties} value.
     * @return {@code HardwareProperties#BOOLEAN_VALUES[0]} if true,
     *         {@code HardwareProperties#BOOLEAN_VALUES[1]} otherwise.
     */
    private static String getBooleanVal(boolean bool) {
        if (bool) {
            return HardwareProperties.BOOLEAN_VALUES[0];
        }
        return HardwareProperties.BOOLEAN_VALUES[1];
    }

    private Collection<Device> loadDevices(File deviceXml) {
        try {
            return DeviceParser.parse(deviceXml);
        } catch (SAXException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (ParserConfigurationException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (IOException e) {
            mLog.error(null, "Error reading %1$s", deviceXml.getAbsolutePath());
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
        }
        return new ArrayList<Device>();
    }

    private void notifyListeners() {
        synchronized (sListeners) {
            for (DevicesChangedListener listener : sListeners) {
                listener.onDevicesChanged();
            }
        }
    }

    /* Returns all of DeviceProfiles in the extras/ folder */
    private List<File> getExtraDirs(File extrasFolder) {
        List<File> extraDirs = new ArrayList<File>();
        // All OEM provided device profiles are in
        // $SDK/extras/$VENDOR/$ITEM/devices.xml
        if (extrasFolder != null && extrasFolder.isDirectory()) {
            for (File vendor : extrasFolder.listFiles()) {
                if (vendor.isDirectory()) {
                    for (File item : vendor.listFiles()) {
                        if (item.isDirectory() && isDevicesExtra(item)) {
                            extraDirs.add(item);
                        }
                    }
                }
            }
        }

        return extraDirs;
    }

    /*
     * Returns whether a specific folder for a specific vendor is a
     * DeviceProfiles folder
     */
    private boolean isDevicesExtra(File item) {
        File properties = new File(item, SdkConstants.FN_SOURCE_PROP);
        try {
            BufferedReader propertiesReader = new BufferedReader(new FileReader(properties));
            try {
                String line;
                while ((line = propertiesReader.readLine()) != null) {
                    Matcher m = PATH_PROPERTY_PATTERN.matcher(line);
                    if (m.matches()) {
                        return true;
                    }
                }
            } finally {
                propertiesReader.close();
            }
        } catch (IOException ignore) {
        }
        return false;
    }
}
