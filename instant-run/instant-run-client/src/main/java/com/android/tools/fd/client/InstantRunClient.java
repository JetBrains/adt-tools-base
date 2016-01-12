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

package com.android.tools.fd.client;

import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_EOF;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_PATCHES;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_PING;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_RESTART_ACTIVITY;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_SHOW_TOAST;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_IDENTIFIER;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_VERSION;
import static com.android.tools.fd.runtime.Paths.getDeviceIdFolder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.fd.runtime.ApplicationPatch;
import com.android.tools.fd.runtime.Paths;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

public class InstantRunClient {
    private static final String LOCAL_HOST = "127.0.0.1";

    /** Local port on the desktop machine via which we tunnel to the Android device */
    private static final int DEFAULT_LOCAL_PORT = 46622; // Note: just a random number, hopefully it is a free/available port on the host

    /** Prefix for classes.dex files */
    private static final String CLASSES_DEX_PREFIX = "classes";

    /** Suffix for classes.dex files */
    private static final String CLASSES_DEX_SUFFIX = ".dex";


    /** Instead of writing to the data folder, we can read/write to a local temp file instead */
    private static final boolean USE_BUILD_ID_TEMP_FILE =
            !Boolean.getBoolean("instantrun.use_datadir");

    @NonNull
    private final UserFeedback mUserFeedback;

    @NonNull
    private final ILogger mLogger;

    @NonNull
    private final String mPackageName;

    private final long mToken;
    private final int mLocalPort;

    public InstantRunClient(
            @NonNull String packageName,
            @NonNull UserFeedback userFeedback,
            @NonNull ILogger logger,
            long token) {
        this(packageName, userFeedback, logger, token, DEFAULT_LOCAL_PORT);
    }

    @VisibleForTesting
    public InstantRunClient(
            @NonNull String packageName,
            @NonNull UserFeedback userFeedback,
            @NonNull ILogger logger,
            long token,
            int port) {
        mPackageName = packageName;
        mUserFeedback = userFeedback;
        mLogger = logger;
        mToken = token;
        mLocalPort = port;
    }

    @NonNull
    private static String copyToDeviceScratchFile(@NonNull IDevice device, @NonNull String pkgName,
            @NonNull String contents)
            throws IOException, AdbCommandRejectedException, SyncException, TimeoutException {

        File local = null;
        try {
            local = createTempFile("data", "fdr");
            Files.write(contents.getBytes(Charsets.UTF_8), local);
            return copyToDeviceScratchFile(device, pkgName, local);
        } finally {
            if (local != null) {
                //noinspection ResultOfMethodCallIgnored
                local.delete();
            }
        }
    }

    @NonNull
    private static String copyToDeviceScratchFile(@NonNull IDevice device, @NonNull String pkgName,
            @NonNull File local)
            throws IOException, AdbCommandRejectedException, SyncException, TimeoutException {
        String remoteTmpBuildId = Paths.DEVICE_TEMP_DIR + "/" + pkgName + "-data.fdr";
        device.pushFile(local.getAbsolutePath(), remoteTmpBuildId);
        return remoteTmpBuildId;
    }

