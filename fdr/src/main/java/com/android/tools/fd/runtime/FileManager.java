package com.android.tools.fd.runtime;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexFile;

import static com.android.tools.fd.runtime.AppInfo.applicationId;
import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

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
    private static final String RESOURCE_FILE_NAME = "resources.ap_";
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

    /** Suffix for classes.dex files */
    public static final String CLASSES_DEX_SUFFIX = ".dex";

    /** Suffix for classes.dex for hot-swapping files */
    public static final String CLASSES_DEX_3_SUFFIX = ".dex.3";

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
        return new File("/data/data/" + applicationId + "/files/studio-fd");
    }

    @NonNull
    private static File getResourceFile(File base) {
        //noinspection ConstantConditions
        return new File(base, USE_EXTRACTED_RESOURCES ? RESOURCE_FOLDER_NAME : RESOURCE_FILE_NAME);
    }

    /**
     * Returns the folder used for .dex files used during the next app start
     */
    @NonNull
    private static File getDexFileFolder(File base) {
        return new File(base, "dex");
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
        return new File(getDataFolder(), "lib");
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

    /** Returns the list of available .dex files to be loaded, possibly empty */
    @NonNull
    public static List<String> getDexList() {
        File dataFolder = getDataFolder();

        // Get rid of reload dex files from previous runs, if any
        FileManager.purgeTempDexFiles(dataFolder);
        // Get rid of patches no longer applicable
        FileManager.purgeMaskedDexFiles(dataFolder);

        List<String> list = new ArrayList<String>();

        // We don't need "double buffering" for dex files - we never rewrite files, so we
        // can accumulate in the same dir
        File[] dexFiles = getDexFileFolder(dataFolder).listFiles();
        if (dexFiles == null) {
            Log.v(LOG_TAG, "Cannot find newer dex classes, not patching them in");
            // TODO: Sort?
            return list;
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

    /** Produces the next available dex file name */
    @Nullable
    public static File getNextDexFile() {
        // Find the file name of the next dex file to write
        File dexFolder = getDexFileFolder(getDataFolder());
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

    public static boolean writeRawBytes(@NonNull File destination, @NonNull byte[] bytes) {
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
            try {
                output.write(bytes);
                return true;
            } finally {
                output.close();
            }
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Failed to write resource file " + destination, ioe);
        }
        return false;
    }

    public static boolean extractZip(@NonNull File destination, @NonNull byte[] zipBytes) {
        InputStream inputStream = new ByteArrayInputStream(zipBytes);
        return extractZip(destination, inputStream);
    }

    public static boolean extractZip(@NonNull File resourceDir, @NonNull InputStream inputStream) {
        try {
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);

            ZipEntry entry = zipInputStream.getNextEntry();
            byte[] buffer = new byte[2000];

            while (entry != null) {
                String name = entry.getName();
                // Don't extract META-INF data
                if (name.startsWith("META-INF")) {
                    continue;
                }
                // Using / as separators in both .zip files and on Android, no need to convert
                // to File.separator
                File dest = new File(resourceDir, name);
                if (!entry.isDirectory()) {
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
                entry = zipInputStream.getNextEntry();
            }

            zipInputStream.close();
            return true;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Failed to extract zip contents into directory " + resourceDir, ioe);
            return false;
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
    public static File writeDexFile(@NonNull byte[] bytes, boolean writeIndex) {
        File file = getNextDexFile();
        if (file != null) {
            writeRawBytes(file, bytes);
            if (writeIndex) {
                File indexFile = getIndexFile(file);
                try {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(indexFile), getUtf8Charset()));
                    DexFile dexFile = new DexFile(file);
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
                    writer.close();

                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        Log.i(LOG_TAG, "Wrote restart patch index " + indexFile);
                    }
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "Failed to write dex index file " + indexFile);
                }
            }
        }

        return file;
    }

    public static void writeAaptResources(@NonNull String relativePath, @NonNull byte[] bytes) {
        // TODO: Take relativePath into account for the actual destination file
        File file = getResourceFile(getWriteFolder(false));
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
                extractZip(file, bytes);
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
    public static void purgeMaskedDexFiles(@NonNull File dataFolder) {
        File dexFolder = getDexFileFolder(dataFolder);
        if (!dexFolder.isDirectory()) {
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

        Charset utf8 = getUtf8Charset();
        for (int i = files.length - 1; i >= 0; i--) {
            File file = files[i];
            String path = file.getPath();
            if (path.endsWith(EXT_INDEX_FILE)) {
                try {
                    InputStreamReader is = new InputStreamReader(new FileInputStream(file), utf8);
                    BufferedReader reader = new BufferedReader(is);

                    boolean containsUniqueClasses = false;

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

                    reader.close();

                    if (!containsUniqueClasses) {
                        File dexFile = getDexFile(file);
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
}
