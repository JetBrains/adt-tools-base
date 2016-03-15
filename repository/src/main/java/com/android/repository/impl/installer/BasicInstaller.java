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
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Properties;

/**
 * A simple {@link PackageInstaller} that just unzips the {@code complete} version of an {@link
 * Archive} into its destination directory.
 */
public class BasicInstaller implements PackageInstaller {

    private static final String PATH_KEY = "path";

    private static final String CLASSNAME_KEY = "class";

    private static final String PREPARE_COMPLETE_FN = ".prepareComplete";

    private static final String INSTALL_DATA_FN = ".installData";

    private static final String UNZIP_DIR_FN = "unzip";

    private InstallStatus mInstallStatus = InstallStatus.NOT_STARTED;

    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    /**
     * Just deletes the package.
     *
     * @param p        The {@link LocalPackage} to delete.
     * @param progress A {@link ProgressIndicator}. Unused by this installer.
     * @param manager  A {@link RepoManager} that knows about this package.
     * @param fop      The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in a
     *                 unit test.
     * @return {@code true} if the uninstall was successful, {@code false} otherwise.
     */
    @Override
    public boolean uninstall(@NonNull LocalPackage p, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop) {
        if (!updateStatus(InstallStatus.UNINSTALL_STARTING, p, progress)) {
            return false;
        }
        String path = p.getPath();
        path = path.replace(RepoPackage.PATH_SEPARATOR, File.separatorChar);
        File location = new File(manager.getLocalPath(), path);

        fop.deleteFileOrFolder(location);
        manager.markInvalid();

        if (!updateStatus(InstallStatus.UNINSTALL_COMPLETE, p, progress)) {
            return false;
        }
        return !fop.exists(location);
    }

    /**
     * Writes information used to restore the install process, then calls {@link
     * #doPrepareInstall(RemotePackage, File, Downloader, SettingsController, ProgressIndicator,
     * RepoManager, FileOp)} )}
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean prepareInstall(@NonNull RemotePackage p,
            @NonNull Downloader downloader,
            @Nullable SettingsController settings,
            @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager,
            @NonNull FileOp fop) {
        if (!updateStatus(InstallStatus.PREPARING, p, progress)) {
            return false;
        }
        manager.installBeginning(p, this);
        boolean result = false;
        try {
            File installPath = InstallerUtil.getInstallPath(p, manager, progress);
            File installTempPath = writeInstallerMetadata(p, installPath, manager, progress,
                    fop);
            if (installTempPath == null) {
                return false;
            }
            File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
            if (!fop.exists(prepareCompleteMarker)) {
                if (doPrepareInstall(p, installTempPath, downloader, settings, progress, manager,
                        fop)) {
                    fop.createNewFile(prepareCompleteMarker);
                    result = updateStatus(InstallStatus.PREPARED, p, progress);
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
                manager.installEnded(p);
                updateStatus(InstallStatus.FAILED, p, progress);
                // If there was a failure don't clean up the files, so we can continue if requested
                if (progress.isCanceled()) {
                    try {
                        cleanup(InstallerUtil.getInstallPath(p, manager, progress), fop);
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    protected File writeInstallerMetadata(@NonNull RemotePackage p, @NonNull File installPath,
            @NonNull RepoManager manager, @NonNull ProgressIndicator progress, @NonNull FileOp fop)
            throws IOException {
        Properties installProperties = readOrCreateInstallProperties(fop, installPath);
        File installTempPath = new File((String) installProperties.get(PATH_KEY));
        if (!fop.exists(installPath) && !fop.mkdirs(installPath) || !fop
                .isDirectory(installPath)) {
            progress.logWarning("Failed to create output dir: " + installPath);
            return null;
        }
        fop.deleteOnExit(installTempPath);
        InstallerUtil.writePendingPackageXml(p, installPath, manager, fop, progress);
        return installTempPath;
    }

    /**
     * Downloads and unzips the complete archive for {@code p} into {@code installTempPath}.
     * Subclasses should override this to extend {@link BasicInstaller}s behavior. If {@code false}
     * is returned, {@link #cleanup(File, FileOp)} will be run.
     *
     * @see #prepareInstall(RemotePackage, Downloader, SettingsController, ProgressIndicator,
     * RepoManager, FileOp)
     */
    protected boolean doPrepareInstall(@NonNull RemotePackage p, @NonNull File installTempPath,
            @NonNull Downloader downloader, @Nullable SettingsController settings,
            @NonNull ProgressIndicator progress, @NonNull RepoManager manager,
            @NonNull FileOp fop) {
        URL url = InstallerUtil.resolveCompleteArchiveUrl(p, progress);
        if (url == null) {
            progress.logWarning("No compatible archive found!");
            return false;
        }
        try {
            File downloadLocation = new File(installTempPath, url.getFile());
            // TODO: allow resuming of partial downloads
            if (!isFileDownloaded(p, downloadLocation, progress, fop)) {
                downloader.downloadFully(url, settings, downloadLocation, progress);
            }
            if (progress.isCanceled()) {
                return false;
            }
            if (!fop.exists(downloadLocation)) {
                progress.logWarning("Failed to download package!");
                return false;
            }
            File unzip = new File(installTempPath, UNZIP_DIR_FN);
            fop.mkdirs(unzip);
            InstallerUtil.unzip(downloadLocation, unzip, fop,
                                p.getArchive().getComplete().getSize(), progress);
            if (progress.isCanceled()) {
                return false;
            }
            fop.delete(downloadLocation);

            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning(String.format(
                    "An error occurred while preparing SDK package %1$s: %2$s", p.getDisplayName(),
                    (message.isEmpty() ? "." : ": " + message + ".")), e);
        }
        return false;
    }

