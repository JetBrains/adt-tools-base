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

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * A simple {@link PackageInstaller} that just unzips the {@code complete} version of an {@link
 * Archive} into its destination directory.
 */
public class BasicInstaller implements PackageInstaller {

    /**
     * Just deletes the package.
     *
     * @param p        The {@link LocalPackage} to delete.
     * @param progress A {@link ProgressIndicator}. Unused by this installer.
     * @param manager  A {@link RepoManager} that knows about this package.
     * @param fop      The {@link FileOp} to use. Should be {@link FileOpUtils#create()} if not in
     *                 a unit test.
     * @return {@code true} if the uninstall was successful, {@code false} otherwise.
     */
    @Override
    public boolean uninstall(@NonNull LocalPackage p, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop) {
        String path = p.getPath();
        path = path.replace(RepoPackage.PATH_SEPARATOR, File.separatorChar);
        File location = new File(manager.getLocalPath(), path);

        fop.deleteFileOrFolder(location);
        manager.markInvalid();

        return !fop.exists(location);
    }

    /**
     * Installs the package by unzipping into its {@code path}.
     *
     * {@inheritDoc}
     */
    @Override
    public boolean install(@NonNull RemotePackage p, @NonNull Downloader downloader,
            @Nullable SettingsController settings, @NonNull ProgressIndicator progress,
            @NonNull RepoManager manager, @NonNull FileOp fop) {
        URL url = InstallerUtil.resolveCompleteArchiveUrl(p, progress);
        if (url == null) {
            return false;
        }
        try {
            String path = p.getPath();
            path = path.replace(RepoPackage.PATH_SEPARATOR, File.separatorChar);
            File dest = new File(manager.getLocalPath(), path);
            if (!InstallerUtil.checkValidPath(dest, manager, progress)) {
                return false;
            }

            File in = downloader.downloadFully(url, settings, progress);
            if (in == null) {
                progress.logWarning("Download failed!");
                return false;
            }

            File out = FileOpUtils.getNewTempDir("BasicInstaller", fop);
            if (out == null || !fop.mkdirs(out)) {
                throw new IOException("Failed to create temp dir");
            }
            fop.deleteOnExit(out);
            progress.logInfo(String.format("Installing %1$s in %2$s", p.getDisplayName(), dest));
            Archive archive = p.getArchive();
            if (archive == null) {
                progress.logWarning("No compatible archives found!");
                return false;
            }
            InstallerUtil.unzip(in, out, fop, archive.getComplete().getSize(), progress);
            fop.delete(in);

            // Archives must contain a single top-level directory.
            File[] topDirContents = fop.listFiles(out);
            File packageRoot;
            if (topDirContents.length != 1) {
                // TODO: we should be consistent and only support packages with a single top-level
                // directory, but right now haxm doesn't have one. Put this check back when it's
                // fixed.
                // throw new IOException("Archive didn't have single top level directory");
                packageRoot = out;
            }
            else {
                packageRoot = topDirContents[0];
            }

            InstallerUtil.writePackageXml(p, packageRoot, manager, fop, progress);

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(packageRoot, dest, fop, progress);
            manager.markInvalid();
            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            progress.logWarning("An error occurred during installation" +
                    (message.isEmpty() ? "." : ": " + message + "."), e);
        }

        return false;
    }
}
