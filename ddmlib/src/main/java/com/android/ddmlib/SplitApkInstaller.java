/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitApkInstaller {
    private static final String LOG_TAG = "SplitApkInstaller";

    @NonNull private final IDevice mDevice;
    @NonNull private final List<File> mApks;
    @NonNull private final String mOptions;

    private SplitApkInstaller(@NonNull IDevice device, @NonNull List<File> apks,
            @NonNull String options) {
        mDevice = device;
        mApks = apks;
        mOptions = options;
    }

    public void install(long timeout, @NonNull TimeUnit unit) throws InstallException {
        // Installing multiple APK's is perfomed as follows:
        //  # First we create a install session passing in the total size of all APKs
        //      $ pm install-create -S <total_size>
        //      Success: [integer-session-id]   # error if session-id < 0
        //  # Then for each APK, we perform the following. A unique id per APK is generated
        //  # as <index>_<name>, the - at the end means that the APK is streamed via stdin
        //      $ pm install-write -S <session-id> <per_apk_unique_id> -
        //  # Finally, we close the session
        //      $ pm install-commit <session-id>  (or)
        //      $ pm install-abandon <session-id>

        try {
            // create a installation session.
            String sessionId = createMultiInstallSession(mApks, mOptions, timeout, unit);
            if (sessionId == null) {
                Log.d(LOG_TAG, "Failed to establish session, quit installation");
                throw new InstallException("Failed to establish session");
            }

            // now upload each APK in turn.
            int index = 0;
            boolean allUploadSucceeded = true;
            while (allUploadSucceeded && index < mApks.size()) {
                allUploadSucceeded = uploadApk(sessionId, mApks.get(index), index++, timeout,
                        unit);
            }

            // if all files were upload successfully, commit otherwise abandon the installation.
            String command = "pm install-" +
                    (allUploadSucceeded ? "commit " : "abandon ") +
                    sessionId;
            Device.InstallReceiver receiver = new Device.InstallReceiver();
            mDevice.executeShellCommand(command, receiver, timeout, unit);
            String errorMessage = receiver.getErrorMessage();
            if (errorMessage != null) {
                String message = String.format("Failed to finalize session : %1$s", errorMessage);
                Log.e(LOG_TAG, message);
                throw new InstallException(message);
            }

            if (!allUploadSucceeded) {
                throw new InstallException("Failed to install all ");
            }
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Nullable
    private String createMultiInstallSession(@NonNull List<File> apkFiles,
            @NonNull String pmOptions, long timeout, @NonNull TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {

        long totalFileSize = 0L;
        for (File apkFile : apkFiles) {
            totalFileSize += apkFile.length();
        }

        MultiInstallReceiver receiver = new MultiInstallReceiver();
        String cmd = String.format("pm install-create %1$s -S %2$d", pmOptions, totalFileSize);
        mDevice.executeShellCommand(cmd, receiver, timeout, unit);
        return receiver.getSessionId();
    }

    private static final CharMatcher UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS =
            CharMatcher.inRange('a','z').or(CharMatcher.inRange('A','Z'))
                    .or(CharMatcher.anyOf("_-")).negate();

    private boolean uploadApk(@NonNull String sessionId, @NonNull File fileToUpload, int uniqueId,
            long timeout, @NonNull TimeUnit unit) {
        Log.d(sessionId, String.format("Uploading APK %1$s ", fileToUpload.getPath()));
        if (!fileToUpload.exists()) {
            Log.e(sessionId, String.format("File not found: %1$s", fileToUpload.getPath()));
            return false;
        }
        if (fileToUpload.isDirectory()) {
            Log.e(sessionId, String.format("Directory upload not supported: %1$s",
                    fileToUpload.getAbsolutePath()));
            return false;
        }
        String baseName = fileToUpload.getName().lastIndexOf('.') != -1
                ? fileToUpload.getName().substring(0, fileToUpload.getName().lastIndexOf('.'))
                : fileToUpload.getName();

        baseName = UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS.replaceFrom(baseName, '_');

        String command = String.format("pm install-write -S %d %s %d_%s -",
                fileToUpload.length(), sessionId, uniqueId, baseName);

        Log.d(sessionId, String.format("Executing : %1$s", command));
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(fileToUpload));
            Device.InstallReceiver receiver = new Device.InstallReceiver();
            AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(),
                    AdbHelper.AdbService.EXEC, command, mDevice,
                    receiver, timeout, unit, inputStream);
            if (receiver.getErrorMessage() != null) {
                Log.e(sessionId, String.format("Error while uploading %1$s : %2$s", fileToUpload.getName(),
                        receiver.getErrorMessage()));
            } else {
                Log.d(sessionId, String.format("Successfully uploaded %1$s", fileToUpload.getName()));
            }
            return receiver.getErrorMessage() == null;
        } catch (Exception e) {
            Log.e(sessionId, e);
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(sessionId, e);
                }
            }

        }
    }

    @NonNull
    private static String getOptions(boolean reInstall, @NonNull List<String> pmOptions) {
        return getOptions(reInstall, false, null, pmOptions);
    }

    @NonNull
    private static String getOptions(boolean reInstall, boolean partialInstall,
            @Nullable String applicationId, List<String> pmOptions) {
        StringBuilder sb = new StringBuilder();

        if (reInstall) {
            sb.append("-r ");
        }

        if (partialInstall) {
            if (applicationId == null) {
                throw new IllegalArgumentException(
                        "Cannot do a partial install without knowing the application id");
            }

            sb.append("-r ");
            sb.append(applicationId);
            sb.append(' ');
        }

        sb.append(Joiner.on(' ').join(pmOptions));

        return sb.toString();
    }

    private static void validateArguments(@NonNull IDevice device, @NonNull List<File> apks) {
        if (apks.isEmpty()) {
            throw new IllegalArgumentException(
              "List of APKs is empty: the main APK must be specified.");
        }

        for (File apk: apks) {
            if (!apk.isFile()) {
                throw new IllegalArgumentException("Invalid File: " + apk.getPath());
            }
        }

        if (!device.getVersion().isGreaterOrEqualThan(21)) {
            if (apks.size() > 1) {
                throw new IllegalArgumentException(
                  "Cannot install split APKs on device with API level < 21");
            }
        }
    }

    /**
     * Returns a {@link SplitApkInstaller} for the given list of APKs on the given device.
     * @param apks list of APKs, must include at least the main APK
     */
    public static SplitApkInstaller create(@NonNull IDevice device, @NonNull List<File> apks,
            boolean reInstall, @NonNull List<String> pmOptions) {
        validateArguments(device, apks);
        return new SplitApkInstaller(device, apks, getOptions(reInstall, pmOptions));
    }

    public static SplitApkInstaller create(@NonNull IDevice device, @NonNull String applicationId,
      @NonNull List<File> apks, boolean reInstall, @NonNull List<String> pmOptions) {
        validateArguments(device, apks);
        return new SplitApkInstaller(device, apks,
          getOptions(reInstall, true, applicationId, pmOptions));
    }

    /**
     * Implementation of {@link com.android.ddmlib.MultiLineReceiver} that can receive a
     * Success message from ADB followed by a session ID.
     */
    private static class MultiInstallReceiver extends MultiLineReceiver {

        private static final Pattern successPattern = Pattern.compile("Success: .*\\[(\\d*)\\]");

        @Nullable String sessionId = null;

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher matcher = successPattern.matcher(line);
                if (matcher.matches()) {
                    sessionId = matcher.group(1);
                }
            }
        }

        @Nullable
        public String getSessionId() {
            return sessionId;
        }
    }
}
