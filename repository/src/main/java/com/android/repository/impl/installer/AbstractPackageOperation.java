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
         * Properties written to the final install folder, used to restart the installer if needed.
         */
        private Properties mInstallProperties;

        public AbstractInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
                @NonNull FileOp fop) {
            super(mgr, fop);
            mPackage = p;
        }

        @Override
        @NonNull
        public RemotePackage getPackage() {
            return mPackage;
        }


        /**
         * Finds the prepared files using the installer metadata, calls {@link
         * #doCompleteInstall(File, File, ProgressIndicator)}, and finally writes the package xml.
         *
         * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
         *                 logging.
         * @return {@code true} if the install was successful, {@code false} otherwise.
         */
        @Override
        public final boolean completeInstall(@NonNull ProgressIndicator progress) {
            if (!updateStatus(InstallStatus.INSTALLING, progress)) {
                return false;
            }
            boolean result = false;
            File dest = null;
            String installTempPath = mInstallProperties.getProperty(PATH_KEY);
            if (installTempPath == null) {
                return false;
            }
            File installTemp = new File(installTempPath);
            try {
                dest = InstallerUtil.getInstallPath(getPackage(), getRepoManager(), progress);
                result = doCompleteInstall(installTemp, dest, progress);
            } catch (IOException e) {
                result = false;
            } finally {
                if (!result && progress.isCanceled()) {
                    try {
                        cleanup(
                                InstallerUtil
                                        .getInstallPath(getPackage(), getRepoManager(), progress),
                                mFop);
                    } catch (IOException e) {
                        // ignore
                    }
                }
                result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED,
                        progress);
                getRepoManager().markInvalid();
            }
            try {
                if (result) {
                    try {
                        InstallerUtil
                                .writePackageXml(getPackage(), dest, getRepoManager(), mFop,
                                        progress);
                        mFop.delete(installTemp);
                    } catch (IOException e) {
                        progress.logWarning("Failed to update package.xml", e);
                        result = false;
                    }
                }
            } finally {
                getRepoManager().installEnded(getPackage());
                getRepoManager().markInvalid();
            }
            return result;
        }

        /**
         * Subclasses should implement this to do any completion actions required.
         *
         * @param installTemp The temporary dir in which we prepared the install.
         * @param dest        The destination into which to install the package.
         * @param progress    For logging and progress indication.
         * @return {@code true} if the install succeeded, {@code false} otherwise.
         * @see #completeInstall(ProgressIndicator)
         */
        protected abstract boolean doCompleteInstall(@NonNull File installTemp, @NonNull File dest,
                @NonNull ProgressIndicator progress);

        /**
         * Writes information used to restore the install process, then calls {@link
         * #doPrepareInstall(File, Downloader, ProgressIndicator)}
         *
         * @param downloader The {@link Downloader} used to download the archive.
         * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
         *                   logging.
         * @return {@code true} if the operation succeeded, {@code false} otherwise.
         */
        @Override
        public final boolean prepareInstall(@NonNull Downloader downloader,
                @NonNull ProgressIndicator progress) {
            try {
                File dest = InstallerUtil
                        .getInstallPath(mPackage, getRepoManager(), progress);
                mInstallProperties = readOrCreateInstallProperties(dest);
            } catch (IOException e) {
                progress.logWarning("Failed to read or create install properties file.");
                return false;
            }
            if (!updateStatus(InstallStatus.PREPARING, progress)) {
                return false;
            }
            getRepoManager().installBeginning(mPackage, this);
            boolean result = false;
            try {
                File installPath = InstallerUtil
                        .getInstallPath(mPackage, getRepoManager(), progress);
                File installTempPath = writeInstallerMetadata(installPath, progress);
                if (installTempPath == null) {
                    return false;
                }
                File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
                if (!mFop.exists(prepareCompleteMarker)) {
                    if (doPrepareInstall(installTempPath, downloader, progress)) {
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
                        try {
                            cleanup(InstallerUtil
                                    .getInstallPath(mPackage, getRepoManager(),
                                            progress), mFop);
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            return result;
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
            File metaDir = new File(installPath, InstallerUtil.INSTALLER_DIR_FN);
            if (!mFop.exists(metaDir)) {
                mFop.mkdirs(metaDir);
            }
            File dataFile = new File(metaDir, INSTALL_DATA_FN);
            Properties installProperties = new Properties();
            if (mFop.exists(dataFile)) {
                InputStream inStream = mFop.newFileInputStream(dataFile);
                try {
                    installProperties.load(inStream);
                } finally {
                    inStream.close();
                }
            } else {
                File installTempPath = FileOpUtils.getNewTempDir("BasicInstaller", mFop);
                if (installTempPath == null) {
                    throw new IOException("Failed to create temp path");
                }
                mFop.mkdirs(installTempPath);
                installProperties.put(PATH_KEY, installTempPath.getPath());
                installProperties.put(CLASSNAME_KEY, getClass().getName());
                mFop.createNewFile(dataFile);
                OutputStream out = mFop.newFileOutputStream(dataFile);
                try {
                    installProperties.store(out, null);
                } finally {
                    out.close();
                }
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
         * @param downloader      {@link Downloader} to use for fetching remote packages.
         * @param progress        For logging and progress display
         */
        protected abstract boolean doPrepareInstall(@NonNull File installTempPath,
                @NonNull Downloader downloader, @NonNull ProgressIndicator progress);

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
            InstallerUtil
                    .writePendingPackageXml(mPackage, installPath, getRepoManager(), mFop,
                            progress);
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


        /**
         * Uninstall the package.
         *
         * @param progress A {@link ProgressIndicator}. Unused by this installer.
         * @return {@code true} if the uninstall was successful, {@code false} otherwise.
         */
        @Override
        public final boolean uninstall(@NonNull ProgressIndicator progress) {
            if (!updateStatus(InstallStatus.UNINSTALL_STARTING, progress)) {
                return false;
            }

            boolean result = doUninstall(progress);
            getRepoManager().markInvalid();

            if (!updateStatus(InstallStatus.UNINSTALL_COMPLETE, progress)) {
                return false;
            }
            return result;
        }

        /**
         * Subclasses should implement this to actually remove the files for this package.
         *
         * @return {@code true} if the uninstall succeeds, {@code false} otherwise.
         */
        protected abstract boolean doUninstall(@NonNull ProgressIndicator progress);
    }

    protected AbstractPackageOperation(@NonNull RepoManager repoManager, @NonNull FileOp fop) {
        mRepoManager = repoManager;
        mFop = fop;
    }

    /**
     * Gets the {@link RepoManager} for which we're installing/uninstalling a package.
     */
    @NonNull
    protected RepoManager getRepoManager() {
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

