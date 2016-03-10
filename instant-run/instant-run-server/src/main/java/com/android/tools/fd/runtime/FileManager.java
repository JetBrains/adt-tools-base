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

package com.android.tools.fd.runtime;

import static com.android.tools.fd.runtime.AppInfo.applicationId;
import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexFile;

/**
 * Class which handles locating existing code and resource files on the device,
 * as well as writing new versions of these.
 */
public class FileManager {
    /**
     * According to Dianne, using an extracted directory tree of resources rather than
     * in an archive was implemented before 1.0 and never used or tested... so we should
     * tread carefully here.
     */
    private static final boolean USE_EXTRACTED_RESOURCES = false;

    /** Name of file to write resource data into, if not extracting resources */
    private static final String RESOURCE_FILE_NAME = Paths.RESOURCE_FILE_NAME;

    /** Name of folder to write extracted resource data into, if extracting resources */
    private static final String RESOURCE_FOLDER_NAME = "resources";

    /** Name of the file which points to either the left or the right data directory */
    private static final String FILE_NAME_ACTIVE = "active";

    /** Name of the left directory */
    private static final String FOLDER_NAME_LEFT = "left";

    /** Name of the right directory */
    private static final String FOLDER_NAME_RIGHT = "right";

    /** Prefix for classes.dex files */
    private static final String CLASSES_DEX_PREFIX = "classes";

    /** Prefix for reload.dex files */
    private static final String RELOAD_DEX_PREFIX = "reload";

    /** Suffix for classes.dex files */
    public static final String CLASSES_DEX_SUFFIX = ".dex";

    /** Filename suffix for dex index files */
    private static final String EXT_INDEX_FILE = ".index";

    /**
     * The folder where resources and code are located. Within this folder we have two
     * alternatives: "left" and "right". One is in the foreground (in use), one is in the
     * background (to write to). These are named {@link #FOLDER_NAME_LEFT} and
     * {@link #FOLDER_NAME_RIGHT} and the current one is pointed to by
     * {@link #FILE_NAME_ACTIVE}. */
    private static File getDataFolder() {
        // TODO: Call Context#getFilesDir(), but since we don't have a context yet figure
        // out what to do
        // Keep in sync with ResourceDeltaManager in the IDE (which needs this path
        // in order to run an adb wipe command when reinstalling a freshly built app
        // to avoid using stale data)
        return new File(Paths.getDataDirectory(applicationId));
    }

    @NonNull
    private static File getResourceFile(File base) {
        //noinspection ConstantConditions
        return new File(base, USE_EXTRACTED_RESOURCES ? RESOURCE_FOLDER_NAME : RESOURCE_FILE_NAME);
    }

    /**
     * Returns the folder used for .dex files used during the next app start
     */
    @Nullable
    private static File getDexFileFolder(File base, boolean createIfNecessary) {
        File file = new File(base, Paths.DEX_DIRECTORY_NAME);
        if (createIfNecessary) {
            if (!file.isDirectory()) {
                boolean created = file.mkdirs();
                if (!created) {
                    Log.e(LOG_TAG, "Failed to create directory " + file);
                    return null;
                }
            }
        }

        return file;
    }

    /**
     * Returns the folder used for temporary .dex files (e.g. classes loaded on the fly
     * and only needing to exist during the current app process
     */
    @NonNull
    private static File getTempDexFileFolder(File base) {
        return new File(base, "dex-temp");
    }

    public static File getNativeLibraryFolder() {
        return new File(Paths.getMainApkDataDirectory(applicationId), "lib");
    }

    /**
     * Returns the "foreground" folder: the location to read code and resources from.
     */
    @NonNull
    public static File getReadFolder() {
        String name = leftIsActive() ? FOLDER_NAME_LEFT : FOLDER_NAME_RIGHT;
        return new File(getDataFolder(), name);
    }

    /**
     * Swaps the read/write folders such that the next time somebody asks for the
     * read or write folders, they'll get the opposite.
     */
    public static void swapFolders() {
        setLeftActive(!leftIsActive());
    }

    /**
     * Returns the "background" folder: the location to write code and resources to.
     */
    @NonNull
    public static File getWriteFolder(boolean wipe) {
        String name = leftIsActive() ? FOLDER_NAME_RIGHT : FOLDER_NAME_LEFT;
        File folder = new File(getDataFolder(), name);
        if (wipe && folder.exists()) {
            delete(folder);
            boolean mkdirs = folder.mkdirs();
            if (!mkdirs) {
                Log.e(LOG_TAG, "Failed to create folder " + folder);
            }
        }
        return folder;
    }

