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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.builder.packaging.ZipAbortException;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.io.Closer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Jar Merger class.
 */
public class JarMerger {

    private final byte[] buffer = new byte[8192];

    @NonNull
    private final ILogger logger = LoggerWrapper.getLogger(JarMerger.class);

    @NonNull
    private final File jarFile;
    private Closer closer;
    private JarOutputStream jarOutputStream;

    private ZipEntryFilter filter;

    public JarMerger(@NonNull File jarFile) throws IOException {
        this.jarFile = jarFile;
    }

    private void init() throws IOException {
        if (closer == null) {
            FileUtils.mkdirs(jarFile.getParentFile());

            closer = Closer.create();

            FileOutputStream fos = closer.register(new FileOutputStream(jarFile));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            jarOutputStream = closer.register(new JarOutputStream(bos));
        }
    }

    /**
     * Sets a list of regex to exclude from the jar.
     */
    public void setFilter(@NonNull ZipEntryFilter filter) {
        this.filter = filter;
    }

    public void addFolder(@NonNull File folder) throws IOException {
        init();
        try {
            addFolder(folder, "");
        } catch (ZipAbortException e) {
            throw new IOException(e);
        }
    }

    private void addFolder(@NonNull File folder, @NonNull String path)
            throws IOException, ZipAbortException {
        logger.verbose("addFolder(%1$s, %2$s)", folder, path);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String entryPath = path + file.getName();
                    if (filter == null || filter.checkEntry(entryPath)) {
                        logger.verbose("addFolder(%1$s, %2$s): entry %3$s", folder, path, entryPath);
                        // new entry
                        jarOutputStream.putNextEntry(new JarEntry(entryPath));

                        // put the file content
                        try (Closer localCloser = Closer.create()) {
                            FileInputStream fis = localCloser.register(new FileInputStream(file));
                            int count;
                            while ((count = fis.read(buffer)) != -1) {
                                jarOutputStream.write(buffer, 0, count);
                            }
                        }

                        // close the entry
                        jarOutputStream.closeEntry();
                    }
                } else if (file.isDirectory()) {
                    addFolder(file, path + file.getName() + "/");
                }
            }
        }
    }

    public void addJar(@NonNull File file) throws IOException {
        addJar(file, false);
    }

    public void addJar(@NonNull File file, boolean removeEntryTimestamp) throws IOException {
        logger.verbose("addJar(%1$s)", file);
        init();

        try (Closer localCloser = Closer.create()) {
            FileInputStream fis = localCloser.register(new FileInputStream(file));
            ZipInputStream zis = localCloser.register(new ZipInputStream(fis));

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (filter != null && !filter.checkEntry(name)) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }
                if (removeEntryTimestamp) {
                    newEntry.setTime(0);
                }

                // add the entry to the jar archive
                logger.verbose("addJar(%1$s): entry %2$s", file, name);
                jarOutputStream.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    jarOutputStream.write(buffer, 0, count);
                }

                // close the entries for this file
                jarOutputStream.closeEntry();
                zis.closeEntry();
            }
        } catch (ZipAbortException e) {
            throw new IOException(e);
        }
    }

    public void addEntry(@NonNull String path, @NonNull byte[] bytes) throws IOException {
        init();

        jarOutputStream.putNextEntry(new JarEntry(path));
        jarOutputStream.write(bytes);
        jarOutputStream.closeEntry();
    }

    public void close() throws IOException {
        if (closer != null) {
            closer.close();
        }
    }
}
