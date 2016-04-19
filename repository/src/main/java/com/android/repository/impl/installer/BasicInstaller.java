/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A simple {@link Installer} that just unzips the {@code complete} version of an {@link
 * Archive} into its destination directory.
 *
 * Probably instances should be created by {@link BasicInstallerFactory}
 */
public class BasicInstaller extends AbstractPackageOperation.AbstractInstaller {

    protected BasicInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
            @NonNull FileOp fop) {
        super(p, mgr, fop);
    }

    /**
     * Downloads and unzips the complete archive for {@code p} into {@code installTempPath}.
     *
     * @see #prepareInstall(Downloader, ProgressIndicator)
     */
    @Override
    protected boolean doPrepareInstall(@NonNull File installTempPath,
      @NonNull Downloader downloader, @NonNull ProgressIndicator progress) {
        URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
        if (url == null) {
            progress.logWarning("No compatible archive found!");
            return false;
        }
        Archive archive = getPackage().getArchive();
        assert archive != null;
        try {
            File downloadLocation = new File(installTempPath, url.getFile());
            // TODO: allow resuming of partial downloads
            if (!isFileDownloaded(downloadLocation, archive, progress)) {
                downloader.downloadFully(url, downloadLocation, progress);
            }
            if (progress.isCanceled()) {
                return false;
            }
            if (!mFop.exists(downloadLocation)) {
                progress.logWarning("Failed to download package!");
                return false;
            }
            File unzip = new File(installTempPath, BasicInstallerFactory.UNZIP_DIR_FN);
            mFop.mkdirs(unzip);
            InstallerUtil.unzip(downloadLocation, unzip, mFop,
              archive.getComplete().getSize(), progress);
            if (progress.isCanceled()) {
                return false;
            }
            mFop.delete(downloadLocation);

            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning(String.format(
              "An error occurred while preparing SDK package %1$s: %2$s",
              getPackage().getDisplayName(),
              (message.isEmpty() ? "." : ": " + message + ".")), e);
        }
        return false;
    }

    private boolean isFileDownloaded(@NonNull File downloadLocation, @NonNull Archive archive,
      @NonNull ProgressIndicator progress) throws IOException {
        if (!mFop.exists(downloadLocation)) {
            return false;
        }
        progress.logInfo("Checking existing downloaded package...");
        boolean alreadyDownloaded;
        String checksum = archive.getComplete().getChecksum();
        InputStream in = new BufferedInputStream(mFop.newFileInputStream(downloadLocation));
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

    @SuppressWarnings("MethodMayBeStatic")
    protected void cleanup(@NonNull File installPath, @NonNull FileOp fop) {
        fop.deleteFileOrFolder(new File(installPath, InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Just moves the prepared files into place.
     *
     * @see #completeInstall(RemotePackage, ProgressIndicator, RepoManager, FileOp)
     */
    @Override
    protected boolean doCompleteInstall(@Nullable File installTempPath,
      @NonNull File destination, @NonNull ProgressIndicator progress) {
        if (installTempPath == null) {
            return false;
        }
        try {
            if (progress.isCanceled()) {
                return false;
            }
            // Archives must contain a single top-level directory.
            File unzipDir = new File(installTempPath, BasicInstallerFactory.UNZIP_DIR_FN);
            File[] topDirContents = mFop.listFiles(unzipDir);
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
              .logInfo(String.format("Installing %1$s in %2$s", getPackage().getDisplayName(),
                destination));

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(packageRoot, destination, mFop, progress);

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