    private static void delete(@NonNull File file) {
        if (file.isDirectory()) {
            // Delete the contents
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        boolean deleted = file.delete();
        if (!deleted) {
            Log.e(LOG_TAG, "Failed to delete file " + file);
        }
    }

    private static boolean leftIsActive() {
        File folder = getDataFolder();
        File pointer = new File(folder, FILE_NAME_ACTIVE);
        if (!pointer.exists()) {
            return true;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(pointer));
            try {
                String line = reader.readLine();
                return FOLDER_NAME_LEFT.equals(line);
            } finally {
                reader.close();
            }
        } catch (IOException ignore) {
            return true;
        }
    }

    private static void setLeftActive(boolean active) {
        File folder = getDataFolder();
        File pointer = new File(folder, FILE_NAME_ACTIVE);
        if (pointer.exists()) {
            boolean deleted = pointer.delete();
            if (!deleted) {
                Log.e(LOG_TAG, "Failed to delete file " + pointer);
            }
        } else if (!folder.exists()) {
            boolean create = folder.mkdirs();
            if (!create) {
                Log.e(LOG_TAG, "Failed to create directory " + folder);
            }
            return;
        }

        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pointer),
                    "UTF-8"));
            try {
                writer.write(active ? FOLDER_NAME_LEFT : FOLDER_NAME_RIGHT);
            } finally {
                writer.close();
            }
        } catch (IOException ignore) {
        }
    }

    /** Looks in the inbox for new changes sent while the app wasn't running and apply them */
    public static void checkInbox() {
        File inbox = new File(Paths.getInboxDirectory(applicationId));
        if (inbox.isDirectory()) {
            File resources = new File(inbox, RESOURCE_FILE_NAME);
            if (resources.isFile()) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Processing resource file from inbox (" + resources + ")");
                }
                byte[] bytes = readRawBytes(resources);
                if (bytes != null) {
                    FileManager.startUpdate();
                    FileManager.writeAaptResources(RESOURCE_FILE_NAME, bytes);
                    FileManager.finishUpdate(true);
                    boolean deleted = resources.delete();
                    if (!deleted) {
                        if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                            Log.e(LOG_TAG, "Couldn't remove inbox resource file: " + resources);
                        }
                    }
                }
            }
        }
    }

    /** Returns the current/active resource file, if it exists */
    @Nullable
    public static File getExternalResourceFile() {
        File file = getResourceFile(getReadFolder());
        if (!file.exists()) {
            Log.v(LOG_TAG, "Cannot find external resources, not patching them in");
            return null;
        }

        return file;
    }

    /** Returns the list of available .dex files to be loaded, possibly empty
     * @param apkModified main apk installation time to purge old dex files from previous
     *                    installation.
     */
    @NonNull
    public static List<String> getDexList(long apkModified) {
        File dataFolder = getDataFolder();

        // Get rid of reload dex files from previous runs, if any
        FileManager.purgeTempDexFiles(dataFolder);

        // Get rid of patches no longer applicable
        if (Build.VERSION.SDK_INT < 21) {
            FileManager.purgeMaskedDexFiles(dataFolder, apkModified);
        }

        List<String> list = new ArrayList<String>();

        // We don't need "double buffering" for dex files - we never rewrite files, so we
        // can accumulate in the same dir
        File dexFolder = getDexFileFolder(dataFolder, false);

        // Extract slices.
        //
        // Imagine this scenario -- you run your app (so the device dex folder is filled).
        // Then you do a clean build etc -- so Gradle doesn't know there is existing state
        // on the device. If we *only* extract slices when there are no slices there already,
        // then we'd end up here just running the old slices already on the device.
        // On the other hand, we can't just always extract slices, since then each time
        // you run we'll overwrite coldswap and freezeswap slices.
        //
        // So what this code does is pass the APK timestamp to the extractor, and in the
        // extractor, if the timestamp is positive, we check before writing each slice that
        // it doesn't already exist and is newer than the APK.
        boolean extractedSlices = false;
        if (dexFolder == null || !dexFolder.isDirectory()) {
            // It's the first run of a freshly installed app, and we need to extract the
            // slices from within the APK into the dex folder
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "No local dex slice folder: First run since installation.");
            }
            dexFolder = getDexFileFolder(dataFolder, true);
            if (dexFolder == null) {
                // Failed to create dex folder.
                Log.wtf(LOG_TAG, "Couldn't create dex code folder");
                return list; // unreachable
            }
            extractedSlices = extractSlices(dexFolder, -1); // -1: unconditionally extract all
        }

        File[] dexFiles = dexFolder.listFiles();
        if (dexFiles == null) {
            Log.v(LOG_TAG, "Cannot find dex classes, not patching them in");
            return list;
        }

        // See if any of the slices are older than the APK. This will only be the case
        // if it's not the first run, and the APK has been reinstalled while there are some
        // potentially stale dex files.
        if (!extractedSlices && dexFiles.length > 0) {
            long oldest = apkModified;
            for (File dex : dexFiles) {
                oldest = Math.min(dex.lastModified(), oldest);
            }
            if (oldest < apkModified) {
                // At least one slice is older than the APK: re-extract those that
                // need it
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "One or more slices were older than APK: extracting newer slices");
                }
                extractSlices(dexFolder, apkModified);
            }
        }

        for (File dex : dexFiles) {
            if (dex.getName().endsWith(CLASSES_DEX_SUFFIX)) {
                list.add(dex.getPath());
            }
        }

        // Dex files should be sorted in reverse order such that the class loader finds
        // most recent updates first
        Collections.sort(list, Collections.reverseOrder());

        return list;
    }

    /**
     * Extracts the slices found in the APK root directory (instant-run.zip) into the dex folder,
     * and skipping any files that already exist and are newer than apkModified (unless apkModified
     * <= 0)
     */
    private static boolean extractSlices(@NonNull File dexFolder, long apkModified) {
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "Extracting slices into " + dexFolder);
        }
        InputStream stream = BootstrapApplication.class.getResourceAsStream("/instant-run.zip");
        if (stream == null) {
            Log.v(LOG_TAG, "Could not find slices in APK; aborting.");
            return false;
        }
        try {
            ZipInputStream zipInputStream = new ZipInputStream(stream);
            try {
                byte[] buffer = new byte[2000];

                for (ZipEntry entry = zipInputStream.getNextEntry();
                        entry != null;
                        entry = zipInputStream.getNextEntry()) {
                    String name = entry.getName();
                    // Don't extract META-INF data
                    if (name.startsWith("META-INF")) {
                        continue;
                    }
                    if (!entry.isDirectory()
                            && name.indexOf('/') == -1 // only files in root directory
                            && name.endsWith(CLASSES_DEX_SUFFIX)) {
                        // Using / as separators in both .zip files and on Android, no need to convert
                        // to File.separator

                        // Map slice name to the scheme already used by the code to push slices
                        // via the embedded server as well as the code to push via adb:
                        //   slice-<slicedir>
                        File dest = new File(dexFolder, Paths.DEX_SLICE_PREFIX + name);

                        if (apkModified > 0) {
                            long sliceModified = dest.lastModified();
                            if (sliceModified > apkModified) {
                                // Ignore this slice: disk copy more recent than APK copy
                                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                                    Log.v(LOG_TAG, "Ignoring slice " + name
                                            + ": newer on disk than in APK");
                                }
                                continue;
                            }
                        }
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "Extracting slice " + name + " into " + dest);
                        }
                        File parent = dest.getParentFile();
                        if (parent != null && !parent.exists()) {
                            boolean created = parent.mkdirs();
                            if (!created) {
                                Log.wtf(LOG_TAG, "Failed to create directory " + dest);
                                return false;
                            }
                        }

                        OutputStream src = new BufferedOutputStream(new FileOutputStream(dest));
                        try {
                            int bytesRead;
                            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                                src.write(buffer, 0, bytesRead);
                            }
                        } finally {
                            src.close();
                        }
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "File written at " + System.currentTimeMillis());
                            Log.v(LOG_TAG, "File last modified reported : " + dest.lastModified());
                        }
                    }
                }

                return true;
            } catch (IOException ioe) {
                Log.wtf(LOG_TAG, "Failed to extract slices into directory " + dexFolder, ioe);
                return false;
            } finally {
                try {
                    zipInputStream.close();
                } catch (IOException ignore) {
                }
            }
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
    }

    /** Produces the next available dex file path */
    @Nullable
    public static File getNextDexFile() {
        // Find the file name of the next dex file to write
        File dexFolder = getDexFileFolder(getDataFolder(), true);
        if (dexFolder == null) {
            return null;
        }
        File[] files = dexFolder.listFiles();
        int max = -1;

        // Pick highest available number + 1 - we want these to be sortable
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(CLASSES_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
                    String middle = name.substring(CLASSES_DEX_PREFIX.length(),
                            name.length() - CLASSES_DEX_SUFFIX.length());
                    try {
                        int version = Integer.decode(middle);
                        if (version > max) {
                            max = version;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }

        String fileName = String.format("%s0x%04x%s", CLASSES_DEX_PREFIX, max + 1,
                CLASSES_DEX_SUFFIX);
        File file = new File(dexFolder, fileName);

        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "Writing new dex file: " + file);
        }

        return file;
    }

    /** Produces the next available dex file name */
    @Nullable
    public static File getTempDexFile() {
        // Find the file name of the next dex file to write
        File dexFolder = getTempDexFileFolder(getDataFolder());
        if (!dexFolder.exists()) {
            boolean created = dexFolder.mkdirs();
            if (!created) {
                Log.e(LOG_TAG, "Failed to create directory " + dexFolder);
                return null;
            }
        }
        File[] files = dexFolder.listFiles();
        int max = -1;

        // Pick highest available number + 1 - we want these to be sortable
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(RELOAD_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
                    String middle = name.substring(RELOAD_DEX_PREFIX.length(),
                            name.length() - CLASSES_DEX_SUFFIX.length());
                    try {
                        int version = Integer.decode(middle);
                        if (version > max) {
                            max = version;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }

        String fileName = String.format("%s0x%04x%s", RELOAD_DEX_PREFIX, max + 1,
                CLASSES_DEX_SUFFIX);
        File file = new File(dexFolder, fileName);

        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "Writing new dex file: " + file);
        }

        return file;
    }

    public static boolean writeRawBytes(@NonNull File destination, @NonNull byte[] bytes) {
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
            try {
                output.write(bytes);
                output.flush();
                return true;
            } finally {
                output.close();
            }
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Failed to write file " + destination, ioe);
        }
        return false;
    }

    public static boolean extractZip(@NonNull File destination, @NonNull byte[] zipBytes) {
        InputStream inputStream = new ByteArrayInputStream(zipBytes);
        return extractZip(destination, inputStream);
    }

    public static boolean extractZip(@NonNull File destDir, @NonNull InputStream inputStream) {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        try {
            byte[] buffer = new byte[2000];

            for (ZipEntry entry = zipInputStream.getNextEntry();
                    entry != null;
                    entry = zipInputStream.getNextEntry()) {
                String name = entry.getName();
                // Don't extract META-INF data
                if (name.startsWith("META-INF")) {
                    continue;
                }
                if (!entry.isDirectory()) {
                    // Using / as separators in both .zip files and on Android, no need to convert
                    // to File.separator
                    File dest = new File(destDir, name);
                    File parent = dest.getParentFile();
                    if (parent != null && !parent.exists()) {
                        boolean created = parent.mkdirs();
                        if (!created) {
                            Log.e(LOG_TAG, "Failed to create directory " + dest);
                            return false;
                        }
                    }

                    OutputStream src = new BufferedOutputStream(new FileOutputStream(dest));
                    try {
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            src.write(buffer, 0, bytesRead);
                        }
                    } finally {
                        src.close();
                    }
                }
            }

            return true;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Failed to extract zip contents into directory " + destDir, ioe);
            return false;
        } finally {
            try {
                zipInputStream.close();
            } catch (IOException ignore) {
            }
        }
    }

    public static void startUpdate() {
        // Wipe the back-buffer, if already present
        getWriteFolder(true);
    }

    public static void finishUpdate(boolean wroteResources) {
        if (wroteResources) {
            swapFolders();
        }
    }

    @Nullable
    public static File writeDexShard(@NonNull byte[] bytes, @NonNull String name) {
        File dexFolder = getDexFileFolder(getDataFolder(), true);
        if (dexFolder == null) {
            return null;
        }
        File file = new File(dexFolder, name);
        writeRawBytes(file, bytes);
        return file;
    }

    @Nullable
    public static File writeDexFile(@NonNull byte[] bytes, boolean writeIndex) {
        File file = getNextDexFile();
        if (file != null && Build.VERSION.SDK_INT < 21) {
            writeDexFile(bytes, writeIndex, file);
        }

        return file;
    }

    @Nullable
    public static File writeDexFile(@NonNull byte[] bytes, boolean writeIndex,
            @NonNull File file) {
        writeRawBytes(file, bytes);
        if (writeIndex) {
            File indexFile = getIndexFile(file);
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(indexFile), getUtf8Charset()));
                try {
                    // Use a temporary jar file with a unique name to avoid caching issues
                    // when reusing the file names between restart.dex and reload.dex
                    File tmpFile = File.createTempFile("install", ".jar");
                    Log.i(LOG_TAG, "Temp jar file : " + tmpFile.getAbsolutePath());
                    JarOutputStream jarOutputStream = new JarOutputStream(
                            new BufferedOutputStream(new FileOutputStream(tmpFile)));
                    try {
                        jarOutputStream.putNextEntry(new ZipEntry("classes.dex"));
                        jarOutputStream.write(bytes);
                        jarOutputStream.closeEntry();
                    } finally {
                        jarOutputStream.close();
                    }
                    DexFile dexFile = DexFile.loadDex(
                            tmpFile.getAbsolutePath(),
                            new File(file.getParentFile(), tmpFile.getName()).getAbsolutePath(), 0);
                    if (!tmpFile.delete()) {
                        Log.i(LOG_TAG, "Cannot delete " + tmpFile.getAbsolutePath());
                    }
                    Enumeration<String> entries = dexFile.entries();
                    while (entries.hasMoreElements()) {
                        String nextPath = entries.nextElement();

                        // Skip inner classes: we only care about classes at the
                        // compilation unit level
                        if (nextPath.indexOf('$') != -1) {
                            continue;
                        }

                        writer.write(nextPath);
                        writer.write('\n');
                    }
                } finally {
                    writer.close();
                }

                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Wrote restart patch index " + indexFile);
                }
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Failed to write dex index file " + indexFile, ioe);
            }
        }

        return file;
    }

    public static void writeAaptResources(@NonNull String relativePath, @NonNull byte[] bytes) {
        // TODO: Take relativePath into account for the actual destination file
        File resourceFile = getResourceFile(getWriteFolder(false));
        File file = resourceFile;
        if (USE_EXTRACTED_RESOURCES) {
            file = new File(file, relativePath);
        }
        File folder = file.getParentFile();
        if (!folder.isDirectory()) {
            boolean created = folder.mkdirs();
            if (!created) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Cannot create local resource file directory " + folder);
                }
                return;
            }
        }

        if (relativePath.equals(RESOURCE_FILE_NAME)) {
            //noinspection ConstantConditions
            if (USE_EXTRACTED_RESOURCES) {
                extractZip(resourceFile, bytes);
            } else {
                writeRawBytes(file, bytes);
            }
        } else {
            writeRawBytes(file, bytes);
        }
    }

    @Nullable
    public static String writeTempDexFile(byte[] bytes) {
        File file = getTempDexFile();
        if (file != null) {
            writeRawBytes(file, bytes);
            return file.getPath();
        } else {
            Log.e(LOG_TAG, "No file to write temp dex content to");
        }
        return null;
    }

    /** Returns the class index file for the given .dex file */
    private static File getIndexFile(@NonNull File file) {
        return new File(file.getPath() + EXT_INDEX_FILE);
    }

    /** Returns the dex file for the given index file */
    private static File getDexFile(@NonNull File file) {
        String path = file.getPath();
        return new File(path.substring(0, path.length() - EXT_INDEX_FILE.length()));
    }

    /**
     * Removes .dex files from the temp dex file folder
     */
    public static void purgeTempDexFiles(@NonNull File dataFolder) {
        File dexFolder = getTempDexFileFolder(dataFolder);
        if (!dexFolder.isDirectory()) {
            return;
        }
        File[] files = dexFolder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.getPath().endsWith(CLASSES_DEX_SUFFIX)) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.e(LOG_TAG, "Could not delete temp dex file " + file);
                }
            }
        }
    }

    /**
     * Removes .dex files that contain only classes seen in later patches (e.g. none
     * of the classes in the .dex file will be found by the application class loader
     * because there are newer versions available)
     */
    public static void purgeMaskedDexFiles(@NonNull File dataFolder, long apkModified) {
        File dexFolder = getDexFileFolder(dataFolder, false);
        if (dexFolder == null) {
            return;
        }
        File[] files = dexFolder.listFiles();
        if (files == null || files.length < 2) {
            return;
        }
        Arrays.sort(files);

        // Go back through patches in reverse order, and for any patch that contains a
        // class not seen in later patches, mark that patch file as relevant
        Set<String> classes = new HashSet<String>(200);
        // contains the list of dex file that have an index file associated.

        Charset utf8 = getUtf8Charset();
        for (int i = files.length - 1; i >= 0; i--) {
            File file = files[i];
            String path = file.getPath();
            if (path.endsWith(EXT_INDEX_FILE)) {
                try {
                    boolean containsUniqueClasses = false;
                    InputStreamReader is = new InputStreamReader(new FileInputStream(file), utf8);
                    BufferedReader reader = new BufferedReader(is);
                    try {

                        while (true) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            }
                            if (line.isEmpty()) {
                                continue;
                            }
                            if (!classes.contains(line)) {
                                classes.add(line);
                                containsUniqueClasses = true;
                            }
                        }
                    } finally {
                        reader.close();
                    }

                    // check if the dex file is not older than the APK.
                    File dexFile = getDexFile(file);

                    if (!containsUniqueClasses || dexFile.lastModified() < apkModified) {
                        // Nearly always true, unless user has gone in there and deleted
                        // stuff
                        if (dexFile.exists()) {
                            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                                Log.i(LOG_TAG, "Removing dex patch " + dexFile
                                        + ": All classes in it are hidden by later patches");
                            }
                            boolean deleted = dexFile.delete();
                            if (!deleted) {
                                Log.e(LOG_TAG, "Could not prune " + dexFile);
                            } else {
                                Log.i(LOG_TAG, "pruned " + file.getAbsolutePath());
                            }
                        }
                        boolean deleted = file.delete();
                        if (!deleted) {
                            Log.e(LOG_TAG, "Could not prune " + file);
                        }

                    }
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "Could not read dex index file " + file, ioe);
                }
            }
        }

        // TODO: Consider reordering the files here such that we close holes and
        // stay with lower patch numbers
    }

    @NonNull
    private static Charset getUtf8Charset() {
        Charset utf8;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            utf8 = StandardCharsets.UTF_8;
        } else {
            utf8 = Charset.forName("UTF-8");
        }
        return utf8;
    }

    public static long getFileSize(@NonNull String path) {
        // Currently only handle this for resource files
        if (path.equals(RESOURCE_FILE_NAME)) {
            File file = getExternalResourceFile();
            if (file != null) {
                return file.length();
            }
        }

        return -1;
    }

    @Nullable
    public static byte[] getCheckSum(@NonNull String path) {
        // Currently only handle this for resource files
        if (path.equals(RESOURCE_FILE_NAME)) {
            File file = getExternalResourceFile();
            if (file != null) {
                return getCheckSum(file);
            }
        }

        return null;
    }

    /**
     * Computes a checksum of a file.
     *
     * @param file the file to compute the fingerprint for
     * @return a fingerprint
     */
    @Nullable
    public static byte[] getCheckSum(@NonNull File file) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[4096];
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                while (true) {
                    int read = input.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    digest.update(buffer, 0, read);
                }
                return digest.digest();
            } finally {
                input.close();
            }
        } catch (NoSuchAlgorithmException e) {
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "Couldn't look up message digest", e);
            }
        } catch (IOException ioe) {
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "Failed to read file " + file, ioe);
            }
        } catch (Throwable t) {
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "Unexpected checksum exception", t);
            }
        }
        return null;
    }

    public static byte[] readRawBytes(@NonNull File source) {
        try {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Reading the bytes for file " + source);
            }
            long length = source.length();
            if (length > Integer.MAX_VALUE) {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "File too large (" + length + ")");
                }
                return null;
            }
            byte[] result = new byte[(int)length];

            BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
            try {
                int index = 0;
                int remaining = result.length - index;
                while (remaining > 0) {
                    int numRead = input.read(result, index, remaining);
                    if (numRead == -1) {
                        break;
                    }
                    index += numRead;
                    remaining -= numRead;
                }
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Returning length " + result.length + " for file " + source);
                }
                return result;
            } finally {
                input.close();
            }
        } catch (IOException ioe) {
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "Failed to read file " + source, ioe);
            }
        }
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "I/O error, no bytes returned for " + source);
        }
        return null;
    }
}
