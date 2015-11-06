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

package com.android.repository.io;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.impl.FileOpImpl;

import java.io.File;
import java.io.IOException;

/**
 * Some convenience methods for working with {@link File}s/{@link FileOp}s.
 */
public final class FileOpUtils {

    /**
     * The standard way to create a {@link FileOp} that interacts with the real filesystem.
     */
    @NonNull
    public static FileOp create() {
        return new FileOpImpl();
    }

    /**
     * Copies a file or directory tree to the given location. {@code dest} should not exist: with
     * the file system currently looking like
     * <pre>
     *     {@code
     *     /
     *       dir1/
     *         a.txt
     *       dir2/
     *     }
     * </pre>
     * Running {@code recursiveCopy(new File("/dir1"), new File("/dir2"), fOp)} will result in an
     * exception, while {@code recursiveCopy(new File("/dir1"), new File("/dir2/foo")} will result
     * in
     * <pre>
     *     {@code
     *     /
     *       dir1/
     *         a.txt
     *       dir2/
     *         foo/
     *           a.txt
     *     }
     * </pre>
     * This is equivalent to the behavior of {@code cp -r} when the target does not exist.
     *
     * @param src  File to copy
     * @param dest Destination.
     * @param fop  The FileOp to use for file operations.
     * @throws IOException If the destination already exists, or if there is a problem copying the
     *                     files or creating directories.
     */
    public static void recursiveCopy(@NonNull File src, @NonNull File dest, @NonNull FileOp fop)
            throws IOException {
        if (fop.exists(dest)) {
            throw new IOException(dest + " already exists!");
        }
        if (fop.isDirectory(src)) {
            fop.mkdirs(dest);

            File[] children = fop.listFiles(src);
            for (File child : children) {
                File newDest = new File(dest, child.getName());
                recursiveCopy(child, newDest, fop);
            }
        } else if (fop.isFile(src)) {
            fop.copyFile(src, dest);
        }
    }

    /**
     * Moves a file or directory from one location to another. If the destination exists, it is
     * moved away, and once the operation has completed successfully, deleted. If there is a problem
     * during the copy, the original files are moved back into place.
     *
     * @param src      File to move
     * @param dest     Destination. Follows the same rules as {@link #recursiveCopy(File, File,
     *                 FileOp)}}.
     * @param fop      The FileOp to use for file operations.
     * @param progress Currently only used for error logging.
     * @throws IOException If some problem occurs during copies or directory creation.
     */
    // TODO: Seems strange to use the more-fully-featured ProgressIndicator here.
    //       Is a more general logger needed?
    public static void safeRecursiveOverwrite(@NonNull File src, @NonNull File dest,
            @NonNull FileOp fop, @NonNull ProgressIndicator progress) throws IOException {

        if (fop.exists(dest)) {

            File toDelete = getNewTempDir("FileOpUtilsToDelete", fop);
            if (toDelete == null) {
                // weird, try to delete in place
                fop.deleteFileOrFolder(dest);
            } else {
                FileOpUtils.recursiveCopy(dest, toDelete, fop);
            }
            try {
                fop.deleteFileOrFolder(dest);
                FileOpUtils.recursiveCopy(src, dest, fop);
            } catch (IOException e) {
                // this is bad
                progress.logError("Old dir was moved away, but new one failed to be moved into "
                        + "place. Trying to move old one back.");
                if (fop.exists(dest)) {
                    fop.deleteFileOrFolder(dest);
                }
                if (toDelete == null) {
                    // this is the worst case
                    progress.logError(
                            "Failed to move old dir back into place! Component was lost.");
                } else {
                    FileOpUtils.recursiveCopy(toDelete, dest, fop);
                }
                throw new IOException("failed to move new dir into place", e);
            }
            if (toDelete != null) {
                fop.deleteFileOrFolder(toDelete);
            }
        } else {
            FileOpUtils.recursiveCopy(src, dest, fop);
        }
    }

    /**
     * Creates a new subdirectory of the system temp directory. The directory will be named {@code
     * <base> + NN}, where NN makes the directory distinct from any existing directories.
     */
    @Nullable
    public static File getNewTempDir(@NonNull String base, @NonNull FileOp fileOp) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        if (!fileOp.exists(tempDir)) {
            fileOp.mkdirs(tempDir);
        }
        for (int i = 1; i < 100; i++) {
            File folder = new File(tempDir,
                    String.format("%1$s%2$02d", base, i));  //$NON-NLS-1$
            if (!fileOp.exists(folder)) {
                return folder;
            }
        }
        return null;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private FileOpUtils() {
    }
}