    private static int getMaxDexFileNumber(@NonNull String fileListing) {
        int max = -1;

        for (String name : Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings()
                .splitToList(fileListing)) {
            if (name.startsWith(CLASSES_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
                String middle = name.substring(CLASSES_DEX_PREFIX.length(),
                        name.length() - CLASSES_DEX_SUFFIX.length());
                try {
                    int version = Integer.decode(middle);
                    if (version > max) {
                        max = version;
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }

        return max;
    }

    private static File createTempFile(String prefix, String suffix) throws IOException {
        //noinspection SSBasedInspection Tests use this in tools/base
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        return file;
    }

    /**
     * Attempts to connect to a given device and sees if an instant run enabled app is running
     * there.
     */
    @NonNull
    public AppState getAppState(@NonNull IDevice device) {
        try {
            return talkToApp(device,
                    new Communicator<AppState>() {
                        @Override
                        public AppState communicate(@NonNull DataInputStream input,
                                @NonNull DataOutputStream output) throws
                                IOException {
                            output.writeInt(MESSAGE_PING);
                            // Wait for "pong"
                            boolean foreground = input.readBoolean();
                            mLogger.info(
                                    "Ping sent and replied successfully, application seems to be running. Foreground="
                                            + foreground);
                            return foreground ? AppState.FOREGROUND : AppState.BACKGROUND;
                        }
                    }, AppState.NOT_RUNNING);
        } catch (Throwable e) {
            return AppState.NOT_RUNNING;
        }
    }

    @NonNull
    private <T> T talkToApp(@NonNull IDevice device,
            @NonNull Communicator<T> communicator,
            @NonNull T errorValue) {

        try {
            device.createForward(mLocalPort, mPackageName,
                                 IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            try {
                return talkToAppWithinPortForward(communicator, errorValue, mLocalPort);
            } catch (UnknownHostException e) {
                mLogger.warning("%s", e);
            } catch (SocketException e) {
                if (e.getMessage().equals("Broken pipe")) {
                    mUserFeedback.error("No connection to app; cannot sync changes");
                    return errorValue;
                }
                mLogger.warning("%s", e);
            } catch (IOException e) {
                mLogger.warning("%s", e);
            } catch (Throwable e) {
                mLogger.warning("%s", e);
                return errorValue;
            } finally {
                device.removeForward(mLocalPort, mPackageName,
                                     IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            }
        } catch (TimeoutException e) {
            mLogger.warning("%s", e);
        } catch (AdbCommandRejectedException e) {
            mLogger.warning("%s", e);
        } catch (Throwable e) {
            mLogger.warning("%s", e);
        }

        return errorValue;
    }

    private static <T> T talkToAppWithinPortForward(@NonNull Communicator<T> communicator,
      @NonNull T errorValue, int localPort) throws IOException {
        Socket socket = new Socket(LOCAL_HOST, localPort);
        try {
            socket.setSoTimeout(8 * 1000); // Allow up to 8 second before timing out
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            try {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                try {
                    output.writeLong(PROTOCOL_IDENTIFIER);
                    output.writeInt(PROTOCOL_VERSION);

                    int version = input.readInt();
                    if (version != PROTOCOL_VERSION) {
                        return errorValue;
                    }

                    socket.setSoTimeout(communicator.getTimeout());
                    T value = communicator.communicate(input, output);

                    output.writeInt(MESSAGE_EOF);

                    return value;
                } finally {
                    input.close();
                }
            } finally {
                output.close();
            }
        } finally {
            socket.close();
        }
    }

    public void showToast(@NonNull IDevice device, @NonNull final String message) {
        try {
            talkToApp(device, new Communicator<Boolean>() {
                @Override
                public Boolean communicate(@NonNull DataInputStream input,
                        @NonNull DataOutputStream output) throws IOException {
                    output.writeInt(MESSAGE_SHOW_TOAST);
                    output.writeUTF(message);
                    return false;
                }
            }, true);
        } catch (Throwable e) {
            mLogger.warning("%s", e);
        }
    }

    /**
     * Restart the activity on this device, if it's running and is in the foreground.
     */
    public void restartActivity(@NonNull IDevice device) {
        AppState appState = getAppState(device);
        if (appState == AppState.FOREGROUND || appState == AppState.BACKGROUND) {
            talkToApp(device, new Communicator<Boolean>() {
                @Override
                public Boolean communicate(@NonNull DataInputStream input,
                        @NonNull DataOutputStream output) throws IOException {
                    output.writeInt(MESSAGE_RESTART_ACTIVITY);
                    writeToken(output);
                    return false;
                }
            }, true);
        }
    }

    public void pushPatches(@Nullable IDevice device,
            @NonNull final String buildId,
            @NonNull final List<ApplicationPatch> changes,
            @NonNull UpdateMode updateMode,
            final boolean isRestartActivity,
            final boolean isShowToastEnabled) {
        if (changes.isEmpty() || updateMode == UpdateMode.NO_CHANGES) {
            // Sync the build id to the device; Gradle might rev the build id even when there are no changes,
            // and we need to make sure that the device id reflects this new build id, or the next
            // build will discover different id's and will conclude that it needs to do a full rebuild
            if (device != null) {
                transferLocalIdToDeviceId(device, buildId);
            }

            mUserFeedback.noChanges();
            return;
        }

        if (updateMode == UpdateMode.HOT_SWAP && isRestartActivity) {
            updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
        }

        if (device != null) {
            final UpdateMode updateMode1 = updateMode;
            talkToApp(device, new Communicator<Boolean>() {
                @Override
                public Boolean communicate(@NonNull DataInputStream input,
                        @NonNull DataOutputStream output) throws IOException {
                    output.writeInt(MESSAGE_PATCHES);
                    writeToken(output);
                    ApplicationPatchUtil.write(output, changes, updateMode1);

                    // Let the app know whether it should show toasts
                    output.writeBoolean(isShowToastEnabled);

                    // Finally read a boolean back from the other side; this has the net effect of
                    // waiting until applying/verifying code on the other side is done. (It doesn't
                    // count the actual restart time, but for activity restarts it's typically instant,
                    // and for cold starts we have no easy way to handle it (the process will die and a
                    // new process come up; to measure that we'll need to work a lot harder.)
                    input.readBoolean();

                    return false;
                }

                @Override
                int getTimeout() {
                    return 8000; // allow up to 8 seconds for resource push
                }
            }, true);

            transferLocalIdToDeviceId(device, buildId);
        }

        mUserFeedback.notifyEnd(updateMode);
    }

    /**
     * Called after a build &amp; successful push to device: updates the build id on the device to
     * whatever the build id was assigned by Gradle.
     *
     * @param device the device to push to
     */
    public void transferLocalIdToDeviceId(@NonNull IDevice device, @NonNull String buildId) {
        try {
            if (USE_BUILD_ID_TEMP_FILE) {
                String remoteIdFile = getDeviceIdFolder(mPackageName);
                //noinspection SSBasedInspection This should work
                File local = File.createTempFile("build-id", "txt");
                local.deleteOnExit();
                Files.write(buildId, local, Charsets.UTF_8);
                device.pushFile(local.getPath(), remoteIdFile);
            } else {
                String remote = copyToDeviceScratchFile(device, mPackageName, buildId);
                String dataDir = Paths.getDataDirectory(mPackageName);

                // We used to do this here:
                //String cmd = "run-as " + pkg + " mkdir -p " + dataDir + "; run-as " + pkg + " cp " + remote + " " + dataDir + "/" + BUILD_ID_TXT;
                // but it turns out "cp" is missing on API 15! Let's use cat and sh instead which seems to be available everywhere.
                // (Note: echo is not, it's missing on API 19.)
                String cmd = "run-as " + mPackageName + " mkdir -p " + dataDir + "; cat " + remote
                             + " | run-as " + mPackageName + " sh -c 'cat > " + dataDir + "/"
                             + Paths.BUILD_ID_TXT + "'";
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                device.executeShellCommand(cmd, receiver);
                String output = receiver.getOutput();
                if (!output.trim().isEmpty()) {
                    mLogger.warning("Unexpected shell output: " + output);
                }
            }
        } catch (IOException ioe) {
            mLogger.warning("Couldn't write build id file", ioe);
        } catch (AdbCommandRejectedException e) {
            mLogger.warning("%s", e);
        } catch (TimeoutException e) {
            mLogger.warning("%s", e);
        } catch (ShellCommandUnresponsiveException e) {
            mLogger.warning("%s", e);
        } catch (SyncException e) {
            mLogger.warning("%s", e);
        }
    }

    /**
     * Returns the build timestamp on the device, or null if it is not found.
     */
    @Nullable
    public String getDeviceBuildTimestamp(@NonNull IDevice device) {
        try {
            if (USE_BUILD_ID_TEMP_FILE) {
                String remoteIdFile = getDeviceIdFolder(mPackageName);
                File localIdFile = createTempFile("build-id", "txt");
                try {
                    device.pullFile(remoteIdFile, localIdFile.getPath());
                    return Files.toString(localIdFile, Charsets.UTF_8).trim();
                } catch (SyncException ignore) {
                    return null;
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    localIdFile.delete();
                }
            } else {
                String remoteIdFile = Paths.getDataDirectory(mPackageName) + "/"
                                      + Paths.BUILD_ID_TXT;
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                device.executeShellCommand("run-as " + mPackageName + " cat " + remoteIdFile,
                        receiver);
                String output = receiver.getOutput().trim();
                String id;
                if (output.contains(":")) { // cat: command not found, cat: permission denied etc
                    if (output.startsWith(remoteIdFile)) {
                        // /data/data/my.pkg.path/files/studio-fd/build-id.txt: No such file or directory
                        return null;
                    }
                    // on a user device, we cannot pull from a path where the segments aren't readable (I think this is a ddmlib limitation)
                    // So we first copy to /data/local/tmp and pull from there..
                    String remoteTmpFile = "/data/local/tmp/build-id.txt";
                    device.executeShellCommand("cp " + remoteIdFile + " " + remoteTmpFile,
                            receiver);
                    output = receiver.getOutput().trim();
                    if (!output.isEmpty()) {
                        mLogger.info(output);
                    }
                    File localIdFile = createTempFile("build-id", "txt");
                    device.pullFile(remoteTmpFile, localIdFile.getPath());
                    id = Files.toString(localIdFile, Charsets.UTF_8).trim();
                    //noinspection ResultOfMethodCallIgnored
                    localIdFile.delete();
                } else {
                    id = output;
                }
                return id;
            }
        } catch (IOException ignore) {
        } catch (AdbCommandRejectedException e) {
            mLogger.warning("%s", e);
        } catch (SyncException e) {
            mLogger.warning("%s", e);
        } catch (TimeoutException e) {
            mLogger.warning("%s", e);
        } catch (ShellCommandUnresponsiveException e) {
            mLogger.warning("%s", e);
        }

        return null;
    }

    private void writeToken(@NonNull DataOutputStream output) throws IOException {
        output.writeLong(mToken);
    }

    /**
     * Transfer the file as a slice/sharded dex file. This means
     * that its remote path should be the slice name, in the dex
     * directory.
     */
    public static final int TRANSFER_MODE_SLICE = 1;

    /**
     * Transfer the file as a dex coldswap overlay file. This means
     * that its remote path should be a new, unique (and numerically
     * highest) dex file name in the dex folder.
     */
    public static final int TRANSFER_MODE_NEW_DEX = 2;

    /**
     * Transfer the file as a hotswap overlay file. This means
     * that its remote path should be a temporary file.
     */
    public static final int TRANSFER_MODE_HOTSWAP = 3;

    /**
     * Transfer the file as a resource file. This means that it
     * should be written to the inactive resource file section
     * in the app data directory.
     */
    public static final int TRANSFER_MODE_RESOURCES = 4;

    /**
     * File to be transferred to the device. For use with
     * {@link #pushFiles(List, IDevice, String)}
     */
    public static class FileTransfer {
        public final int mode;
        public final File source;
        public final String name;

        public FileTransfer(int mode, @NonNull File source, @NonNull String name) {
            this.mode = mode;
            this.source = source;
            this.name = name;
        }

        @NonNull
        public static FileTransfer createRestartDex(@NonNull File source) {
            return new FileTransfer(TRANSFER_MODE_NEW_DEX, source, Paths.RESTART_DEX_FILE_NAME);
        }

        @NonNull
        public static FileTransfer createSliceDex(@NonNull File source, @NonNull String name) {
            return new FileTransfer(TRANSFER_MODE_SLICE, source, name);
        }

        @NonNull
        public static FileTransfer createResourceFile(@NonNull File source) {
            return new FileTransfer(TRANSFER_MODE_RESOURCES, source, Paths.RESOURCE_FILE_NAME);
        }

        @NonNull
        public static FileTransfer createHotswapPatch(@NonNull File source) {
            return new FileTransfer(TRANSFER_MODE_HOTSWAP, source, Paths.RELOAD_DEX_FILE_NAME);
        }

        @NonNull
        public ApplicationPatch getPatch() throws IOException {
            byte[] bytes = Files.toByteArray(source);
            String path;
            // These path names are specially handled on the client side
            // (e.g. it interprets "classes.dex" as meaning create a new
            // unique class file in the class folder
            switch (mode) {
                case TRANSFER_MODE_SLICE:
                    path = Paths.DEX_SLICE_PREFIX + name;
                    break;
                case TRANSFER_MODE_NEW_DEX:
                case TRANSFER_MODE_HOTSWAP:
                case TRANSFER_MODE_RESOURCES:
                    path = name;
                    break;
                default:
                    throw new IllegalArgumentException(Integer.toString(mode));
            }

            return new ApplicationPatch(path, bytes);
        }

        @Override
        public String toString() {
            return source + " as " + name + " with mode " + mode;
        }
    }

    /**
     * Install dex and resource files on the given device (using adb to push files
     * to the device when the app isn't running so we can't send it patches via
     * the socket connection.)
     *
     * @param files the files to push to the device, and the paths to push them as.
     * @param device   the device to push to
     * @return true if installation succeeded
     */
    public boolean pushFiles(
            @NonNull List<FileTransfer> files,
            @NonNull IDevice device,
            @NonNull final String buildId) {
        try {
            Set<String> createdDirs = Sets.newHashSet();

            // List the files in the dex folder to compute a new .dex file name that follows the existing
            // naming pattern but is numbered higher than any existing .dex files
            int max = -1;
            for (FileTransfer file : files) {
                String folder;
                String name;
                switch (file.mode) {
                    case TRANSFER_MODE_SLICE:
                        folder = Paths.getDexFileDirectory(mPackageName);
                        name = file.name;
                        break;
                    case TRANSFER_MODE_NEW_DEX:
                        // Compute a unique name
                        if (max == -1) {
                            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                            String dexFolder = Paths.getDexFileDirectory(mPackageName);
                            String cmd = "run-as " + mPackageName + " ls " + dexFolder;
                            device.executeShellCommand(cmd, receiver);
                            max = getMaxDexFileNumber(receiver.getOutput());
                        }
                        folder = Paths.getDexFileDirectory(mPackageName);
                        name = String.format("%s0x%04x%s", CLASSES_DEX_PREFIX, ++max,
                                CLASSES_DEX_SUFFIX);
                        break;
                    case TRANSFER_MODE_RESOURCES:
                        folder = Paths.getInboxDirectory(mPackageName);
                        name = Paths.RESOURCE_FILE_NAME;
                        break;
                    case TRANSFER_MODE_HOTSWAP:
                        throw new IllegalArgumentException("Hotswap patches can only be applied "
                                + "when the app is running");
                    default:
                        throw new IllegalArgumentException(Integer.toString(file.mode));
                }

                // Copy the restart .dex file over to the device in the dex folder with the new name
                String remote = copyToDeviceScratchFile(device, mPackageName, file.source);

                // Make sure directory exists
                if (!createdDirs.contains(folder)) {
                    createdDirs.add(folder);
                    if (!runCommand(device, "run-as " + mPackageName + " mkdir -p " + folder)) {
                        return false;
                    }
                }

                String cmd = "run-as " + mPackageName + " cp " + remote + " " + folder + "/" + name;
                if (!runCommand(device, cmd)) {
                    return false;
                }
            }

            transferLocalIdToDeviceId(device, buildId);

            return true;
        } catch (IOException ioe) {
            mLogger.warning("Couldn't write build id file: %s", ioe);
        } catch (AdbCommandRejectedException e) {
            mLogger.warning("%s", e);
        } catch (TimeoutException e) {
            mLogger.warning("%s", e);
        } catch (ShellCommandUnresponsiveException e) {
            mLogger.warning("%s", e);
        } catch (SyncException e) {
            mLogger.warning("%s", e);
        }

        return false;
    }

    private boolean runCommand(@NonNull IDevice device, @NonNull String cmd)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        CollectingOutputReceiver receiver;
        String output;
        receiver = new CollectingOutputReceiver();
        device.executeShellCommand(cmd, receiver);
        output = receiver.getOutput();
        if (!output.trim().isEmpty()) {
            mLogger.warning("Unexpected shell output for " + cmd + ": " + output);
            return false;
        }
        return true;
    }
}
