/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdklib.io;

import com.android.SdkConstants;
import com.android.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Mock version of {@link FileOp} that wraps some common {@link File}
 * operations on files and folders.
 * <p/>
 * This version does not perform any file operation. Instead it records a textual
 * representation of all the file operations performed.
 * <p/>
 * To avoid cross-platform path issues (e.g. Windows path), the methods here should
 * always use rooted (aka absolute) unix-looking paths, e.g. "/dir1/dir2/file3".
 * When processing {@link File}, you can convert them using {@link #getAgnosticAbsPath(File)}.
 */
public class MockFileOp implements IFileOp {

    private final Set<String> mExistinfFiles = new TreeSet<String>();
    private final Set<String> mExistinfFolders = new TreeSet<String>();
    private final List<StringOutputStream> mOutputStreams = new ArrayList<StringOutputStream>();

    public MockFileOp() {
    }

    /** Resets the internal state, as if the object had been newly created. */
    public void reset() {
        mExistinfFiles.clear();
        mExistinfFolders.clear();
    }

    public String getAgnosticAbsPath(File file) {
        return getAgnosticAbsPath(file.getAbsolutePath());
    }

    public String getAgnosticAbsPath(String path) {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            // Try to convert the windows-looking path to a unix-looking one
            path = path.replace('\\', '/');
            path = path.replace("C:", "");      //$NON-NLS-1$ //$NON-NLS-2$
        }
        return path;
    }

    /**
     * Records a new absolute file path.
     * Parent folders are not automatically created.
     */
    public void recordExistingFile(File file) {
        mExistinfFiles.add(getAgnosticAbsPath(file));
    }

    /**
     * Records a new absolute file path.
     * Parent folders are not automatically created.
     * <p/>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFilePath A unix-like file path, e.g. "/dir/file"
     */
    public void recordExistingFile(String absFilePath) {
        mExistinfFiles.add(absFilePath);
    }

    /**
     * Records a new absolute folder path.
     * Parent folders are not automatically created.
     */
    public void recordExistingFolder(File folder) {
        mExistinfFolders.add(getAgnosticAbsPath(folder));
    }

    /**
     * Records a new absolute folder path.
     * Parent folders are not automatically created.
     * <p/>
     * The syntax should always look "unix-like", e.g. "/dir/file".
     * On Windows that means you'll want to use {@link #getAgnosticAbsPath(File)}.
     * @param absFolderPath A unix-like folder path, e.g. "/dir/file"
     */
    public void recordExistingFolder(String absFolderPath) {
        mExistinfFolders.add(absFolderPath);
    }

    /**
     * Returns the list of paths added using {@link #recordExistingFile(String)}
     * and eventually updated by {@link #delete(File)} operations.
     * <p/>
     * The returned list is sorted by alphabetic absolute path string.
     */
    public String[] getExistingFiles() {
        return mExistinfFiles.toArray(new String[mExistinfFiles.size()]);
    }

    /**
     * Returns the list of folder paths added using {@link #recordExistingFolder(String)}
     * and eventually updated {@link #delete(File)} or {@link #mkdirs(File)} operations.
     * <p/>
     * The returned list is sorted by alphabetic absolute path string.
     */
    public String[] getExistingFolders() {
        return mExistinfFolders.toArray(new String[mExistinfFolders.size()]);
    }

    /**
     * Returns the {@link StringOutputStream#toString()} as an array, in creation order.
     * Array can be empty but not null.
     */
    public String[] getOutputStreams() {
        int n = mOutputStreams.size();
        String[] result = new String[n];
        for (int i = 0; i < n; i++) {
            result[i] = mOutputStreams.get(i).toString();
        }
        return result;
    }

    /**
     * Helper to delete a file or a directory.
     * For a directory, recursively deletes all of its content.
     * Files that cannot be deleted right away are marked for deletion on exit.
     * The argument can be null.
     */
    @Override
    public void deleteFileOrFolder(File fileOrFolder) {
        if (fileOrFolder != null) {
            if (isDirectory(fileOrFolder)) {
                // Must delete content recursively first
                for (File item : listFiles(fileOrFolder)) {
                    deleteFileOrFolder(item);
                }
            }
            delete(fileOrFolder);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <em>Note: this mock version does nothing.</em>
     */
    @Override
    public void setExecutablePermission(File file) throws IOException {
        // pass
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <em>Note: this mock version does nothing.</em>
     */
    @Override
    public void setReadOnly(File file) {
        // pass
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <em>Note: this mock version does nothing.</em>
     */
    @Override
    public void copyFile(File source, File dest) throws IOException {
        // pass
    }

    /**
     * Checks whether 2 binary files are the same.
     *
     * @param source the source file to copy
     * @param destination the destination file to write
     * @throws FileNotFoundException if the source files don't exist.
     * @throws IOException if there's a problem reading the files.
     */
    @Override
    public boolean isSameFile(File source, File destination) throws IOException {
        throw new UnsupportedOperationException("MockFileUtils.isSameFile is not supported."); //$NON-NLS-1$
    }

    /** Invokes {@link File#isFile()} on the given {@code file}. */
    @Override
    public boolean isFile(File file) {
        String path = getAgnosticAbsPath(file);
        return mExistinfFiles.contains(path);
    }

    /** Invokes {@link File#isDirectory()} on the given {@code file}. */
    @Override
    public boolean isDirectory(File file) {
        String path = getAgnosticAbsPath(file);
        if (mExistinfFolders.contains(path)) {
            return true;
        }

        // If we defined a file or folder as a child of the requested file path,
        // then the directory exists implicitely.
        Pattern pathRE = Pattern.compile(
                Pattern.quote(path + (path.endsWith("/") ? "" : '/')) +  //$NON-NLS-1$ //$NON-NLS-2$
                ".*");                                                   //$NON-NLS-1$

        for (String folder : mExistinfFolders) {
            if (pathRE.matcher(folder).matches()) {
                return true;
            }
        }
        for (String filePath : mExistinfFiles) {
            if (pathRE.matcher(filePath).matches()) {
                return true;
            }
        }

        return false;
    }

    /** Invokes {@link File#exists()} on the given {@code file}. */
    @Override
    public boolean exists(File file) {
        return isFile(file) || isDirectory(file);
    }

    /** Invokes {@link File#length()} on the given {@code file}. */
    @Override
    public long length(File file) {
        throw new UnsupportedOperationException("MockFileUtils.length is not supported."); //$NON-NLS-1$
    }

    @Override
    public boolean delete(File file) {
        String path = getAgnosticAbsPath(file);

        if (mExistinfFiles.remove(path)) {
            return true;
        }

        boolean hasSubfiles = false;
        for (String folder : mExistinfFolders) {
            if (folder.startsWith(path) && !folder.equals(path)) {
                // the File.delete operation is not recursive and would fail to remove
                // a root dir that is not empty.
                return false;
            }
        }
        if (!hasSubfiles) {
            for (String filePath : mExistinfFiles) {
                if (filePath.startsWith(path) && !filePath.equals(path)) {
                    // the File.delete operation is not recursive and would fail to remove
                    // a root dir that is not empty.
                    return false;
                }
            }
        }

        return mExistinfFolders.remove(path);
    }

    /** Invokes {@link File#mkdirs()} on the given {@code file}. */
    @Override
    public boolean mkdirs(File file) {
        for (; file != null; file = file.getParentFile()) {
            String path = getAgnosticAbsPath(file);
            mExistinfFolders.add(path);
        }
        return true;
    }

    /**
     * Invokes {@link File#listFiles()} on the given {@code file}.
     * The returned list is sorted by alphabetic absolute path string.
     */
    @Override
    public File[] listFiles(File file) {
        TreeSet<File> files = new TreeSet<File>();

        String path = getAgnosticAbsPath(file);
        Pattern pathRE = Pattern.compile(
                Pattern.quote(path + (path.endsWith("/") ? "" : '/')) +  //$NON-NLS-1$ //$NON-NLS-2$
                ".*");                                                   //$NON-NLS-1$

        for (String folder : mExistinfFolders) {
            if (pathRE.matcher(folder).matches()) {
                files.add(new File(folder));
            }
        }
        for (String filePath : mExistinfFiles) {
            if (pathRE.matcher(filePath).matches()) {
                files.add(new File(filePath));
            }
        }
        return files.toArray(new File[files.size()]);
    }

    /** Invokes {@link File#renameTo(File)} on the given files. */
    @Override
    public boolean renameTo(File oldFile, File newFile) {
        boolean renamed = false;

        String oldPath = getAgnosticAbsPath(oldFile);
        String newPath = getAgnosticAbsPath(newFile);
        Pattern pathRE = Pattern.compile(
                "^(" + Pattern.quote(oldPath) + //$NON-NLS-1$
                ")($|/.*)");                    //$NON-NLS-1$

        Set<String> newStrings = new HashSet<String>();
        for (Iterator<String> it = mExistinfFolders.iterator(); it.hasNext(); ) {
            String folder = it.next();
            Matcher m = pathRE.matcher(folder);
            if (m.matches()) {
                it.remove();
                String newFolder = newPath + m.group(2);
                newStrings.add(newFolder);
                renamed = true;
            }
        }
        mExistinfFolders.addAll(newStrings);
        newStrings.clear();

        for (Iterator<String> it = mExistinfFiles.iterator(); it.hasNext(); ) {
            String filePath = it.next();
            Matcher m = pathRE.matcher(filePath);
            if (m.matches()) {
                it.remove();
                String newFilePath = newPath + m.group(2);
                newStrings.add(newFilePath);
                renamed = true;
            }
        }
        mExistinfFiles.addAll(newStrings);

        return renamed;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <em>TODO: we might want to overload this to read mock properties instead of a real file.</em>
     */
    @Override
    public @NonNull Properties loadProperties(@NonNull File file) {
        Properties props = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            props.load(fis);
        } catch (IOException ignore) {
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignore) {}
            }
        }
        return props;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <em>Note that this uses the mock version of {@link #newFileOutputStream(File)} and thus
     * records the write rather than actually performing it.</em>
     */
    @Override
    public boolean saveProperties(@NonNull File file, @NonNull Properties props,
            @NonNull String comments) {
        OutputStream fos = null;
        try {
            fos = newFileOutputStream(file);

            props.store(fos, comments);
            return true;
        } catch (IOException ignore) {
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }

        return false;
    }

    /**
     * Returns an OutputStream that will capture the bytes written and associate
     * them with the given file.
     */
    @Override
    public OutputStream newFileOutputStream(File file) throws FileNotFoundException {
        StringOutputStream os = new StringOutputStream(file);
        mOutputStreams.add(os);
        return os;
    }

    /**
     * An {@link OutputStream} that will capture the stream as an UTF-8 string once properly closed
     * and associate it to the given {@link File}.
     */
    public class StringOutputStream extends ByteArrayOutputStream {
        private String mData;
        private final File mFile;

        public StringOutputStream(File file) {
            mFile = file;
            recordExistingFile(file);
        }

        public File getFile() {
            return mFile;
        }

        /** Can be null if the stream has never been properly closed. */
        public String getData() {
            return mData;
        }

        /** Once the stream is properly closed, convert the byte array to an UTF-8 string */
        @Override
        public void close() throws IOException {
            super.close();
            mData = new String(toByteArray(), "UTF-8");                         //$NON-NLS-1$
        }

        /** Returns a string representation suitable for unit tests validation. */
        @Override
        public synchronized String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('<').append(getAgnosticAbsPath(mFile)).append(": ");      //$NON-NLS-1$
            if (mData == null) {
                sb.append("(stream not closed properly)>");                     //$NON-NLS-1$
            } else {
                sb.append('\'').append(mData).append("'>");                     //$NON-NLS-1$
            }
            return sb.toString();
        }
    }
}
