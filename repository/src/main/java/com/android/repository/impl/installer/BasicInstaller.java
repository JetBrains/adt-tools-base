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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

/**
 * A simple {@link Installer} that just unzips the {@code complete} version of an {@link
 * Archive} into its destination directory.
 *
 * Probably instances should be created by {@link BasicInstallerFactory}
 */
class BasicInstaller extends AbstractInstaller {
    private static final String FN_UNZIP_DIR = "unzip";

    BasicInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
      @NonNull Downloader downloader, @NonNull FileOp fop) {
        super(p, mgr, downloader, fop);
    }

    /**
     * Downloads and unzips the complete archive for {@code p} into {@code installTempPath}.
     *
     * @see #prepare(ProgressIndicator)
     */
    @Override
    protected boolean doPrepare(@NonNull File installTempPath,
      @NonNull ProgressIndicator progress) {
        URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
        if (url == null) {
            progress.logWarning("No compatible archive found!");
            return false;
        }
        Archive archive = getPackage().getArchive();
        assert archive != null;
        try {
            String fileName = url.getPath();
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            File downloadLocation = new File(installTempPath, fileName);
            // TODO: allow resuming of partial downloads
            String checksum = archive.getComplete().getChecksum();
            getDownloader().downloadFully(url, downloadLocation, checksum, progress);
            if (progress.isCanceled()) {
                return false;
            }
            if (!mFop.exists(downloadLocation)) {
                progress.logWarning("Failed to download package!");
                return false;
            }
            File unzip = new File(installTempPath, FN_UNZIP_DIR);
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
              "An error occurred while preparing SDK package %1$s%2$s",
              getPackage().getDisplayName(),
              (message.isEmpty() ? "." : ": " + message + ".")), e);
        }
        return false;
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected void cleanup(@NonNull File installPath, @NonNull FileOp fop) {
        fop.deleteFileOrFolder(new File(installPath, InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Just moves the prepared files into place.
     *
     * @see #complete(ProgressIndicator)
     */
    @Override
    protected boolean doComplete(@Nullable File installTempPath,
            @NonNull ProgressIndicator progress) {
        if (installTempPath == null) {
            return false;
        }
        try {
            if (progress.isCanceled()) {
                return false;
            }
            // Archives must contain a single top-level directory.
            File unzipDir = new File(installTempPath, FN_UNZIP_DIR);
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
                getLocation(progress)));

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(packageRoot, getLocation(progress), mFop, progress);

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
