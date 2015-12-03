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

package com.android.repository.util;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBElement;

/**
 * Utility methods for {@link PackageInstaller} implementations.
 */
public class InstallerUtil {

    /**
     * Unzips the given zipped input stream into the given directory.
     *
     * @param in The (zipped) input stream.
     * @param out The directory into which to expand the files. Must exist.
     * @param fop The {@link FileOp} to use for file operations.
     * @param expectedSize Compressed size of the stream.
     * @param progress Currently only used for logging.
     * @throws IOException If we're unable to read or write.
     */
    public static void unzip(@NonNull InputStream in, @NonNull File out, @NonNull FileOp fop,
            long expectedSize, @NonNull ProgressIndicator progress)
            throws IOException {
        if (!fop.exists(out) || !fop.isDirectory(out)) {
            throw new IllegalArgumentException("out must exist and be a directory.");
        }
        progress.setText("Unzipping...");
        double fraction = 0;
        ZipInputStream input = new ZipInputStream(new BufferedInputStream(in));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                File entryFile = new File(out, name);
                progress.setSecondaryText(name);
                if (entry.isDirectory()) {
                    if (fop.exists(entryFile)) {
                        progress.logWarning(entryFile + " already exists");
                    }
                    else {
                        if (!fop.mkdirs(entryFile)) {
                            progress.logWarning("failed to mkdirs " + entryFile);
                        }
                    }
                }
                else {
                    if (!fop.exists(entryFile)) {
                        File parent = entryFile.getParentFile();
                        if (parent != null && !fop.exists(parent)) {
                            fop.mkdirs(parent);
                        }
                        if (!fop.createNewFile(entryFile)) {
                            throw new IOException("Failed to create file " + entryFile);
                        }
                    }

                    int size;
                    byte[] buf = new byte[8192];
                    BufferedOutputStream bos = new BufferedOutputStream(
                            fop.newFileOutputStream(entryFile));
                    try {
                        while ((size = input.read(buf)) > -1) {
                            bos.write(buf, 0, size);
                            fraction += ((double) entry.getCompressedSize() / expectedSize) *
                                    ((double) size / entry.getSize());
                            progress.setFraction(fraction);
                        }
                    }
                    finally {
                        bos.close();
                    }
                }
                input.closeEntry();
            }
        }
        finally {
            input.close();
        }
    }

    /**
     * Writes out the XML for a {@link LocalPackageImpl} corresponding to the given
     * {@link RemotePackage} to a {@code package.xml} file in {@code packageRoot}.
     *
     * @param p The package to convert to a local package and write out.
     * @param packageRoot The location to write to. Must exist and be a directory.
     * @param manager A {@link RepoManager} instance.
     * @param fop The {@link FileOp} to use for file operations.
     * @param progress Currently only used for logging.
     * @throws IOException If we fail to write the output file.
     */
    public static void writePackageXml(@NonNull RemotePackage p, @NonNull File packageRoot,
            @NonNull RepoManager manager, @NonNull FileOp fop, @NonNull ProgressIndicator progress)
            throws IOException {
        if (!fop.exists(packageRoot) || !fop.isDirectory(packageRoot)) {
            throw new IllegalArgumentException("packageRoot must exist and be a directory.");
        }
        CommonFactory factory = (CommonFactory) manager.getCommonModule().createLatestFactory();
        // Create the package.xml
        Repository repo = factory.createRepositoryType();
        LocalPackageImpl impl = LocalPackageImpl.create(p, manager);
        repo.setLocalPackage(impl);
        License l = p.getLicense();
        if (l != null) {
            repo.addLicense(l);
        }
        File packageXml = new File(packageRoot, LocalRepoLoader.PACKAGE_XML_FN);
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
                            manager.getResourceResolver(progress), progress);
        } finally {
            fos.close();
        }
    }

    /**
     * Returns a URL corresponding to {@link Archive#getComplete()} of the given
     * {@link RemotePackage}. If the url in the package is a relative url, resolves it by using the
     * prefix of the url in the {@link RepositorySource} of the package.
     *
     * @return The resolved {@link URL}, or {@code null} if the given archive location is not
     * parsable in its original or resolved form.
     */
    @Nullable
    public static URL resolveCompleteArchiveUrl(@NonNull RemotePackage p,
            @NonNull ProgressIndicator progress) {
        Archive arch = p.getArchive();
        String urlStr = arch.getComplete().getUrl();
        URL url;
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
                progress.logWarning("Failed to parse url: " + urlStr);
                return null;
            }
        }
        return url;

    }
}
