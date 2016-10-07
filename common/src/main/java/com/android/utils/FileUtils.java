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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess") // These are utility methods, meant to be public.
public final class FileUtils {

    private FileUtils() {}

    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);
    private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");
    private static final Joiner UNIX_NEW_LINE_JOINER = Joiner.on('\n');

    /**
     * Recursively deletes a path.
     *
     * @param path the path delete, may exist or not
     * @throws IOException failed to delete the file / directory
     */
    public static void deletePath(@NonNull final File path) throws IOException {
        if (!path.exists()) {
            return;
        }

        if (path.isDirectory()) {
            deleteDirectoryContents(path);
        }

        if (!path.delete()) {
            throw new IOException(String.format("Could not delete path '%s'.", path));
        }
    }

    /**
     * Recursively deletes a directory or file.
     *
     * @param directory the directory, that must exist and be a valid directory
     * @throws IOException failed to delete the file / directory
     */
    public static void deleteDirectoryContents(@NonNull final File directory) throws IOException {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory");

        File[] files = directory.listFiles();
        Preconditions.checkNotNull(files);
        for (File file : files) {
            deletePath(file);
        }
    }

    /**
     * Makes sure {@code path} is an empty directory. If {@code path} is a directory, its contents
     * are removed recursively, leaving an empty directory. If {@code path} is not a directory,
     * it is removed and a directory created with the given path. If {@code path} does not
     * exist, a directory is created with the given path.
     *
     * @param path the path, that may exist or not and may be a file or directory
     * @throws IOException failed to delete directory contents, failed to delete {@code path} or
     * failed to create a directory at {@code path}
     */
    public static void cleanOutputDir(@NonNull File path) throws IOException {
        if (!path.isDirectory()) {
            if (path.exists()) {
                deletePath(path);
            }

            if (!path.mkdirs()) {
                throw new IOException(String.format("Could not create empty folder %s", path));
            }

            return;
        }

        deleteDirectoryContents(path);
    }

    /**
     * Copies a regular file from one path to another, preserving file attributes. If the
     * destination file exists, it gets overwritten.
     */
    public static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        java.nio.file.Files.copy(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Copies a directory from one path to another. If the destination directory exists, the file
     * contents are merged and files from the source directory overwrite files in the destination.
     */
    public static void copyDirectory(@NonNull File from, @NonNull File to) throws IOException {
        Preconditions.checkArgument(from.isDirectory(), "Source path is not a directory.");
        Preconditions.checkArgument(
                !to.exists() || to.isDirectory(),
                "Destination path exists and is not a directory.");

        mkdirs(to);
        File[] children = from.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile()) {
                    copyFileToDirectory(child, to);
                } else if (child.isDirectory()) {
                    copyDirectoryToDirectory(child, to);
                } else {
                    throw new IllegalArgumentException(
                            "Don't know how to copy file " + child.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Makes a copy of the given file in the specified directory, preserving the name and file
     * attributes.
     */
    public static void copyFileToDirectory(@NonNull final File from, @NonNull final File to)
            throws IOException {
        copyFile(from, new File(to, from.getName()));
    }

    /**
     * Makes a copy of the given directory in the specified destination directory.
     *
     * @see #copyDirectory(File, File)
     */
    public static void copyDirectoryToDirectory(@NonNull final File from, @NonNull final File to)
            throws IOException {
        copyDirectory(from, new File(to, from.getName()));
    }

    /**
     * Makes a copy of the directory's content, in the specified location, while maintaining the
     * directory structure. So the entire directory tree from the source will be copied.
     *
     * @param from directory from which the content is copied
     * @param to destination directory, will be created if does not exist
     */
    public static void copyDirectoryContentToDirectory(
            @NonNull final File from, @NonNull final File to) throws IOException {
        Preconditions.checkArgument(from.isDirectory(), "Source path is not a directory.");

        File[] children = from.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory()) {
                    File destination = new File(to, relativePath(f, from));
                    Files.createParentDirs(destination);
                    mkdirs(destination);

                    copyDirectoryContentToDirectory(f, destination);
                } else if (f.isFile()) {
                    File destination = new File(to, relativePath(f.getParentFile(), from));
                    Files.createParentDirs(destination);
                    mkdirs(destination);

                    copyFileToDirectory(f, destination);
                }
            }
        }
    }

    /**
     * Creates a directory, if it doesn't exist.
     *
     * @param folder the directory to create, may already exist
     * @return {@code folder}
     */
    @NonNull
    public static File mkdirs(@NonNull File folder) {
        // attempt to create first.
        // if failure only throw if folder does not exist.
        // This makes this method able to create the same folder(s) from different thread
        if (!folder.mkdirs() && !folder.exists()) {
            throw new RuntimeException("Cannot create directory " + folder);
        }

        return folder;
    }

    /**
     * Deletes a file.
     *
     * @param file the file to delete; the file must exist
     * @throws IOException failed to delete the file
     */
    public static void delete(@NonNull File file) throws IOException {
        boolean result = file.delete();
        if (!result) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }

    public static void deleteIfExists(@NonNull File file) throws IOException {
        boolean result = file.delete();
        if (!result && file.exists()) {
            throw new IOException("Failed to delete " + file.getAbsolutePath());
        }
    }

    public static void renameTo(@NonNull File file, @NonNull File to) throws IOException {
        boolean result = file.renameTo(to);
        if (!result) {
            throw new IOException("Failed to rename " + file.getAbsolutePath() + " to " + to);
        }
    }

    /**
     * Joins a list of path segments to a given File object.
     *
     * @param dir the file object.
     * @param paths the segments.
     * @return a new File object.
     */
    @NonNull
    public static File join(@NonNull File dir, @NonNull String... paths) {
        if (paths.length == 0) {
            return dir;
        }

        return new File(dir, PATH_JOINER.join(paths));
    }

    /**
     * Joins a list of path segments to a given File object.
     *
     * @param dir the file object.
     * @param paths the segments.
     * @return a new File object.
     */
    @NonNull
    public static File join(@NonNull File dir, @NonNull Iterable<String> paths) {
        return new File(dir, PATH_JOINER.join(paths));
    }

    /**
     * Joins a set of segment into a string, separating each segments with a host-specific
     * path separator.
     *
     * @param paths the segments.
     * @return a string with the segments.
     */
    @NonNull
    public static String join(@NonNull String... paths) {
        return PATH_JOINER.join(paths);
    }

    /**
     * Joins a set of segment into a string, separating each segments with a host-specific
     * path separator.
     *
     * @param paths the segments.
     * @return a string with the segments.
     */
    @NonNull
    public static String join(@NonNull Iterable<String> paths) {
        return PATH_JOINER.join(paths);
    }

    /**
     * Loads a text file forcing the line separator to be of Unix style '\n' rather than being
     * Windows style '\r\n'.
     */
    @NonNull
    public static String loadFileWithUnixLineSeparators(@NonNull File file) throws IOException {
        return UNIX_NEW_LINE_JOINER.join(Files.readLines(file, Charsets.UTF_8));
    }

    /**
     * Computes the relative of a file or directory with respect to a directory.
     *
     * @param file the file or directory, which must exist in the filesystem
     * @param dir the directory to compute the path relative to
     * @return the relative path from {@code dir} to {@code file}; if {@code file} is a directory
     * the path comes appended with the file separator (see documentation on {@code relativize}
     * on java's {@code URI} class)
     */
    @NonNull
    public static String relativePath(@NonNull File file, @NonNull File dir) {
        checkArgument(file.isFile() || file.isDirectory(), "%s is not a file nor a directory.",
                file.getPath());
        checkArgument(dir.isDirectory(), "%s is not a directory.", dir.getPath());
        return relativePossiblyNonExistingPath(file, dir);
    }

    /**
     * Computes the relative of a file or directory with respect to a directory.
     * For example, if the file's absolute path is {@code /a/b/c} and the directory
     * is {@code /a}, this method returns {@code b/c}.
     *
     * @param file the path that may not correspond to any existing path in the filesystem
     * @param dir the directory to compute the path relative to
     * @return the relative path from {@code dir} to {@code file}; if {@code file} is a directory
     * the path comes appended with the file separator (see documentation on {@code relativize}
     * on java's {@code URI} class)
     */
    @NonNull
    public static String relativePossiblyNonExistingPath(@NonNull File file, @NonNull File dir) {
        String path = dir.toURI().relativize(file.toURI()).getPath();
        return toSystemDependentPath(path);
    }

    /**
     * Converts a /-based path into a path using the system dependent separator.
     * @param path the system independent path to convert
     * @return the system dependent path
     */
    @NonNull
    public static String toSystemDependentPath(@NonNull String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }
        return path;
    }

    /**
     * Converts a system-dependent path into a /-based path.
     * @param path the system dependent path
     * @return the system independent path
     */
    @NonNull
    public static String toSystemIndependentPath(@NonNull String path) {
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        return path;
    }

    @NonNull
    public static String sha1(@NonNull File file) throws IOException {
        return Hashing.sha1().hashBytes(Files.toByteArray(file)).toString();
    }

    @NonNull
    public static FluentIterable<File> getAllFiles(@NonNull File dir) {
        return Files.fileTreeTraverser().preOrderTraversal(dir).filter(Files.isFile());
    }

    @NonNull
    public static String getNamesAsCommaSeparatedList(@NonNull Iterable<File> files) {
        return COMMA_SEPARATED_JOINER.join(Iterables.transform(files, File::getName));
    }

    /**
     * Chooses a directory name, based on a JAR file name, considering exploded-aar and classes.jar.
     */
    @NonNull
    public static String getDirectoryNameForJar(@NonNull File inputFile) {
        // add a hash of the original file path.
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(inputFile.getAbsolutePath(), Charsets.UTF_16LE);

        String name = Files.getNameWithoutExtension(inputFile.getName());
        if (name.equals("classes") && inputFile.getAbsolutePath().contains("exploded-aar")) {
            // This naming scheme is coming from DependencyManager#computeArtifactPath.
            File versionDir = inputFile.getParentFile().getParentFile();
            File artifactDir = versionDir.getParentFile();
            File groupDir = artifactDir.getParentFile();

            name = Joiner.on('-').join(
                    groupDir.getName(), artifactDir.getName(), versionDir.getName());
        }
        name = name + "_" + hashCode.toString();
        return name;
    }

    public static void createFile(@NonNull File file, @NonNull String content) throws IOException {
        checkArgument(!file.exists(), "%s exists already.", file);

        Files.createParentDirs(file);
        Files.write(content, file, Charsets.UTF_8);
    }

    /**
     * Find a list of files in a directory, using a specified path pattern.
     */
    public static List<File> find(@NonNull File base, @NonNull final Pattern pattern) {
        checkArgument(base.isDirectory(), "'%s' must be a directory.", base.getAbsolutePath());
        return Files.fileTreeTraverser()
                .preOrderTraversal(base)
                .filter(Predicates.compose(Predicates.contains(pattern), File::getPath))
                .toList();
    }

    /**
     * Find a file with the specified name in a given directory .
     */
    public static Optional<File> find(@NonNull File base, @NonNull final String name) {
        checkArgument(base.isDirectory(), "'%s' must be a directory.", base.getAbsolutePath());
        return Files.fileTreeTraverser()
                .preOrderTraversal(base)
                .filter(Predicates.compose(Predicates.equalTo(name), File::getName))
                .last();
    }

    /**
     * Reads a portion of a file to memory.
     *
     * @param file the file to read data from
     * @param start the offset in the file to start reading
     * @param length the number of bytes to read
     * @return the bytes read
     * @throws Exception failed to read the file
     */
    @NonNull
    public static byte[] readSegment(@NonNull File file, long start, int length) throws Exception {
        Preconditions.checkArgument(start >= 0, "start < 0");
        Preconditions.checkArgument(length >= 0, "length < 0");

        byte data[];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);

            data = new byte[length];
            int tot = 0;
            while (tot < length) {
                int r = raf.read(data, tot, length - tot);
                if (r < 0) {
                    throw new EOFException();
                }

                tot += r;
            }
        }

        return data;
    }

    /**
     * Join multiple file paths as String.
     */
    @NonNull
    public static String joinFilePaths(@NonNull Iterable<File> files) {
        return Joiner.on(File.pathSeparatorChar)
                .join(Iterables.transform(files, File::getAbsolutePath));
    }

    /**
     * Returns a valid file name modified from the requested file name. This method guarantees that
     * there is a one-to-one mapping between the requested file names and the returned file names
     * (i.e., if the input file names are different, the returned file names will also be
     * different). The file name consists of a base name and an extension (which is an empty string
     * if there is no extension). A directory where the file is located is also provided to check
     * the length of the file path and keep both the file name and file path's lengths within limit.
     *
     * @param baseName the base name of the requested file name
     * @param extension the extension of the requested file name (empty string if not available)
     * @param directory the directory where the file will be located
     * @throws IOException if the requested file name or file path is too long
     */
    @NonNull
    public static String getValidFileName(
            @NonNull String baseName, @NonNull String extension, @NonNull File directory)
            throws IOException {
        String fileName = (extension.isEmpty() ? baseName : (baseName + "." + extension));

        String validBaseName = baseName.replaceAll("[^a-zA-Z0-9]", "_");
        String validExtension = extension.replaceAll("[^a-zA-Z0-9]", "_");
        String validExtensionWithDot = (validExtension.isEmpty() ? "" : ("." + validExtension));
        String validFileName = validBaseName + validExtensionWithDot;

        // Add a hash code to the returned file name to avoid accidental collision (when two
        // different requested file names produce the same returned file name)
        String fileHash = Hashing.sha1().hashString(fileName, StandardCharsets.UTF_8).toString();
        if (!validFileName.equals(fileName)) {
            validFileName = validBaseName + "_" + fileHash + validExtensionWithDot;
        }

        // If the file name/file path is too long, retain the hash code only and also keep the
        // extension
        if (isFilePathTooLong(validFileName, directory)) {
            validFileName = fileHash + validExtensionWithDot;

            // If the file name/file path is still too long, throw a RuntimeException
            if (isFilePathTooLong(validFileName, directory)) {
                throw new IOException("File name or file path is too long: "
                        + new File(directory, validFileName).getAbsolutePath());
            }
        }

        return validFileName;
    }

    /**
     * Returns <code>true</code> if the file name is too long.
     *
     * @param fileName the file name
     */
    public static boolean isFileNameTooLong(@NonNull String fileName) {
        return fileName.length() > 255;
    }

    /**
     * Returns <code>true</code> if the file name or file path is too long.
     *
     * @param fileName the file name
     * @param directory the directory where the file will be located
     */
    public static boolean isFilePathTooLong(@NonNull String fileName, @NonNull File directory) {
        if (isFileNameTooLong(fileName)) {
            return true;
        }

        int filePathLength = new File(directory, fileName).getAbsolutePath().length();
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            return filePathLength > 260;
        } else {
            return filePathLength > 4096;
        }
    }
}
