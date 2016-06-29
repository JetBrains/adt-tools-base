/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.packaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.model.AaptOptions;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Utility class for packaging.
 */
public class PackagingUtils {

    /**
     * Checks whether a folder and its content is valid for packaging into the .apk as
     * standard Java resource.
     * @param folderName the name of the folder.
     *
     * @return true if the folder is valid for packaging.
     */
    public static boolean checkFolderForApkPackaging(@NonNull String folderName) {
        return !folderName.equalsIgnoreCase("CVS") &&
                !folderName.equalsIgnoreCase(".svn") &&
                !folderName.equalsIgnoreCase("SCCS") &&
                !folderName.startsWith("_");
    }

    /**
     * Set of file formats which are already compressed, or don't compress well, same as the one
     * used by aapt.
     */
    public static final ImmutableSet<String> DEFAULT_DONT_COMPRESS_EXTENSIONS =
            ImmutableSet.of(
                    ".jpg", ".jpeg", ".png", ".gif", ".wav", ".mp2", ".mp3", ".ogg", ".aac", ".mpg",
                    ".mpeg", ".mid", ".midi", ".smf", ".jet", ".rtttl", ".imy", ".xmf", ".mp4",
                    ".m4a", ".m4v", ".3gp", ".3gpp", ".3g2", ".3gpp2", ".amr", ".awb", ".wma",
                    ".wmv", ".webm", ".mkv");


    /**
     * Checks a file to make sure it should be packaged as standard resources.
     * @param filePath OS-independent path of the file (including extension), relative to the
     *                 archive
     * @param allowClassFiles whether to allow java class files
     * @return true if the file should be packaged as standard java resources
     */
    public static boolean checkFileForApkPackaging(
            @NonNull String filePath, boolean allowClassFiles) {
        String fileName = new File(filePath).getName();

        // ignore hidden files and backup files
        return !(fileName.charAt(0) == '.' || fileName.charAt(fileName.length() - 1) == '~')
                && !isOfNonResourcesExtensions(Files.getFileExtension(fileName), allowClassFiles)
                && !isNotAResourceFile(fileName)
                && !filePath.equals("META-INF/MANIFEST.MF")
                && !isUsedForSigning(filePath)
                && !isMavenMetadata(filePath);
    }

    private static boolean isMavenMetadata(String filePath) {
        return filePath.startsWith("META-INF/maven");
    }

