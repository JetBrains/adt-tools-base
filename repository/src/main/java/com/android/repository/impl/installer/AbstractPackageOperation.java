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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Installer;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

/**
 * Frameworks for concrete {@link Installer}s and {@link Uninstaller}s that manages creation of temp
 * directories, writing package metadata and install status, and resuming in-progress installs.
 */
public abstract class AbstractPackageOperation implements PackageOperation {

    /**
     * Key used in the properties file for the temporary path.
     */
    private static final String PATH_KEY = "path";

    /**
     * The concrete type of the installer. TODO: do we actually need this?
     */
    private static final String CLASSNAME_KEY = "class";

    /**
     * Name of the marker file that's written into the temporary directory when the prepare phase
     * has completed successfully.
     */
    private static final String PREPARE_COMPLETE_FN = ".prepareComplete";

    /**
     * Name of the directory created in the final install location containing data to get the
     * install restarted if it stops.
     */
    private static final String INSTALL_DATA_FN = ".installData";

    /**
     * Status of the installer.
     */
    private PackageOperation.InstallStatus mInstallStatus
      = PackageOperation.InstallStatus.NOT_STARTED;

    /**
     * Properties written to the final install folder, used to restart the installer if needed.
     */
    private Properties mInstallProperties;

    /**
     * Listeners that will be notified when the status changes.
     */
    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    private final RepoManager mRepoManager;

    protected final FileOp mFop;

    protected AbstractPackageOperation(@NonNull RepoManager repoManager, @NonNull FileOp fop) {
        mRepoManager = repoManager;
        mFop = fop;
    }

    /**
     * Subclasses should override this to prepare a package for (un)installation, including
     * downloading, unzipping, etc. as needed. No modification to the actual SDK should happen
     * during this time.
     *
     * @param installTempPath The dir that should be used for any intermediate processing.
     * @param progress        For logging and progress display
     */
    protected abstract boolean doPrepare(@NonNull File installTempPath,
      @NonNull ProgressIndicator progress);

    /**
     * Subclasses should implement this to do any install/uninstall completion actions required.
     *
     * @param installTemp The temporary dir in which we prepared the (un)install. May be
     *                    {@code null} if for example the installer removed the installer
     *                    properties file, but should not be normally.
     * @param progress    For logging and progress indication.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     * @see #complete(ProgressIndicator)
     */
    protected abstract boolean doComplete(@Nullable File installTemp,
      @NonNull ProgressIndicator progress);

    /**
     * Finds the prepared files using the installer metadata, and calls {@link
     * #doComplete(File, ProgressIndicator)}.
     *
     * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
     *                 logging.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    @Override
    public final boolean complete(@NonNull ProgressIndicator progress) {
        progress.logInfo(String.format("Finishing \"%1$s\"", getName()));
        if (!updateStatus(InstallStatus.RUNNING, progress)) {
            progress.setFraction(1);
            progress.setIndeterminate(false);
            progress.logInfo(String.format("\"%1$s\" failed.", getName()));
            return false;
        }
        if (mInstallProperties == null) {
            try {
                mInstallProperties = readInstallProperties(
                  getLocation(progress));
            } catch (IOException e) {
                // We won't have a temp path, but try to continue anyway
            }
        }
        boolean result = false;
        String installTempPath = null;
        if (mInstallProperties != null) {
            installTempPath = mInstallProperties.getProperty(PATH_KEY);
        }
        File installTemp = installTempPath == null ? null : new File(installTempPath);
        try {
            // Re-validate the install path, in case something was changed since prepare.
            if (!InstallerUtil.checkValidPath(getLocation(progress), getRepoManager(), progress)) {
                return false;
            }

            result = doComplete(installTemp, progress);
        } finally {
            if (!result && progress.isCanceled()) {
                cleanup(progress);
            }
            result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED,
              progress);
            if (result && installTemp != null) {
                mFop.deleteFileOrFolder(installTemp);
            }
            getRepoManager().installEnded(getPackage());
            getRepoManager().markLocalCacheInvalid();
        }

        progress.setFraction(1);
        progress.setIndeterminate(false);
        progress
          .logInfo(String.format("\"%1$s\" %2$s.", getName(), result ? "complete" : "failed"));
        return result;
    }

    /**
     * Looks in {@code installPath} for an install properties file and returns the contents if
     * found.
     */
    @Nullable
    private Properties readInstallProperties(@NonNull File installPath) throws IOException {
        File metaDir = new File(installPath, InstallerUtil.INSTALLER_DIR_FN);
        if (!mFop.exists(metaDir)) {
            return null;
        }
        File dataFile = new File(metaDir, INSTALL_DATA_FN);

        if (mFop.exists(dataFile)) {
            Properties installProperties = new Properties();
            try (InputStream inStream = mFop.newFileInputStream(dataFile)) {
                installProperties.load(inStream);
                return installProperties;
            }
        }
        return null;
    }

