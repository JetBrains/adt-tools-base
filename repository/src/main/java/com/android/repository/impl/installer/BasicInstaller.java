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
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.meta.*;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBElement;

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
        path = path.replaceAll(RepoPackage.PATH_SEPARATOR, File.separator);
        File location = new File(manager.getLocalPath(), path);

        fop.deleteFileOrFolder(location);

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
        Archive arch = p.getArchive();
        String urlStr = arch.getComplete().getUrl();
        URL url;
        String originalUrl = urlStr;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            // If we don't have a real URL, it could be a relative URL. Pick up the URL prefix
            // from the source.
            try {
                String sourceUrl = p.getSource().getUrl();
                if (!sourceUrl.endsWith("/")) {
                    sourceUrl = sourceUrl.substring(0, sourceUrl.lastIndexOf('/') + 1);
                }
                urlStr = sourceUrl + urlStr;
                url = new URL(urlStr);
            } catch (MalformedURLException e2) {
                progress.logWarning("Failed to parse absolute url: " + originalUrl + "\n"
                        + "or as a relative url: " + urlStr);
                return false;
            }
        }
        try {
            String path = p.getPath();
            path = path.replaceAll(RepoPackage.PATH_SEPARATOR, File.separator);
            File dest = new File(manager.getLocalPath(), path);

            ZipInputStream input = new ZipInputStream(downloader.download(url, settings, progress));

            File out = FileOpUtils.getNewTempDir("BasicInstaller", fop);
            if (out == null || !fop.mkdirs(out)) {
                throw new IOException("Failed to create temp dir");
            }

            // Unzip.
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File entryFile = new File(out, entry.getName());
                if (entry.isDirectory()) {
                    if (fop.exists(entryFile)) {
                        progress.logWarning(entryFile + " already exists");
                    } else {
                        if (!fop.mkdirs(entryFile)) {
                            throw new IOException("failed to mkdirs " + entryFile);
                        }
                    }
                } else {
                    if (!fop.exists(entryFile) && !fop.createNewFile(entryFile)) {
                        throw new IOException("Failed to create file " + entryFile);
                    }
                    byte[] bytes = ByteStreams.toByteArray(input);
                    if (bytes != null) {
                        OutputStream os = fop.newFileOutputStream(entryFile);
                        os.write(bytes);
                        os.close();
                    }
                }

            }

            // Archives must contain a single top-level directory.
            File[] topDirContents = fop.listFiles(out);
            if (topDirContents.length != 1) {
                throw new IOException("Archive didn't have single top level directory");
            }

            CommonFactory factory = (CommonFactory)manager.getCommonModule().createLatestFactory();
            // Create the package.xml
            Repository repo = factory.createRepositoryType();
            LocalPackageImpl impl = LocalPackageImpl.create(p, manager);
            repo.setLocalPackage(impl);
            License l = p.getLicense();
            if (l != null) {
                repo.addLicense(l);
            }
            File packageXml = new File(topDirContents[0], LocalRepoLoader.PACKAGE_XML_FN);
            OutputStream fos = fop.newFileOutputStream(packageXml);
            TypeDetails typeDetails = impl.getTypeDetails();
            JAXBElement<Repository> element;
            if (typeDetails != null) {
                // If we have a details type, create the associated repo type.
                element = typeDetails.createFactory().generateElement(repo);
            } else {
                // Otherwise create a generic repo.
                element = factory.generateElement(repo);
            }
            try {
                SchemaModuleUtil
                        .marshal(element, p.getSource().getPermittedModules(), fos,
                                manager.getResourceResolver(), progress);
            } finally {
                fos.close();
            }

            // Move the final unzipped archive into place.
            FileOpUtils.safeRecursiveOverwrite(topDirContents[0], dest, fop, progress);
            return true;
        } catch (IOException e) {
            progress.logWarning("An error occurred during installation.", e);
        }

        return false;
    }
}