    private static boolean isUsedForSigning(String filePath) {
        if (!"META-INF".equals(new File(filePath).getParent())) {
            return false;
        }

        String fileExtension = Files.getFileExtension(filePath);
        for (String extension : SIGNING_EXTENSIONS) {
            if (fileExtension.equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isOfNonResourcesExtensions(
            @NonNull String extension,
            boolean allowClassFiles) {
        for (String ext : NON_RESOURCES_EXTENSIONS) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return !allowClassFiles && SdkConstants.EXT_CLASS.equals(extension);
    }

    private static boolean isNotAResourceFile(@NonNull String fileName) {
        for (String name : NON_RESOURCES_FILENAMES) {
            if (name.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * List of file extensions that represents non resources files.
     */
    private static final ImmutableList<String> NON_RESOURCES_EXTENSIONS =
            ImmutableList.<String>builder()
                    .add("aidl")            // Aidl files
                    .add("rs")              // RenderScript files
                    .add("fs")              // FilterScript files
                    .add("rsh")             // RenderScript header files
                    .add("d")               // Dependency files
                    .add("java")            // Java files
                    .add("scala")           // Scala files
                    .add("scc")             // VisualSourceSafe
                    .add("swp")             // vi swap file
                    .build();

    /**
     * List of file extensions that are used for jar signing.
     */
    public static final ImmutableList<String> SIGNING_EXTENSIONS =
            ImmutableList.of("SF", "RSA", "DSA", "EC");

    /**
     * File names that are not resource files.
     */
    private static final ImmutableList<String> NON_RESOURCES_FILENAMES =
            ImmutableList.<String>builder()
                    .add("thumbs.db")       // image index file
                    .add("picasa.ini")      // image index file
                    .add("about.html")      // Javadoc
                    .add("package.html")    // Javadoc
                    .add("overview.html")   // Javadoc
                    .build();

    /**
     * Computes an "application hash", a reasonably unique identifier for an app.
     * <p>
     * This is currently used by Instant Run to prevent apps on a device from guessing
     * the authentication token associated with an instant run developed app on the same
     * device.
     * <p>
     * This method creates the "secret", the field in the AppInfo class which is used as a simple
     * authentication token between the IDE and the app server.
     * <p>
     * This is not a cryptographically strong unique id; we could attempt to make a truly random
     * number here, but we'd need to store that id somewhere such that subsequent builds
     * will use the same secret, to avoid having the IDE and the app getting out of sync,
     * and there isn't really a natural place for us to store the key to make it survive across
     * a clean build. (One possibility is putting it into local.properties).
     * <p>
     * However, we have much simpler needs: we just need a key that can't be guessed from
     * a hostile app on the developer's device, and it only has a few guesses (after providing
     * the wrong secret to the server a few times, the server will shut down.) We can't
     * rely on the package name alone, since the port number is known, and the package name is
     * discoverable by the hostile app (by querying the contents of /data/data/*). Therefore
     * we also include facts that the hostile app can't know, such as as the path on the
     * developer's machine to the app project and the name of the developer's machine, etc.
     * The goal is for this secret to be reasonably stable (e.g. the same from build to build)
     * yet not something an app could guess if it only has a couple of tries.
     */
    public static long computeApplicationHash(@NonNull File projectDir) {
        HashFunction hashFunction = Hashing.md5();
        Hasher hasher = hashFunction.newHasher();
        try {
            projectDir = projectDir.getCanonicalFile();
        } catch (IOException ignore) {
            // If this throws an exception the assignment won't
            // be done and we'll stick with the regular project dir
        }
        String path = projectDir.getPath();
        hasher.putBytes(path.getBytes(Charsets.UTF_8));
        String user = System.getProperty("user.name");
        if (user != null) {
            hasher.putBytes(user.getBytes(Charsets.UTF_8));
        }
        return hasher.hash().asLong();
    }

    @NonNull
    public static Predicate<String> getDefaultNoCompressPredicate() {
        return getNoCompressPredicateForExtensions(DEFAULT_DONT_COMPRESS_EXTENSIONS);
    }

    @NonNull
    public static Predicate<String> getNoCompressPredicate(
            @NonNull AaptOptions aaptOptions,
            @NonNull File manifest) {
        checkNotNull(aaptOptions);
        checkState(manifest.exists());

        NativeLibrariesPackagingMode packagingMode =
                getNativeLibrariesLibrariesPackagingMode(manifest);

        return getNoCompressPredicateForExtensions(
                getAllNoCompressExtensions(aaptOptions, packagingMode));
    }

    @NonNull
    public static NativeLibrariesPackagingMode getNativeLibrariesLibrariesPackagingMode(
            @NonNull File manifest) {
        checkState(manifest.exists());
        DefaultManifestParser parser = new DefaultManifestParser(manifest);
        Boolean extractNativeLibs = parser.getExtractNativeLibs();

        // The default is "true", so we only package *.so files differently if the user explicitly
        // set this to "false".
        if (Boolean.FALSE.equals(extractNativeLibs)) {
            return NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED;
        } else {
            return NativeLibrariesPackagingMode.COMPRESSED;
        }
    }

    @NonNull
    private static Predicate<String> getNoCompressPredicateForExtensions(
            @NonNull Iterable<String> noCompressExtensions) {
        return name -> {
            for (String extension : noCompressExtensions) {

                //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale - extensions are ASCII
                if (name.toLowerCase().endsWith(extension)) {
                    return true;
                }
            }
            return false;
        };
    }

    @NonNull
    private static List<String> getAllNoCompressExtensions(
            @NonNull AaptOptions aaptOptions,
            @NonNull NativeLibrariesPackagingMode nativeLibrariesPackagingMode) {
        List<String> result = Lists.newArrayList(DEFAULT_DONT_COMPRESS_EXTENSIONS);

        if (nativeLibrariesPackagingMode == NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED) {
            result.add(SdkConstants.DOT_NATIVE_LIBS);
        }

        if (aaptOptions.getNoCompress() != null) {
            result.addAll(aaptOptions.getNoCompress());
        }
        return result;
    }
}