    @Override
    public void registerStateChangeListener(@NonNull StatusChangeListener listener) {
        mListeners.add(listener);
    }

    @Override
    @NonNull
    public InstallStatus getInstallStatus() {
        return mInstallStatus;
    }

    private boolean updateStatus(@NonNull PackageInstaller.InstallStatus status,
      @NonNull RepoPackage p,
      @NonNull ProgressIndicator progress) {
        mInstallStatus = status;
        try {
            for (StatusChangeListener listener : mListeners) {
                try {
                    listener.statusChanged(this, p, progress);
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
            updateStatus(InstallStatus.FAILED, p, progress);
            return false;
        }
        return true;
    }


    private static boolean isFileDownloaded(@NonNull RemotePackage p,
            @NonNull File downloadLocation,
            @NonNull ProgressIndicator progress, @NonNull FileOp fop) throws IOException {
        if (!fop.exists(downloadLocation)) {
            return false;
        }
        progress.logInfo("Checking existing downloaded package...");
        boolean alreadyDownloaded;
        String checksum = p.getArchive().getComplete().getChecksum();
        InputStream in = new BufferedInputStream(fop.newFileInputStream(downloadLocation));
        String hash = hashFile(in, downloadLocation.length(), progress);
        alreadyDownloaded = checksum.equals(hash);
        return alreadyDownloaded;
    }

    @VisibleForTesting
    @NonNull
    static String hashFile(@NonNull InputStream in, long fileSize,
            @NonNull ProgressIndicator progress)
            throws IOException {
        Hasher sha1 = Hashing.sha1().newHasher();
        byte[] buf = new byte[5120];
        long totalRead = 0;
        try {
            int bytesRead;
            while ((bytesRead = in.read(buf)) > 0) {
                sha1.putBytes(buf, 0, bytesRead);
                progress.setFraction((double) totalRead / (double) fileSize);
            }
        } finally {
            in.close();
        }
        return sha1.hash().toString();
    }

    @NonNull
    private Properties readOrCreateInstallProperties(@NonNull FileOp fop, @NonNull File installPath)
            throws IOException {
        File metaDir = new File(installPath, InstallerUtil.INSTALLER_DIR_FN);
        if (!fop.exists(metaDir)) {
            fop.mkdirs(metaDir);
        }
        File dataFile = new File(metaDir, INSTALL_DATA_FN);
        Properties installProperties = new Properties();
        if (fop.exists(dataFile)) {
            InputStream inStream = fop.newFileInputStream(dataFile);
            installProperties.load(inStream);
            inStream.close();
        } else {
            File installTempPath = FileOpUtils.getNewTempDir("BasicInstaller", fop);
            if (installTempPath == null) {
                throw new IOException("Failed to create temp path");
            }
            fop.mkdirs(installTempPath);
            installProperties.put(PATH_KEY, installTempPath.getPath());
            installProperties.put(CLASSNAME_KEY, getClass().getName());
            fop.createNewFile(dataFile);
            OutputStream out = fop.newFileOutputStream(dataFile);
            installProperties.store(out, null);
            out.close();
        }
        return installProperties;
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected void cleanup(@NonNull File installPath, @NonNull FileOp fop) {
        fop.deleteFileOrFolder(new File(installPath, InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Finds the prepared files using the installer metadata, calls {@link
     * #doCompleteInstall(RemotePackage, File, File, ProgressIndicator, RepoManager, FileOp)}
     * finally writes the package xml.
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean completeInstall(@NonNull RemotePackage p,
            @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager,
            @NonNull FileOp fop) {
        if (!updateStatus(InstallStatus.INSTALLING, p, progress)) {
            return false;
        }
        boolean result = false;
        try {
            File dest = InstallerUtil.getInstallPath(p, manager, progress);
            Properties installProperties = readOrCreateInstallProperties(fop, dest);
            String installTempPath = installProperties.getProperty(PATH_KEY);
            if (installTempPath == null) {
                return false;
            }
            File installTemp = new File(installTempPath);
            result = doCompleteInstall(p, installTemp, dest, progress, manager, fop);
            InstallerUtil.writePackageXml(p, dest, manager, fop, progress);
            fop.delete(installTemp);
            manager.markInvalid();
        } catch (IOException e) {
            result = false;
        } finally {
            if (!result && progress.isCanceled()) {
                try {
                    cleanup(InstallerUtil.getInstallPath(p, manager, progress), fop);
                } catch (IOException e) {
                    // ignore
                }
            }
            result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED, p, progress);
            manager.installEnded(p);
        }
        return result;
    }

    /**
     * Subclasses should override this to extend {@link BasicInstaller}s behavior. If {@code false}
     * is returned, {@link #cleanup(File, FileOp)} will be run.
     *
     * @see #completeInstall(RemotePackage, ProgressIndicator, RepoManager, FileOp)
     */
    protected boolean doCompleteInstall(@NonNull RemotePackage p, @NonNull File installTempPath,
            @NonNull File destination, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop) {
        try {
            if (progress.isCanceled()) {
                return false;
            }
            // Archives must contain a single top-level directory.
            File unzipDir = new File(installTempPath, UNZIP_DIR_FN);
            File[] topDirContents = fop.listFiles(unzipDir);
            File packageRoot;
            if (topDirContents.length != 1) {
                // TODO: we should be consistent and only support packages with a single top-level
                // directory, but right now haxm doesn't have one. Put this check back when it's
                // fixed.
                // throw new IOException("Archive didn't have single top level directory");
                packageRoot = unzipDir;
            } else {
                packageRoot = topDirContents[0];
            }

            progress
                    .logInfo(String.format("Installing %1$s in %2$s", p.getDisplayName(),
                            destination));

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(packageRoot, destination, fop, progress);

            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning("An error occurred during installation" +
                    (message.isEmpty() ? "." : ": " + message + "."), e);
        } finally {
            progress.setFraction(1);
        }

        return false;
    }
}
