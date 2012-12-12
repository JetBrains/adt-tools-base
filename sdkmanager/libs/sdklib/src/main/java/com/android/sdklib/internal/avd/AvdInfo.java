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

package com.android.sdklib.internal.avd;

import com.android.SdkConstants;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * An immutable structure describing an Android Virtual Device.
 */
public final class AvdInfo implements Comparable<AvdInfo> {

    /**
     * Status for an {@link AvdInfo}. Indicates whether or not this AVD is valid.
     */
    public static enum AvdStatus {
        /** No error */
        OK,
        /** Missing 'path' property in the ini file */
        ERROR_PATH,
        /** Missing config.ini file in the AVD data folder */
        ERROR_CONFIG,
        /** Missing 'target' property in the ini file */
        ERROR_TARGET_HASH,
        /** Target was not resolved from its hash */
        ERROR_TARGET,
        /** Unable to parse config.ini */
        ERROR_PROPERTIES,
        /** System Image folder in config.ini doesn't exist */
        ERROR_IMAGE_DIR,
        /** The {@link Device} this AVD is based on has changed from its original configuration*/
        ERROR_DEVICE_CHANGED,
        /** The {@link Device} this AVD is based on is no longer available */
        ERROR_DEVICE_MISSING;
    }

    private final String mName;
    private final File mIniFile;
    private final String mFolderPath;
    private final String mTargetHash;
    private final IAndroidTarget mTarget;
    private final String mAbiType;
    /** An immutable map of properties. This must not be modified. Map can be empty. Never null. */
    private final Map<String, String> mProperties;
    private final AvdStatus mStatus;

    /**
     * Creates a new valid AVD info. Values are immutable.
     * <p/>
     * Such an AVD is available and can be used.
     * The error string is set to null.
     *
     * @param name The name of the AVD (for display or reference)
     * @param iniFile The path to the config.ini file
     * @param folderPath The path to the data directory
     * @param targetHash the target hash
     * @param target The target. Can be null, if the target was not resolved.
     * @param abiType Name of the abi.
     * @param properties The property map. If null, an empty map will be created.
     */
    public AvdInfo(String name,
            File iniFile,
            String folderPath,
            String targetHash,
            IAndroidTarget target,
            String abiType,
            Map<String, String> properties) {
         this(name, iniFile, folderPath, targetHash, target, abiType, properties, AvdStatus.OK);
    }

    /**
     * Creates a new <em>invalid</em> AVD info. Values are immutable.
     * <p/>
     * Such an AVD is not complete and cannot be used.
     * The error string must be non-null.
     *
     * @param name The name of the AVD (for display or reference)
     * @param iniFile The path to the config.ini file
     * @param folderPath The path to the data directory
     * @param targetHash the target hash
     * @param target The target. Can be null, if the target was not resolved.
     * @param abiType Name of the abi.
     * @param properties The property map. If null, an empty map will be created.
     * @param status The {@link AvdStatus} of this AVD. Cannot be null.
     */
    public AvdInfo(String name,
            File iniFile,
            String folderPath,
            String targetHash,
            IAndroidTarget target,
            String abiType,
            Map<String, String> properties,
            AvdStatus status) {
        mName = name;
        mIniFile = iniFile;
        mFolderPath = folderPath;
        mTargetHash = targetHash;
        mTarget = target;
        mAbiType = abiType;
        mProperties = properties == null ? Collections.<String, String>emptyMap()
                                         : Collections.unmodifiableMap(properties);
        mStatus = status;
    }

    /** Returns the name of the AVD. */
    public String getName() {
        return mName;
    }

    /** Returns the path of the AVD data directory. */
    public String getDataFolderPath() {
        return mFolderPath;
    }

    /** Returns the processor type of the AVD. */
    public String getAbiType() {
        return mAbiType;
    }

    public String getCpuArch() {
        String cpuArch = mProperties.get(AvdManager.AVD_INI_CPU_ARCH);
        if (cpuArch != null) {
            return cpuArch;
        }

        // legacy
        return SdkConstants.CPU_ARCH_ARM;
    }

    public String getDeviceManufacturer() {
        String deviceManufacturer = mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER);
        if (deviceManufacturer != null && !deviceManufacturer.isEmpty()) {
            return deviceManufacturer;
        }

