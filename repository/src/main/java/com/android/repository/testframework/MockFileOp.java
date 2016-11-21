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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileOpImpl;
import com.android.repository.io.impl.FileSystemFileOp;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


/**
 * Mock version of {@link FileOpImpl} that wraps some common {@link File}
 * operations on files and folders.
 * <p>
 * This version does not perform any file operation. Instead it records a textual
 * representation of all the file operations performed.
 * <p>
 * To avoid cross-platform path issues (e.g. Windows path), the methods here should
 * always use rooted (aka absolute) unix-looking paths, e.g. "/dir1/dir2/file3".
 * When processing {@link File}, you can convert them using {@link #getAgnosticAbsPath(File)}.
 */
public class MockFileOp extends FileSystemFileOp {
    private FileSystem mFileSystem = createFileSystem();

    public MockFileOp() {
        mIsWindows = FileOpUtils.create().isWindows();
    }

    private static FileSystem createFileSystem() {
        // TODO: use the current platform configuration and get rid of all the agnostic path stuff.
        Configuration config = Configuration.unix();
        config = config.toBuilder()
                .setWorkingDirectory("/")
                .setAttributeViews("posix")
                .build();
        return Jimfs.newFileSystem(config);
    }

    @Override
    public FileSystem getFileSystem() {
        return mFileSystem;
    }

    /** Resets the internal state, as if the object had been newly created. */
    public void reset() {
        mFileSystem = createFileSystem();
    }

    @Override
    public void deleteOnExit(File file) {
        // nothing
    }

    public void setIsWindows(boolean isWindows) {
        mIsWindows = isWindows;
    }

    @Override
    public boolean canWrite(@NonNull File file) {
        try {
            return !Sets.intersection(
                    Files.getPosixFilePermissions(toPath(new File(getAgnosticAbsPath(file)))),
                    ImmutableSet.of(
                            PosixFilePermission.OTHERS_WRITE,
                            PosixFilePermission.GROUP_WRITE,
                            PosixFilePermission.OWNER_WRITE))
                    .isEmpty();
        }
        catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public String getAgnosticAbsPath(@NonNull File file) {
        return getAgnosticAbsPath(file.getAbsolutePath());
    }

    @NonNull
    public String getAgnosticAbsPath(@NonNull String path) {
        if (isWindows()) {
            // Try to convert the windows-looking path to a unix-looking one
            path = path.replace('\\', '/');
            path = path.replaceAll("^[A-Z]:", "");
        }
        return path;
    }

    /**
     * Records a new absolute file path.
     * Parent folders are automatically created.
     */
    public void recordExistingFile(@NonNull File file) {
        recordExistingFile(getAgnosticAbsPath(file), 0, (byte[])null);
    }

    /**
     * Records a new absolute file path.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     */
    public void recordExistingFile(@NonNull String absFilePath) {
        recordExistingFile(absFilePath, 0, (byte[])null);
    }

    /**
     * Records a new absolute file path and its input stream content.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     * @param inputStream A non-null byte array of content to return
     *                    via {@link #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath, @Nullable byte[] inputStream) {
        recordExistingFile(absFilePath, 0, inputStream);
    }

    /**
     * Records a new absolute file path and its input stream content.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     * @param content A non-null UTF-8 content string to return
     *                    via {@link #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath, @NonNull String content) {
        recordExistingFile(absFilePath, 0, content.getBytes(Charsets.UTF_8));
    }

    /**
     * Records a new absolute file path and its input stream content.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     * @param inputStream A non-null byte array of content to return
     *                    via {@link #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath,
      long lastModified,
      @Nullable byte[] inputStream) {
        try {
            Path path = mFileSystem.getPath(getAgnosticAbsPath(absFilePath));
            Files.createDirectories(path.getParent());
            Files.write(path,
              inputStream == null ? new byte[0] : inputStream);
            Files.setLastModifiedTime(path, FileTime.fromMillis(lastModified));
        } catch (IOException e) {
            assert false : e.getMessage();
        }
    }

    /**
     * Records a new absolute file path and its input stream content.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     * @param content A non-null UTF-8 content string to return
     *                    via {@link #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath,
      long lastModified,
      @NonNull String content) {
        recordExistingFile(absFilePath, lastModified, content.getBytes(Charsets.UTF_8));
    }

    /**
     * Records a new absolute folder path.
     * Parent folders are automatically created.
     */
    public void recordExistingFolder(File folder) {
        recordExistingFolder(getAgnosticAbsPath(folder));
    }

    /**
     * Records a new absolute folder path.
     * Parent folders are automatically created.
     * <p>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFolderPath A unix-like folder path, e.g. "/dir/file"
     */
    public void recordExistingFolder(String absFolderPath) {
        try {
            Files.createDirectories(mFileSystem.getPath(getAgnosticAbsPath(absFolderPath)));
        } catch (IOException e) {
            assert false : e.getMessage();
        }
    }

    /**
     * Returns true if a folder with the given path has been recorded.
     */
    public boolean hasRecordedExistingFolder(File folder) {
        return exists(folder) && isDirectory(folder);
    }

    /**
     * Returns the list of paths added using {@link #recordExistingFile(String)}
     * and eventually updated by {@link #delete(File)} operations.
     * <p>
     * The returned list is sorted by alphabetic absolute path string.
     */
    @NonNull
    public String[] getExistingFiles() {
        List<String> result = new ArrayList<>();
        mFileSystem.getRootDirectories().forEach(path -> {
            try (Stream<Path> stream = Files.find(path, 100, (p, a) -> true)) {
                stream.filter(p -> Files.isRegularFile(p))
                  .forEach(p -> result.add(p.toString()));
            } catch (IOException e) {
                assert false : e.getMessage();
            }
        });
        return result.toArray(new String[result.size()]);
    }

    /**
     * Returns the list of folder paths added using {@link #recordExistingFolder(String)}
     * and eventually updated {@link #delete(File)} or {@link #mkdirs(File)} operations.
     * <p>
     * The returned list is sorted by alphabetic absolute path string.
     */
    @NonNull
    public String[] getExistingFolders() {
        List<String> result = new ArrayList<>();
        mFileSystem.getRootDirectories().forEach(path -> {
            try (Stream<Path> stream = Files.find(path, 100, (p, a) -> true)) {
                stream.filter(p -> Files.isDirectory(p))
                  .forEach(p -> result.add(p.toString()));
            } catch (IOException e) {
                assert false : e.getMessage();
            }
        });
        return result.toArray(new String[result.size()]);
    }

    @Override
    public File ensureRealFile(@NonNull File in) throws IOException {
        if (!exists(in)) {
            return in;
        }
        File result = File.createTempFile("MockFileOp", null);
        result.deleteOnExit();
        OutputStream os = new FileOutputStream(result);
        try {
            ByteStreams.copy(newFileInputStream(in), os);
        }
        finally {
            os.close();
        }
        return result;
    }

    public byte[] getContent(File file) {
        try {
            return Files.readAllBytes(toPath(file));
        }
        catch (IOException e) {
            return new byte[0];
        }
    }

    @NonNull
    @Override
    public Path toPath(@NonNull File file) {
        return getFileSystem().getPath(getAgnosticAbsPath(file.getPath()));
    }
}