    private void cleanup(@NonNull ProgressIndicator progress) {
        mFop.deleteFileOrFolder(new File(getLocation(progress), InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Writes information used to restore the operation state if needed, then calls {@link
     * #doPrepare(File, ProgressIndicator)}
     *
     * @param progress   A {@link ProgressIndicator}, to show progress and facilitate logging.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     */
    @Override
    public final boolean prepare(@NonNull ProgressIndicator progress) {
        progress.logInfo(String.format("Preparing \"%1$s\".", getName()));
        try {
            File dest = getLocation(progress);

            mInstallProperties = readOrCreateInstallProperties(dest);
        } catch (IOException e) {
            progress.logWarning("Failed to read or create install properties file.");
            return false;
        }
        if (!updateStatus(InstallStatus.PREPARING, progress)) {
            progress.logInfo(String.format("\"%1$s\"  failed.", getName()));
            return false;
        }
        getRepoManager().installBeginning(getPackage(), this);
        boolean result = false;
        try {
            if (!InstallerUtil.checkValidPath(getLocation(progress), getRepoManager(), progress)) {
                return false;
            }

            File installTempPath = writeInstallerMetadata(progress);
            if (installTempPath == null) {
                progress.logInfo(String.format("\"%1$s\" failed.", getName()));
                return false;
            }
            File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
            if (!mFop.exists(prepareCompleteMarker)) {
                if (doPrepare(installTempPath, progress)) {
                    mFop.createNewFile(prepareCompleteMarker);
                    result = updateStatus(InstallStatus.PREPARED, progress);
                }
            } else {
                progress.logInfo("Found existing prepared package.");
                result = true;
            }
        } catch (IOException e) {
            result = false;
        } finally {
            if (!result) {
                getRepoManager().installEnded(getPackage());
                updateStatus(InstallStatus.FAILED, progress);
                // If there was a failure don't clean up the files, so we can continue if requested
                if (progress.isCanceled()) {
                    cleanup(progress);
                }
            }
        }
        progress.logInfo(String.format("\"%1$s\" %2$s.", getName(), result ? "ready" : "failed"));
        return result;
    }

    /**
     * Looks in {@code affectedPath} for an install properties file and returns the contents if
     * found. If not found, creates and populates it.
     *
     * @param affectedPath The path on which this operation acts (either to write to or delete from)
     * @return The read or created properties.
     */
    @NonNull
    private Properties readOrCreateInstallProperties(@NonNull File affectedPath)
      throws IOException {
        Properties installProperties = readInstallProperties(affectedPath);
        if (installProperties != null) {
            return installProperties;
        }
        installProperties = new Properties();

        File metaDir = new File(affectedPath, InstallerUtil.INSTALLER_DIR_FN);
        if (!mFop.exists(metaDir)) {
            mFop.mkdirs(metaDir);
        }
        File dataFile = new File(metaDir, INSTALL_DATA_FN);
        File installTempPath = FileOpUtils.getNewTempDir("BasicInstaller", mFop);
        if (installTempPath == null) {
            throw new IOException("Failed to create temp path");
        }
        installProperties.put(PATH_KEY, installTempPath.getPath());
        installProperties.put(CLASSNAME_KEY, getClass().getName());
        mFop.createNewFile(dataFile);
        try (OutputStream out = mFop.newFileOutputStream(dataFile)) {
            installProperties.store(out, null);
        }
        return installProperties;
    }

    @Nullable
    private File writeInstallerMetadata(@NonNull ProgressIndicator progress)
      throws IOException {
        File installPath = getLocation(progress);
        Properties installProperties = readOrCreateInstallProperties(installPath);
        File installTempPath = new File((String) installProperties.get(PATH_KEY));
        if (!mFop.exists(installPath) && !mFop.mkdirs(installPath) ||
          !mFop.isDirectory(installPath)) {
            progress.logWarning("Failed to create output directory: " + installPath);
            return null;
        }
        mFop.deleteOnExit(installTempPath);
        return installTempPath;
    }

    @NonNull
    @Override
    public RepoManager getRepoManager() {
        return mRepoManager;
    }

    /**
     * Registers a listener that will be called when the {@link PackageOperation.InstallStatus} of
     * this installer changes.
     */
    @Override
    public final void registerStateChangeListener(@NonNull StatusChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Gets the current {@link PackageOperation.InstallStatus} of this installer.
     */
    @Override
    @NonNull
    public final PackageOperation.InstallStatus getInstallStatus() {
        return mInstallStatus;
    }

    /**
     * Sets our status to {@code status} and notifies our listeners. If any listener throws an
     * exception we will stop processing listeners and update our status to {@code
     * InstallStatus.FAILED} (calling the listeners again with that status update).
     */
    protected final boolean updateStatus(@NonNull PackageOperation.InstallStatus status,
      @NonNull ProgressIndicator progress) {
        mInstallStatus = status;
        try {
            for (StatusChangeListener listener : mListeners) {
                try {
                    listener.statusChanged(this, progress);
                } catch (Exception e) {
                    if (status != PackageOperation.InstallStatus.FAILED) {
                        throw e;
                    }
                    // else ignore and continue with the other listeners
                }
            }
        } catch (Exception e) {
            updateStatus(PackageOperation.InstallStatus.FAILED, progress);
            return false;
        }
        return true;
    }
}