        return "";
    }

    public String getDeviceName() {
        String deviceName = mProperties.get(AvdManager.AVD_INI_DEVICE_NAME);
        if (deviceName != null && !deviceName.isEmpty()) {
            return deviceName;
        }

        return "";
    }

    /** Convenience function to return a more user friendly name of the abi type. */
    public static String getPrettyAbiType(String raw) {
        String s = null;
        if (raw.equalsIgnoreCase(SdkConstants.ABI_ARMEABI)) {
            s = "ARM (" + SdkConstants.ABI_ARMEABI + ")";

        } else if (raw.equalsIgnoreCase(SdkConstants.ABI_ARMEABI_V7A)) {
            s = "ARM (" + SdkConstants.ABI_ARMEABI_V7A + ")";

        } else if (raw.equalsIgnoreCase(SdkConstants.ABI_INTEL_ATOM)) {
            s = "Intel Atom (" + SdkConstants.ABI_INTEL_ATOM + ")";

        } else if (raw.equalsIgnoreCase(SdkConstants.ABI_MIPS)) {
            s = "MIPS (" + SdkConstants.ABI_MIPS + ")";

        } else {
            s = raw + " (" + raw + ")";
        }
        return s;
    }

    /**
     * Returns the target hash string.
     */
    public String getTargetHash() {
        return mTargetHash;
    }

    /** Returns the target of the AVD, or <code>null</code> if it has not been resolved. */
    public IAndroidTarget getTarget() {
        return mTarget;
    }

    /** Returns the {@link AvdStatus} of the receiver. */
    public AvdStatus getStatus() {
        return mStatus;
    }

    /**
     * Helper method that returns the default AVD folder that would be used for a given
     * AVD name <em>if and only if</em> the AVD was created with the default choice.
     * <p/>
     * Callers must NOT use this to "guess" the actual folder from an actual AVD since
     * the purpose of the AVD .ini file is to be able to change this folder. Callers
     * should however use this to create a new {@link AvdInfo} to setup its data folder
     * to the default.
     * <p/>
     * The default is {@code getDefaultAvdFolder()/avdname.avd/}.
     * <p/>
     * For an actual existing AVD, callers must use {@link #getDataFolderPath()} instead.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    public static File getDefaultAvdFolder(AvdManager manager, String avdName)
            throws AndroidLocationException {
        return new File(manager.getBaseAvdFolder(),
                        avdName + AvdManager.AVD_FOLDER_EXTENSION);
    }

    /**
     * Helper method that returns the .ini {@link File} for a given AVD name.
     * <p/>
     * The default is {@code getDefaultAvdFolder()/avdname.ini}.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    public static File getDefaultIniFile(AvdManager manager, String avdName)
            throws AndroidLocationException {
        String avdRoot = manager.getBaseAvdFolder();
        return new File(avdRoot, avdName + AvdManager.INI_EXTENSION);
    }

    /**
     * Returns the .ini {@link File} for this AVD.
     */
    public File getIniFile() {
        return mIniFile;
    }

    /**
     * Helper method that returns the Config {@link File} for a given AVD name.
     */
    public static File getConfigFile(String path) {
        return new File(path, AvdManager.CONFIG_INI);
    }

    /**
     * Returns the Config {@link File} for this AVD.
     */
    public File getConfigFile() {
        return getConfigFile(mFolderPath);
    }

    /**
     * Returns an unmodifiable map of properties for the AVD.
     * This can be empty but not null.
     * Callers must NOT try to modify this immutable map.
     */
    public Map<String, String> getProperties() {
        return mProperties;
    }

    /**
     * Returns the error message for the AVD or <code>null</code> if {@link #getStatus()}
     * returns {@link AvdStatus#OK}
     */
    public String getErrorMessage() {
        switch (mStatus) {
            case ERROR_PATH:
                return String.format("Missing AVD 'path' property in %1$s", getIniFile());
            case ERROR_CONFIG:
                return String.format("Missing config.ini file in %1$s", mFolderPath);
            case ERROR_TARGET_HASH:
                return String.format("Missing 'target' property in %1$s", getIniFile());
            case ERROR_TARGET:
                return String.format("Unknown target '%1$s' in %2$s",
                        mTargetHash, getIniFile());
            case ERROR_PROPERTIES:
                return String.format("Failed to parse properties from %1$s",
                        getConfigFile());
            case ERROR_IMAGE_DIR:
                return String.format(
                        "Invalid value in image.sysdir. Run 'android update avd -n %1$s'",
                        mName);
            case ERROR_DEVICE_CHANGED:
                return String.format("%1$s %2$s configuration has changed since AVD creation",
                        mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER),
                        mProperties.get(AvdManager.AVD_INI_DEVICE_NAME));
            case ERROR_DEVICE_MISSING:
                return String.format("%1$s %2$s no longer exists as a device",
                        mProperties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER),
                        mProperties.get(AvdManager.AVD_INI_DEVICE_NAME));
            case OK:
                assert false;
                return null;
        }

        return null;
    }

    /**
     * Returns whether an emulator is currently running the AVD.
     */
    public boolean isRunning() {
        File f = new File(mFolderPath, "userdata-qemu.img.lock");   //$NON-NLS-1$
        return f.isFile();
    }

    /**
     * Compares this object with the specified object for order. Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * @param o the Object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is
     *         less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(AvdInfo o) {
        // first handle possible missing targets (if the AVD failed to load for unresolved targets)
        if (mTarget == null && o != null && o.mTarget == null) {
            return 0;
        } if (mTarget == null) {
            return +1;
        } else if (o == null || o.mTarget == null) {
            return -1;
        }

        // then compare the targets
        int targetDiff = mTarget.compareTo(o.mTarget);

        if (targetDiff == 0) {
            // same target? compare on the avd name
            return mName.compareTo(o.mName);
        }

        return targetDiff;
    }
}
