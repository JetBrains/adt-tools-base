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
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
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
     * Listeners that will be notified when the status changes.
     */
    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    private final RepoManager mRepoManager;

    protected final FileOp mFop;

    /**
     * Framework for an installer that creates a temporary directory, writes package.xml when it's
     * done, and can resume if partly done.
     */
    public abstract static class AbstractInstaller extends AbstractPackageOperation
            implements Installer {

        /**
         * The package we're going to install or uninstall.
         */
        private final RemotePackage mPackage;

        /**
         * The {@link Downloader} we'll use to download the archive.
         */
        private final Downloader mDownloader;

        /**
         * Properties written to the final install folder, used to restart the installer if needed.
         */
        private Properties mInstallProperties;

        public AbstractInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
                @NonNull Downloader downloader, @NonNull FileOp fop) {
            super(mgr, fop);
            mPackage = p;
            mDownloader = downloader;
        }

        @Override
        @NonNull
        public RemotePackage getPackage() {
            return mPackage;
        }

        @Override
        @NonNull
        public final File getLocation(@NonNull ProgressIndicator progress) {
            return mPackage.getInstallDir(getRepoManager(), progress);
        }

        protected Downloader getDownloader() {
            return mDownloader;
        }

        /**
         * Finds the prepared files using the installer metadata, calls {@link
         * #doComplete(File, File, ProgressIndicator)}, and finally writes the package xml.
         *
         * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
         *                 logging.
         * @return {@code true} if the install was successful, {@code false} otherwise.
         */
        @Override
        public final boolean complete(@NonNull ProgressIndicator progress) {
            progress.logInfo(String.format("Finishing installation of %1$s.",
                    getPackage().getDisplayName()));
            if (!updateStatus(InstallStatus.RUNNING, progress)) {
                progress.setFraction(1);
                progress.setIndeterminate(false);
                progress.logInfo(String.format("Installation of %1$s failed.",
                        getPackage().getDisplayName()));
                return false;
            }
            boolean result = false;
            File dest = null;
            if (mInstallProperties == null) {
                try {
                    mInstallProperties = readInstallProperties(
                            getPackage().getInstallDir(getRepoManager(), progress));
                } catch (IOException e) {
                    // We won't have a temp path, but try to continue anyway
                }
            }
            String installTempPath = null;
            if (mInstallProperties != null) {
                installTempPath = mInstallProperties.getProperty(PATH_KEY);
            }
            File installTemp = installTempPath == null ? null : new File(installTempPath);
            try {
                dest = getPackage().getInstallDir(getRepoManager(), progress);
                // Re-validate the install path, in case something was changed since prepare.
                if (!InstallerUtil.checkValidPath(dest, getRepoManager(), progress)) {
                    return false;
                }

                result = doComplete(installTemp, dest, progress);
            } finally {
                if (!result && progress.isCanceled()) {
                    cleanup(getPackage().getInstallDir(getRepoManager(), progress), mFop);
                }
                result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED,
                        progress);
                getRepoManager().markInvalid();
            }
            try {
                if (result) {
                    try {
                        InstallerUtil.writePackageXml(getPackage(), dest, getRepoManager(), mFop,
                                progress);
                        if (installTemp != null) {
                            mFop.deleteFileOrFolder(installTemp);
                        }
                    } catch (IOException e) {
                        progress.logWarning("Failed to update package.xml", e);
                        result = false;
                    }
                }
            } finally {
                getRepoManager().installEnded(getPackage());
                getRepoManager().markInvalid();
            }
            progress.setFraction(1);
            progress.setIndeterminate(false);
            progress.logInfo(String.format("Installation of %1$s %2$s.",
                    getPackage().getDisplayName(), result ? "complete" : "failed"));
            return result;
        }

        /**
         * Subclasses should implement this to do any completion actions required.
         *
         * @param installTemp The temporary dir in which we prepared the install. May be
         *                    {@code null} if for example the installer removed the installer
         *                    properties file, but should not be normally.
         * @param dest        The destination into which to install the package.
         * @param progress    For logging and progress indication.
         * @return {@code true} if the install succeeded, {@code false} otherwise.
         * @see #complete(ProgressIndicator)
         */
        protected abstract boolean doComplete(@Nullable File installTemp, @NonNull File dest,
                @NonNull ProgressIndicator progress);

        /**
         * Writes information used to restore the install process, then calls {@link
         * #doPrepare(File, ProgressIndicator)}
         *
         * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
         *                   logging.
         * @return {@code true} if the operation succeeded, {@code false} otherwise.
         */
        @Override
        public final boolean prepare(@NonNull ProgressIndicator progress) {
            progress.logInfo(String.format("Preparing installation of %1$s.",
                    getPackage().getDisplayName()));
            try {
                File dest = mPackage.getInstallDir(getRepoManager(), progress);

                mInstallProperties = readOrCreateInstallProperties(dest);
            } catch (IOException e) {
                progress.logWarning("Failed to read or create install properties file.");
                return false;
            }
            if (!updateStatus(InstallStatus.PREPARING, progress)) {
                progress.logInfo(String.format("Installation of %1$s failed.",
                        getPackage().getDisplayName()));
                return false;
            }
            getRepoManager().installBeginning(mPackage, this);
            boolean result = false;
            try {
                File installPath = mPackage.getInstallDir(getRepoManager(), progress);
                if (!InstallerUtil.checkValidPath(installPath, getRepoManager(), progress)) {
                    return false;
                }

                File installTempPath = writeInstallerMetadata(installPath, progress);
                if (installTempPath == null) {
                    progress.logInfo(String.format("Installation of %1$s failed.",
                            getPackage().getDisplayName()));
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
                    getRepoManager().installEnded(mPackage);
                    updateStatus(InstallStatus.FAILED, progress);
                    // If there was a failure don't clean up the files, so we can continue if requested
                    if (progress.isCanceled()) {
                        cleanup(mPackage.getInstallDir(getRepoManager(), progress), mFop);
                    }
                }
            }
            progress.logInfo(String.format("Installation of %1$s %2$s.",
                    getPackage().getDisplayName(), result ? "ready" : "failed"));
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

        /**
         * Looks in {@code installPath} for an install properties file and returns the contents if
         * found. If not found, creates and populates it.
         *
         * @param installPath The path in which the package will be installed
         * @return The read or created properties.
         */
        @NonNull
        private Properties readOrCreateInstallProperties(@NonNull File installPath)
                throws IOException {
            Properties installProperties = readInstallProperties(installPath);
            if (installProperties != null) {
                return installProperties;
            }
            installProperties = new Properties();

            File metaDir = new File(installPath, InstallerUtil.INSTALLER_DIR_FN);
            if (!mFop.exists(metaDir)) {
                mFop.mkdirs(metaDir);
            }
            File dataFile = new File(metaDir, INSTALL_DATA_FN);
            File installTempPath = FileOpUtils.getNewTempDir("BasicInstaller", mFop);
            if (installTempPath == null) {
                throw new IOException("Failed to create temp path");
            }
            mFop.mkdirs(installTempPath);
            installProperties.put(PATH_KEY, installTempPath.getPath());
            installProperties.put(CLASSNAME_KEY, getClass().getName());
            mFop.createNewFile(dataFile);
            try (OutputStream out = mFop.newFileOutputStream(dataFile)) {
                installProperties.store(out, null);
            }
            return installProperties;
        }

        private void cleanup(@NonNull File installPath, @NonNull FileOp fop) {
            fop.deleteFileOrFolder(new File(installPath, InstallerUtil.INSTALLER_DIR_FN));
            doCleanup(fop);
        }

        /**
         * Subclasses can override this if they need to do any specific cleanup.
         */
        protected void doCleanup(@NonNull FileOp fop) {
        }

        /**
         * Subclasses should override this to download and prepare a package for installation. No
         * modification to the actual SDK should happen during this time.
         *
         * @param installTempPath The dir that should be used for any intermediate processing.
         * @param progress        For logging and progress display
         */
        protected abstract boolean doPrepare(@NonNull File installTempPath,
                @NonNull ProgressIndicator progress);

        @Nullable
        protected File writeInstallerMetadata(@NonNull File installPath,
                @NonNull ProgressIndicator progress)
                throws IOException {
            Properties installProperties = readOrCreateInstallProperties(installPath);
            File installTempPath = new File((String) installProperties.get(PATH_KEY));
            if (!mFop.exists(installPath) && !mFop.mkdirs(installPath) ||
                    !mFop.isDirectory(installPath)) {
                progress.logWarning("Failed to create output dir: " + installPath);
                return null;
            }
            mFop.deleteOnExit(installTempPath);
            return installTempPath;
        }
    }

    /**
     * Framework for a basic uninstaller that keeps track of its status and invalidates the list of
     * installed packages when complete.
     */
    public abstract static class AbstractUninstaller extends AbstractPackageOperation
            implements Uninstaller {

        private final LocalPackage mPackage;

        public AbstractUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr,
                @NonNull FileOp fop) {
            super(mgr, fop);
            mPackage = p;
        }

        @Override
        @NonNull
        public LocalPackage getPackage() {
            return mPackage;
        }

        @NonNull
        @Override
        public final File getLocation(@NonNull ProgressIndicator progress) {
            return mPackage.getLocation();
        }

        @Override
        public final boolean prepare(@NonNull ProgressIndicator progress) {
            progress.logInfo(String.format("Preparing uninstall of %1$s.",
                    getPackage().getDisplayName()));
            boolean result;
            result = updateStatus(InstallStatus.PREPARING, progress);
            if (result && !doPrepare(progress)) {
                updateStatus(InstallStatus.FAILED, progress);
                result = false;
            }
            if (result && !updateStatus(InstallStatus.PREPARED, progress)) {
                result = false;
            }
            progress.logInfo(
                    String.format("Uninstallation of %1$s %2$s.", getPackage().getDisplayName(),
                            result ? "ready" : "failed"));
            return result;
        }

        @Override
        public final boolean complete(@NonNull ProgressIndicator progress) {
            progress.logInfo(String.format("Uninstalling %1$s.",
                    getPackage().getDisplayName()));
            boolean result = updateStatus(InstallStatus.RUNNING, progress);

            if (result) {
                result = doComplete(progress);
            }
            getRepoManager().markInvalid();
            if (!result) {
                updateStatus(InstallStatus.FAILED, progress);
            }
            else if (!updateStatus(InstallStatus.COMPLETE, progress)) {
                result = false;
            }

            progress.logInfo(String.format("Uninstallation of %1$s %2$s.",
                    getPackage().getDisplayName(), result ? "complete" : "failed"));
            return true;
        }

        /**
         * Subclasses should implement this to do any preparation before uninstalling the package.
         *
         * @return {@code true} if the preparation succeeds, {@code false} otherwise.
         */
        protected abstract boolean doPrepare(@NonNull ProgressIndicator progress);

        /**
         * Subclasses should implement this to actually remove the files for this package.
         *
         * @return {@code true} if the uninstall succeeds, {@code false} otherwise.
         */
        protected abstract boolean doComplete(@NonNull ProgressIndicator progress);
    }

    protected AbstractPackageOperation(@NonNull RepoManager repoManager, @NonNull FileOp fop) {
        mRepoManager = repoManager;
        mFop = fop;
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

