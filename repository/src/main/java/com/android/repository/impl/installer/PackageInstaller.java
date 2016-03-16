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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
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
 * A facility for downloading and installing and uninstalling packages.
 */
public abstract class PackageInstaller {

    /**
     * Key used in the properties file for the temporary path.
     */
    private static final String PATH_KEY = "path";

    /**
     * The concrete type of the installer.
     * TODO: do we actually need this?
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
    private InstallStatus mInstallStatus = InstallStatus.NOT_STARTED;

    /**
     * Listeners that will be notified when the status changes.
     */
    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    /**
     * The package we're going to install or uninstall.
     */
    private final RepoPackage mPackage;

    private final RepoManager mRepoManager;

    protected final FileOp mFop;

    /**
     * Properties written to the final install folder, used to restart the installer if needed.
     */
    private Properties mInstallProperties;

    public enum InstallStatus {
        /** This installer hasn't started yet */
        NOT_STARTED,
        /**
         * This installer is in the process of preparing the component for install. No changes are
         * made to the SDK during this phase.
         */
        PREPARING,
        /** The steps that can be taken without affecting the installed SDK have completed. */
        PREPARED,
        /** The SDK is being modified. */
        INSTALLING,
        /** The installation has completed successfully. */
        FAILED,
        /** The installation has ended unsuccessfully. */
        COMPLETE,
        /** Uninstall is starting. */
        UNINSTALL_STARTING,
        /** Uninstall has completed. */
        UNINSTALL_COMPLETE
    }

    public PackageInstaller(RepoPackage p, RepoManager repoManager, FileOp fop) {
        mPackage = p;
        mRepoManager = repoManager;
        mFop = fop;
    }

    /**
     * Uninstall the package.
     *
     * @param progress A {@link ProgressIndicator}. Unused by this installer.
     *                 unit test.
     * @return {@code true} if the uninstall was successful, {@code false} otherwise.
     */
    public boolean uninstall(@NonNull ProgressIndicator progress) {
        assert mPackage instanceof LocalPackage;
        if (!updateStatus(InstallStatus.UNINSTALL_STARTING, progress)) {
            return false;
        }

        boolean result = doUninstall(progress);
        mRepoManager.markInvalid();

        if (!updateStatus(InstallStatus.UNINSTALL_COMPLETE, progress)) {
            return false;
        }
        return result;
    }

    @NonNull
    public RepoPackage getPackage() {
        return mPackage;
    }

    @NonNull
    protected RepoManager getRepoManager() {
        return mRepoManager;
    }

    /**
     * Subclasses should implement this to actually remove the files for this package.
     *
     * @return {@code true} if the uninstall succeeds, {@code false} otherwise.
     */
    protected abstract boolean doUninstall(@NonNull ProgressIndicator progress);

    /**
     * Completes the installation. This should include anything that actually affects the installed
     * SDK or requires user interaction.
     *
     * Finds the prepared files using the installer metadata, calls {@link
     * #doCompleteInstall(File, File, ProgressIndicator)}, and finally writes the package xml.
     *
     * @param p          The {@link RemotePackage} to install.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     * @param manager    A {@link RepoManager} that knows about this package.
     * @param fop        The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in
     *                   a unit test.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    public final boolean completeInstall(@NonNull RemotePackage p,
            @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager,
            @NonNull FileOp fop) {
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
            dest = InstallerUtil.getInstallPath(p, manager, progress);
            result = doCompleteInstall(installTemp, dest, progress);
        } catch (IOException e) {
            result = false;
        } finally {
            if (!result && progress.isCanceled()) {
                try {
                    cleanup(InstallerUtil.getInstallPath(p, manager, progress), fop);
                }
                catch (IOException e) {
                    // ignore
                }
            }
            result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED,
                                   progress);
            manager.markInvalid();
        }
        try {
            if (result) {
                try {
                    InstallerUtil.writePackageXml(p, dest, manager, fop, progress);
                    fop.delete(installTemp);
                }
                catch (IOException e) {
                    progress.logWarning("Failed to update package.xml", e);
                    result = false;
                }
            }
        }
        finally {
            manager.installEnded(p);
            manager.markInvalid();
        }
        return result;
    }

    /**
     * Subclasses should implement this to do any completion actions required.
     * @see #completeInstall(RemotePackage, ProgressIndicator, RepoManager, FileOp)
     *
     * @param installTemp The temporary dir in which we prepared the install.
     * @param dest The destination into which to install the package.
     * @param progress For logging and progress indication.
     * @return {@code true} if the install succeeded, {@code false} otherwise.
     */
    protected abstract boolean doCompleteInstall(File installTemp, File dest,
            ProgressIndicator progress);

