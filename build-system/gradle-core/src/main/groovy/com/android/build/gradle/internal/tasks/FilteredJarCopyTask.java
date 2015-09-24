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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Simple task copying a Jar file from one location to another location, while filtering
 * out a set of entries.
 *
 */
public class FilteredJarCopyTask extends SingleFileCopyTask {

    private List<String> excludes;

    @Input
    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(@NonNull List<String> excludes) {
        this.excludes = ImmutableList.copyOf(excludes);
    }

    @TaskAction
    @Override
    public void copy() throws IOException {
        if (excludes.isEmpty()) {
            super.copy();
        }

        // create Pattern Objects.
        List<Pattern> patterns = Lists.newArrayListWithCapacity(excludes.size());
        for (String exclude : excludes) {
            patterns.add(Pattern.compile(exclude));
        }

        Closer closer = Closer.create();
        byte[] buffer = new byte[4096];

        try {
            FileOutputStream fos = closer.register(new FileOutputStream(outputFile));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ZipOutputStream zos = closer.register(new ZipOutputStream(bos));

            FileInputStream fis = closer.register(new FileInputStream(inputFile));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ZipInputStream zis = closer.register(new ZipInputStream(bis));

            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (!checkEntry(patterns, name)) {
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

                // add the entry to the jar archive
                zos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        } finally {
            closer.close();
        }
    }

    private static boolean checkEntry(
            @NonNull List<Pattern> patterns,
            @NonNull String name) {

        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }
}
