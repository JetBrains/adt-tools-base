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

package com.android.repository.io.impl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Properties;

/**
 * Wraps some common {@link File} operations on files and folders.
 * <p/>
 * This makes it possible to override/mock/stub some file operations in unit tests.
 * <p/>
 * Instances should be obtained through {@link FileOpUtils#create()}
 */
public class FileOpImpl implements FileOp {

    /**
     * Reflection method for File.setExecutable(boolean, boolean). Only present in Java 6.
     */
    private static Method sFileSetExecutable = null;

    /**
     * Parameters to call File.setExecutable through reflection.
     */
    private static final Object[] sFileSetExecutableParams = new Object[] {
        Boolean.TRUE, Boolean.FALSE };

    // static initialization of sFileSetExecutable.
    static {
        try {
            sFileSetExecutable = File.class.getMethod("setExecutable", //$NON-NLS-1$
                    boolean.class, boolean.class);

        } catch (SecurityException e) {
            // do nothing we'll use chmod instead
        } catch (NoSuchMethodException e) {
            // do nothing we'll use chmod instead
        }
    }

    @Override
    public void deleteFileOrFolder(@NonNull File fileOrFolder) {
        if (isDirectory(fileOrFolder)) {
            // Must delete content recursively first
            File[] files = fileOrFolder.listFiles();
            if (files != null) {
                for (File item : files) {
                    deleteFileOrFolder(item);
                }
            }
        }

        // Don't try to delete it if it doesn't exist.
        if (!exists(fileOrFolder)) {
            return;
        }

        if (isWindows()) {
            // Trying to delete a resource on windows might fail if there's a file
            // indexer locking the resource. Generally retrying will be enough to
            // make it work.
            //
            // Try for half a second before giving up.

            for (int i = 0; i < 5; i++) {
                if (fileOrFolder.delete()) {
                    return;
                }

                try {
                    Thread.sleep(100 /*ms*/);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }

            fileOrFolder.deleteOnExit();

        } else {
            // On Linux or Mac, just straight deleting it should just work.

            if (!fileOrFolder.delete()) {
                fileOrFolder.deleteOnExit();
            }
        }
    }

    @Override
    public void setExecutablePermission(@NonNull File file) throws IOException {
        if (isWindows()) {
            throw new IllegalStateException("Can't setExecutablePermission on windows!");
        }
        if (sFileSetExecutable != null) {
            try {
                sFileSetExecutable.invoke(file, sFileSetExecutableParams);
                return;
            } catch (IllegalArgumentException e) {
                // we'll run chmod below
            } catch (IllegalAccessException e) {
                // we'll run chmod below
            } catch (InvocationTargetException e) {
                // we'll run chmod below
            }
        }

        Runtime.getRuntime().exec(new String[] {
               "chmod", "+x", file.getAbsolutePath()  //$NON-NLS-1$ //$NON-NLS-2$
            });
    }

    @Override
    public void setReadOnly(@NonNull File file) {
        file.setReadOnly();
    }

    @Override
    public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
        byte[] buffer = new byte[8192];

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(dest);

            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }
    }

    @Override
    public boolean isSameFile(@NonNull File file1, @NonNull File file2) throws IOException {

        if (file1.length() != file2.length()) {
            return false;
        }

        FileInputStream fis1 = null;
        FileInputStream fis2 = null;

        try {
            fis1 = new FileInputStream(file1);
            fis2 = new FileInputStream(file2);

            byte[] buffer1 = new byte[8192];
            byte[] buffer2 = new byte[8192];

            int read1;
            while ((read1 = fis1.read(buffer1)) != -1) {
                int read2 = 0;
                while (read2 < read1) {
                    int n = fis2.read(buffer2, read2, read1 - read2);
                    if (n == -1) {
                        break;
                    }
                }

                if (read2 != read1) {
                    return false;
                }

                if (!Arrays.equals(buffer1, buffer2)) {
                    return false;
                }
            }
        } finally {
            if (fis2 != null) {
                try {
                    fis2.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (fis1 != null) {
                try {
                    fis1.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return true;
    }

    @Override
    public boolean isFile(@NonNull File file) {
        return file.isFile();
    }

    @Override
    public boolean isDirectory(@NonNull File file) {
        return file.isDirectory();
    }

    @Override
    public boolean exists(@NonNull File file) {
        return file.exists();
    }

    @Override
    public boolean canWrite(@NonNull File file) {
        return file.canWrite();
    }

    @Override
    public long length(@NonNull File file) {
        return file.length();
    }

    @Override
    public boolean delete(@NonNull File file) {
        return file.delete();
    }

    @Override
    public boolean mkdirs(@NonNull File file) {
        return file.mkdirs();
    }

    @Override
    @NonNull
    public File[] listFiles(@NonNull File file) {
        File[] r = file.listFiles();
        if (r == null) {
            return EMPTY_FILE_ARRAY;
        } else {
            return r;
        }
    }

    @Override
    public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
        return oldFile.renameTo(newFile);
    }

    @Override
    @NonNull
    public OutputStream newFileOutputStream(@NonNull File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    @Override
    @NonNull
    public InputStream newFileInputStream(@NonNull File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    @NonNull
    public Properties loadProperties(@NonNull File file) {
        Properties props = new Properties();
        Closer closer = Closer.create();
        try {
            FileInputStream fis = closer.register(new FileInputStream(file));
            props.load(fis);
        } catch (IOException ignore) {
        } finally {
            try {
                closer.close();
            } catch (IOException e) {
            }
        }
        return props;
    }

    @Override
    public void saveProperties(
            @NonNull File file,
            @NonNull Properties props,
            @NonNull String comments) throws IOException {
        Closer closer = Closer.create();
        try {
            OutputStream fos = closer.register(newFileOutputStream(file));
            props.store(fos, comments);
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }

    @Override
    public long lastModified(@NonNull File file) {
        return file.lastModified();
    }

    @Override
    public boolean createNewFile(@NonNull File file) throws IOException {
        return file.createNewFile();
    }

    @Override
    public boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    @Override
    public boolean canExecute(@NonNull File file) {
        return file.canExecute();
    }

    @Override
    public File ensureRealFile(@NonNull File in) {
        return in;
    }

    @NonNull
    @Override
    public String toString(@NonNull File f, @NonNull Charset c) throws IOException {
        return Files.toString(f, c);
    }

    @Override
    @Nullable
    public String[] list(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        return folder.list(filenameFilter);
    }

    @Override
    @Nullable
    public File[] listFiles(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        return folder.listFiles(filenameFilter);
    }

    @Override
    public void deleteOnExit(File file) {
        file.deleteOnExit();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileOpImpl;
    }

    @Override
    public boolean setLastModified(@NonNull File file, long time) throws IOException {
        try {
            return file.setLastModified(time);
        }
        catch (SecurityException e) {
            throw new IOException(e);
        }
    }
}
