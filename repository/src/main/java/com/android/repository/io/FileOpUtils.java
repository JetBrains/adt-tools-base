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
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

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
            if (!fop.isWindows() && fop.canExecute(src)) {
                fop.setExecutablePermission(dest);
            }
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
                fop.deleteFileOrFolder(src);
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
            fop.deleteFileOrFolder(src);
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

    /**
     * Appends the given {@code segments} to the {@code base} file.
     *
     * @param base A base file, non-null.
     * @param segments Individual folder or filename segments to append to the base file.
     * @return A new file representing the concatenation of the base path with all the segments.
     */
    @NonNull
    public static File append(@NonNull File base, @NonNull String...segments) {
        for (String segment : segments) {
            base = new File(base, segment);
        }
        return base;
    }

    /**
     * Appends the given {@code segments} to the {@code base} file.
     *
     * @param base A base file path, non-empty and non-null.
     * @param segments Individual folder or filename segments to append to the base path.
     * @return A new file representing the concatenation of the base path with all the segments.
     */
    @NonNull
    public static File append(@NonNull String base, @NonNull String...segments) {
        return append(new File(base), segments);
    }
    /**
     * Computes a relative path from "toBeRelative" relative to "baseDir".
     *
     * Rule:
     * - let relative2 = makeRelative(path1, path2)
     * - then pathJoin(path1 + relative2) == path2 after canonicalization.
     *
     * Principle:
     * - let base         = /c1/c2.../cN/a1/a2../aN
     * - let toBeRelative = /c1/c2.../cN/b1/b2../bN
     * - result is removes the common paths, goes back from aN to cN then to bN:
     * - result           =              ../..../../1/b2../bN
     *
     * @param baseDir The base directory to be relative to.
     * @param toBeRelative The file or directory to make relative to the base.
     * @param fop FileOp, in this case just to determine the platform.
     * @return A path that makes toBeRelative relative to baseDir.
     * @throws IOException If drive letters don't match on Windows or path canonicalization fails.
     */

    @NonNull
    public static String makeRelative(@NonNull File baseDir, @NonNull File toBeRelative, FileOp fop)
            throws IOException {
        return makeRelativeImpl(
                baseDir.getCanonicalPath(),
                toBeRelative.getCanonicalPath(),
                fop.isWindows(),
                File.separator);
    }

    /**
     * Implementation detail of makeRelative to make it testable
     * Independently of the platform.
     */
    @VisibleForTesting
    @NonNull
    static String makeRelativeImpl(@NonNull String path1,
            @NonNull String path2,
            boolean isWindows,
            @NonNull String dirSeparator)
            throws IOException {
        if (isWindows) {
            // Check whether both path are on the same drive letter, if any.
            String p1 = path1;
            String p2 = path2;
            char drive1 = (p1.length() >= 2 && p1.charAt(1) == ':') ? p1.charAt(0) : 0;
            char drive2 = (p2.length() >= 2 && p2.charAt(1) == ':') ? p2.charAt(0) : 0;
            if (drive1 != drive2) {
                // Either a mix of UNC vs drive or not the same drives.
                throw new IOException("makeRelative: incompatible drive letters");
            }
        }

        String[] segments1 = path1.split(Pattern.quote(dirSeparator));
        String[] segments2 = path2.split(Pattern.quote(dirSeparator));

        int len1 = segments1.length;
        int len2 = segments2.length;
        int len = Math.min(len1, len2);
        int start = 0;
        for (; start < len; start++) {
            // On Windows should compare in case-insensitive.
            // Mac & Linux file systems can be both type, although their default
            // is generally to have a case-sensitive file system.
            if (( isWindows && !segments1[start].equalsIgnoreCase(segments2[start])) ||
                    (!isWindows && !segments1[start].equals(segments2[start]))) {
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = start; i < len1; i++) {
            result.append("..").append(dirSeparator);
        }
        while (start < len2) {
            result.append(segments2[start]);
            if (++start < len2) {
                result.append(dirSeparator);
            }
        }

        return result.toString();
    }

    private FileOpUtils() {
    }
}
