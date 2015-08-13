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

package com.android.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public class FileUtils {
    public static boolean deleteFolder(final File folder) {
        if (!folder.exists()) {
            return true;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        return folder.delete();
    }

    public static void copyFile(File from, File to) throws IOException {
        to = new File(to, from.getName());
        if (from.isDirectory()) {
            if (!to.exists()) {
                to.mkdirs();
            }

            File[] children = from.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyFile(child, to);
                }
            }
        } else if (from.isFile()) {
            Files.copy(from, to);
        }
    }

    public static String relativePath(@NonNull File file, @NonNull File dir) {
        checkArgument(file.isFile(), "%s is not a file.", file.getPath());
        checkArgument(dir.isDirectory(), "%s is not a directory.", dir.getPath());

        return dir.toURI().relativize(file.toURI()).getPath();
    }

    public static String sha1(@NonNull File file) throws IOException {
        return Hashing.sha1().hashBytes(Files.toByteArray(file)).toString();
    }
}