    /**
     * Prepares the package for installation. This includes downloading, unzipping, and anything
     * else that can be done without affecting the installed SDK or other state.
     *
     * Writes information used to restore the install process, then calls {@link
     * #doPrepareInstall(File, Downloader, SettingsController, ProgressIndicator)}
     *
     * @param downloader The {@link Downloader} used to download the archive.
     * @param settings   The {@link SettingsController} to provide any settings needed.
     * @param progress   A {@link ProgressIndicator}, to show install progress and facilitate
     *                   logging.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     */
    public final boolean prepareInstall(@NonNull Downloader downloader,
            @Nullable SettingsController settings,
            @NonNull ProgressIndicator progress) {
        assert mPackage instanceof RemotePackage;
        try {
            File dest = InstallerUtil
              .getInstallPath((RemotePackage) mPackage, mRepoManager, progress);
            mInstallProperties = readOrCreateInstallProperties(dest);
        }
        catch (IOException e) {
            progress.logWarning("Failed to read or create install properties file.");
            return false;
        }
        if (!updateStatus(InstallStatus.PREPARING, progress)) {
            return false;
        }
        mRepoManager.installBeginning((RemotePackage)mPackage, this);
        boolean result = false;
        try {
            File installPath = InstallerUtil
              .getInstallPath((RemotePackage) mPackage, mRepoManager, progress);
            File installTempPath = writeInstallerMetadata(installPath, progress);
            if (installTempPath == null) {
                return false;
            }
            File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
            if (!mFop.exists(prepareCompleteMarker)) {
                if (doPrepareInstall(installTempPath, downloader, settings, progress)) {
                    mFop.createNewFile(prepareCompleteMarker);
                    result = updateStatus(InstallStatus.PREPARED, progress);
                }
            } else {
                progress.logInfo("Found existing prepared package.");
                result = true;
            }
        }
        catch (IOException e) {
            result = false;
        }
        finally {
            if (!result) {
                mRepoManager.installEnded((RemotePackage)mPackage);
                updateStatus(InstallStatus.FAILED, progress);
                // If there was a failure don't clean up the files, so we can continue if requested
                if (progress.isCanceled()) {
                    try {
                        cleanup(InstallerUtil
                          .getInstallPath((RemotePackage) mPackage, mRepoManager, progress), mFop);
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        return result;
    }

    private void cleanup(File installPath, FileOp fop) {
        fop.deleteFileOrFolder(new File(installPath, InstallerUtil.INSTALLER_DIR_FN));
        doCleanup(fop);
    }

    /**
     * Subclasses can override this if they need to do any specific cleanup.
     */
    protected void doCleanup(FileOp fop) {
    }

    /**
     * Subclasses should override this to download and prepare a package for installation. No
     * modification to the actual SDK should happen during this time.
     *
     * @param installTempPath The dir that should be used for any intermediate processing.
     * @param downloader {@link Downloader} to use for fetching remote packages.
     * @param settings {@link SettingsController} to use for download settings.
     * @param progress For logging and progress display
     * @return
     */
    protected abstract boolean doPrepareInstall(@NonNull File installTempPath,
      @NonNull Downloader downloader, @Nullable SettingsController settings,
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
        InstallerUtil
          .writePendingPackageXml((RemotePackage) mPackage, installPath, mRepoManager, mFop,
            progress);
        return installTempPath;
    }

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of this installer
     * changes.
     */
    public final void registerStateChangeListener(@NonNull StatusChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @NonNull
    public final InstallStatus getInstallStatus() {
        return mInstallStatus;
    }

    protected final boolean updateStatus(@NonNull PackageInstaller.InstallStatus status,
            @NonNull ProgressIndicator progress) {
        mInstallStatus = status;
        try {
            for (StatusChangeListener listener : mListeners) {
                try {
                    listener.statusChanged(this, progress);
                }
                catch (Exception e) {
                    if (status != InstallStatus.FAILED) {
                        throw e;
                    }
                    // else ignore and continue with the other listeners
                }
            }
        }
        catch (Exception e) {
            updateStatus(InstallStatus.FAILED, progress);
            return false;
        }
        return true;
    }

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
            installProperties.load(inStream);
            inStream.close();
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
            installProperties.store(out, null);
            out.close();
        }
        return installProperties;
    }

    /**
     * A listener that will be called when the {@link #getInstallStatus() status} of this installer
     * changes.
     */
    public interface StatusChangeListener {

        void statusChanged(@NonNull PackageInstaller installer, @NonNull ProgressIndicator progress)
                throws StatusChangeListenerException;
    }

    public static class StatusChangeListenerException extends Exception {
        public StatusChangeListenerException(Exception e) {
            super(e);
        }

        public StatusChangeListenerException(String reason) {
            super(reason);
        }
    }
}

