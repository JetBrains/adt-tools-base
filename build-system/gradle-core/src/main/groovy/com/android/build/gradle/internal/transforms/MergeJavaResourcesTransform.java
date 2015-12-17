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

package com.android.build.gradle.internal.transforms;

import static com.android.SdkConstants.FD_APK_NATIVE_LIBS;
import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.model.PackagingOptions;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.signing.SignedJarBuilder;
import com.android.ide.common.packaging.PackagingUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Transform to merge all the Java resources.
 *
 * Based on the value of {@link #getInputTypes()} this will either process native libraries
 * or java resources. While native libraries inside jars are technically java resources, they
 * must be handled separately.
 */
public class MergeJavaResourcesTransform extends Transform {

    private interface FileValidator {
        boolean validateJarPath(@NonNull String path);
        boolean validateFolderPath(@NonNull String path);
        @NonNull
        String folderPathToKey(@NonNull String path);
        @NonNull
        String keyToFolderPath(@NonNull String path);
    }

    @NonNull
    private final PackagingOptions packagingOptions;

    @NonNull
    private final String name;

    @NonNull
    private final Set<Scope> mergeScopes;
    @NonNull
    private final Set<ContentType> mergedType;
    @NonNull
    private final FileValidator validator;

    public MergeJavaResourcesTransform(
            @NonNull PackagingOptions packagingOptions,
            @NonNull Set<Scope> mergeScopes,
            @NonNull ContentType mergedType,
            @NonNull String name) {
        this.packagingOptions = packagingOptions;
        this.name = name;
        this.mergeScopes = Sets.immutableEnumSet(mergeScopes);
        this.mergedType = ImmutableSet.of(mergedType);


        if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
            validator = new FileValidator() {
                @Override
                public boolean validateJarPath(@NonNull String path) {
                    return !path.endsWith(SdkConstants.DOT_CLASS) &&
                            !path.endsWith(SdkConstants.DOT_NATIVE_LIBS);
                }

                @Override
                public boolean validateFolderPath(@NonNull String path) {
                    return !path.endsWith(SdkConstants.DOT_CLASS) &&
                            !path.endsWith(SdkConstants.DOT_NATIVE_LIBS);

                }

                @NonNull
                @Override
                public String folderPathToKey(@NonNull String path) {
                    return path;
                }

                @NonNull
                @Override
                public String keyToFolderPath(@NonNull String path) {
                    return path;
                }
            };

        } else if (mergedType == ExtendedContentType.NATIVE_LIBS) {
            validator = new NativeLibValidator();

        } else {
            throw new UnsupportedOperationException(
                    "mergedType param must be RESOURCES or NATIVE_LIBS");
        }
    }

    private static final class NativeLibValidator implements FileValidator {
        private final Pattern jarAbiPattern = Pattern.compile("lib/([^/]+)/[^/]+");
        private final Pattern folderAbiPattern = Pattern.compile("([^/]+)/[^/]+");
        private final Pattern filenamePattern = Pattern.compile(".*\\.so");

        @Override
        public boolean validateJarPath(@NonNull String path) {
            // extract abi from path, checking the general path structure (lib/<abi>/<filename>)
            Matcher m = jarAbiPattern.matcher(path);

            // if the ABI is accepted, check the 3rd segment
            if (m.matches()) {
                // remove the beginning of the path (lib/<abi>/)
                String filename = path.substring(5 + m.group(1).length());
                // and check the filename
                return filenamePattern.matcher(filename).matches() ||
                        SdkConstants.FN_GDBSERVER.equals(filename) ||
                        SdkConstants.FN_GDB_SETUP.equals(filename);
            }

            return false;
        }

        @Override
        public boolean validateFolderPath(@NonNull String path) {
            // extract abi from path, checking the general path structure (<abi>/<filename>)
            Matcher m = folderAbiPattern.matcher(path);

            // if the ABI is accepted, check the 3rd segment
            if (m.matches()) {
                // remove the beginning of the path (<abi>/)
                String filename = path.substring(1 + m.group(1).length());
                // and check the filename
                return filenamePattern.matcher(filename).matches() ||
                        SdkConstants.FN_GDBSERVER.equals(filename) ||
                        SdkConstants.FN_GDB_SETUP.equals(filename);
            }

            return false;
        }

        @NonNull
        @Override
        public String folderPathToKey(@NonNull String path) {
            return FD_APK_NATIVE_LIBS + "/" + path;
        }

        @NonNull
        @Override
        public String keyToFolderPath(@NonNull String path) {
            return path.substring(FD_APK_NATIVE_LIBS.length() + 1);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return mergedType;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return mergeScopes;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        // TODO the inputs that controls the merge.
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        // FIXME
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException {

        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // folder to copy the files that were originally in folders.
        File outFolder = null;
        // jar to copy the files that came from jars.
        File outJar = null;

        Set<String> excludes = ImmutableSet.copyOf(packagingOptions.getExcludes());
        Set<String> pickFirsts = ImmutableSet.copyOf(packagingOptions.getPickFirsts());
        Set<String> merges = ImmutableSet.copyOf(packagingOptions.getMerges());

        if (!isIncremental) {
            outputProvider.deleteAll();

            // gather all the inputs.
            ListMultimap<String, QualifiedContent> sourceFileList = ArrayListMultimap.create();
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    gatherListFromJar(jarInput, sourceFileList);
                }

                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    gatherListFromFolder(directoryInput, sourceFileList);
                }
            }

            // at this point we have what we need, write the output.

            // we're recording all the files that must be merged.
            // this is a map of (archive path -> source folder/jar)
            ListMultimap<String, File> mergedFiles = ArrayListMultimap.create();

            // we're also going to record for each jar which files comes from it.
            ListMultimap<File, String> jarSources = ArrayListMultimap.create();

            for (String key : sourceFileList.keySet()) {
                // first thing we do is check if it's excluded.
                if (excludes.contains(key)) {
                    // skip, no need to do anything else.
                    continue;
                }

                List<QualifiedContent> contentSourceList = sourceFileList.get(key);

                // if there is only one content or if one of the source is PROJECT then it wins.
                // This is similar behavior as the other merger (assets, res, manifest).
                QualifiedContent selectedContent = findUniqueOrProjectContent(contentSourceList);

                // otherwise search for a selection
                if (selectedContent == null) {
                    if (pickFirsts.contains(key)) {
                        // if pickFirst then just pick the first one.
                        selectedContent = contentSourceList.get(0);
                    } else if (merges.contains(key)) {
                        // if it's selected for merging, we need to record this for later where
                        // we'll merge all the files we've found.
                        for (QualifiedContent content : contentSourceList) {
                            mergedFiles.put(key, content.getFile());
                        }
                    } else {
                        // finally if it's not excluded, then this is an error.
                        // collect the sources.
                        List<File> sources = Lists
                                .newArrayListWithCapacity(contentSourceList.size());
                        for (QualifiedContent content : contentSourceList) {
                            sources.add(content.getFile());
                        }
                        throw new TransformException(new DuplicateFileException(key, sources));
                    }
                }

                // if a file was selected, write it here.
                if (selectedContent != null) {
                    if (selectedContent instanceof JarInput) {
                        // or just record it for now if it's coming from a jar.
                        // This will allow to open these source jars just once to copy
                        // all their content out.
                        jarSources.put(selectedContent.getFile(), key);
                    } else {
                        if (outFolder == null) {
                            outFolder = outputProvider.getContentLocation(
                                    "main",
                                    getOutputTypes(), getScopes(),
                                    Format.DIRECTORY);
                            mkdirs(outFolder);
                        }
                        copyFromFolder(selectedContent.getFile(), outFolder, key);
                    }
                }
            }

            // now copy all the non-merged files into the jar.
            JarMerger jarMerger = null;
            if (!jarSources.isEmpty()) {
                outJar = outputProvider.getContentLocation(
                        "main", getOutputTypes(), getScopes(), Format.JAR);
                mkdirs(outJar.getParentFile());
                jarMerger = copyIntoJar(jarSources, outJar);
            }

            // then handle the merged files.
            if (!mergedFiles.isEmpty()) {
                for (String key : mergedFiles.keySet()) {
                    List<File> sourceFiles = mergedFiles.get(key);

                    // first check if we have a jar source
                    boolean hasJarSource = false;
                    for (File sourceFile : sourceFiles) {
                        if (sourceFile.isDirectory()) {
                            hasJarSource = true;
                            break;
                        }
                    }

                    // merge the content into a ByteArrayOutputStream.
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (File sourceFile : sourceFiles) {
                        if (sourceFile.isDirectory()) {
                            File actualFile = computeFile(sourceFile, validator.keyToFolderPath(key));
                            baos.write(Files.toByteArray(actualFile));
                        } else {
                            ZipFile zipFile = new ZipFile(sourceFile);
                            try {
                                ByteStreams.copy(
                                        zipFile.getInputStream(zipFile.getEntry(key)), baos);
                            } finally {
                                zipFile.close();
                            }
                        }
                    }

                    if (hasJarSource) {
                        // if we haven't written into the outjar, create it.
                        if (outJar == null) {
                            outJar = outputProvider.getContentLocation(
                                    "main", getOutputTypes(), getScopes(), Format.JAR);
                            mkdirs(outJar.getParentFile());
                            jarMerger = new JarMerger(outJar);
                        }

                        jarMerger.addEntry(key, baos.toByteArray());
                    } else {
                        if (outFolder == null) {
                            outFolder = outputProvider.getContentLocation(
                                    "main",
                                    getOutputTypes(), getScopes(),
                                    Format.DIRECTORY);
                            mkdirs(outFolder);
                        }

                        Files.write(baos.toByteArray(), computeFile(outFolder, key));
                    }
                }
            }

            if (jarMerger != null) {
                jarMerger.close();
            }
        }
    }

    @Nullable
    private static QualifiedContent findUniqueOrProjectContent(
            @NonNull List<QualifiedContent> contentSourceList) {
        if (contentSourceList.size() == 1) {
            return contentSourceList.get(0);
        }

        for (QualifiedContent content : contentSourceList) {
            if (content.getScopes().contains(Scope.PROJECT)) {
                return content;
            }
        }

        return null;
    }

    private void copyFromFolder(
            @NonNull File fromFolder,
            @NonNull File toFolder,
            @NonNull String path)
            throws IOException {
        File from = computeFile(fromFolder, validator.keyToFolderPath(path));
        File to = computeFile(toFolder, path);
        mkdirs(to.getParentFile());
        Files.copy(from, to);
    }

    /**
     * computes a file path from a root folder and a zip archive path.
     * @param rootFolder the root folder
     * @param path the archive path
     * @return the File
     */
    private static File computeFile(@NonNull File rootFolder, @NonNull String path) {
        path = path.replace('/', File.separatorChar);
        return new File(rootFolder, path);
    }

    private static class JarFilter implements SignedJarBuilder.IZipEntryFilter {
        private final Set<String> allowedPath = Sets.newHashSet();

        void resetList(@NonNull List<String> paths) {
            allowedPath.clear();
            allowedPath.addAll(paths);
        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return allowedPath.contains(archivePath);
        }
    }

    private static JarMerger copyIntoJar(@NonNull ListMultimap<File, String> jarSources,
            @NonNull File outJar)
            throws IOException {
        JarMerger jarMerger = new JarMerger(outJar);

        JarFilter filter = new JarFilter();
        jarMerger.setFilter(filter);

        for (File jarFile : jarSources.keySet()) {
            // reset filter to allow the expected list of files for that particular jar file.
            filter.resetList(jarSources.get(jarFile));

            // copy the jar file
            jarMerger.addJar(jarFile);
        }

        return jarMerger;
    }

    private void gatherListFromJar(
            @NonNull JarInput jarInput,
            @NonNull ListMultimap<String, QualifiedContent> content) throws IOException {

        ZipFile zipFile = new ZipFile(jarInput.getFile());
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                String path = entry.getName();
                if (skipEntry(entry, path)) {
                    continue;
                }

                content.put(path, jarInput);
            }

        } finally {
            zipFile.close();
        }
    }

    private boolean skipEntry(
            @NonNull ZipEntry entry,
            @NonNull String path) {
        if (entry.isDirectory() ||
                JarFile.MANIFEST_NAME.equals(path) ||
                !validator.validateJarPath(path)) {
            return true;
        }

        // split the path into segments.
        String[] segments = path.split("/");

        // empty path? skip to next entry.
        if (segments.length == 0) {
            return true;
        }

        // Check each folders to make sure they should be included.
        // Folders like CVS, .svn, etc.. should already have been excluded from the
        // jar file, but we need to exclude some other folder (like /META-INF) so
        // we check anyway.
        for (int i = 0 ; i < segments.length - 1; i++) {
            if (!PackagingUtils.checkFolderForPackaging(segments[i])) {
                return true;
            }
        }

        return !PackagingUtils.checkFileForPackaging(segments[segments.length-1],
                false /*allowClassFiles*/);
    }

    private void gatherListFromFolder(
            @NonNull DirectoryInput directoryInput,
            @NonNull ListMultimap<String, QualifiedContent> content) {
        gatherListFromFolder(directoryInput.getFile(), "", directoryInput, content);
    }

    private void gatherListFromFolder(
            @NonNull File file,
            @NonNull String path,
            @NonNull DirectoryInput directoryInput,
            @NonNull ListMultimap<String, QualifiedContent> content) {
        File[] children = file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {

                return file.isDirectory() || !name.endsWith(SdkConstants.DOT_CLASS);
            }
        });

        if (children != null) {
            for (File child : children) {
                String newPath = path.isEmpty() ? child.getName() : path + '/' + child.getName();
                if (child.isDirectory()) {
                    gatherListFromFolder(
                            child,
                            newPath,
                            directoryInput,
                            content);
                } else if (child.isFile() && validator.validateFolderPath(newPath)) {
                    content.put(validator.folderPathToKey(newPath), directoryInput);
                }
            }
        }
    }
}
