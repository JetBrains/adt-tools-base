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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.JarMerger;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.signing.SignedJarBuilder.IZipEntryFilter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES,
 * and processes and combines them, and put them in the bundle folder.
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
public class LibraryJarTransform extends Transform {

    @NonNull
    private final File mainClassLocation;
    @NonNull
    private final File localJarsLocation;
    @NonNull
    private final String packagePath;
    private final boolean packageBuildConfig;

    @Nullable
    private List<ExcludeListProvider> excludeListProviders;

    public LibraryJarTransform(
            @NonNull File mainClassLocation,
            @NonNull File localJarsLocation,
            @NonNull String packageName,
            boolean packageBuildConfig) {
        this.mainClassLocation = mainClassLocation;
        this.localJarsLocation = localJarsLocation;
        this.packagePath = packageName.replace(".", "/");
        this.packageBuildConfig = packageBuildConfig;
    }

    public void addExcludeListProvider(ExcludeListProvider provider) {
        if (excludeListProviders == null) {
            excludeListProviders = Lists.newArrayList();
        }
        excludeListProviders.add(provider);
    }

    @NonNull
    @Override
    public String getName() {
        return "syncLibJars";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_LIBRARY;
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainClassLocation);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(localJarsLocation);
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> unusedInputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider unusedOutputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {
        List<String> excludes = Lists.newArrayListWithExpectedSize(5);

        // these must be regexp to match the zip entries
        excludes.add(packagePath + "/R.class");
        excludes.add(packagePath + "/R\\$(.*).class");
        excludes.add(packagePath + "/Manifest.class");
        excludes.add(packagePath + "/Manifest\\$(.*).class");
        if (!packageBuildConfig) {
            excludes.add(packagePath + "/BuildConfig.class");
        }
        if (excludeListProviders != null) {
            for (ExcludeListProvider provider : excludeListProviders) {
                List<String> list = provider.getExcludeList();
                if (list != null) {
                    excludes.addAll(list);
                }
            }
        }

        // create Pattern Objects.
        List<Pattern> patterns = Lists.newArrayListWithCapacity(excludes.size());
        for (String exclude : excludes) {
            patterns.add(Pattern.compile(exclude));
        }

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        List<QualifiedContent> mainScope = Lists.newArrayList();
        List<QualifiedContent> locaJlJarScope = Lists.newArrayList();

        for (TransformInput input : referencedInputs) {
            for (QualifiedContent qualifiedContent : Iterables.concat(
                    input.getJarInputs(), input.getDirectoryInputs())) {
                if (qualifiedContent.getScopes().contains(Scope.PROJECT)) {
                    // even if the scope contains both project + local jar, we treat this as main
                    // scope.
                    mainScope.add(qualifiedContent);
                } else {
                    locaJlJarScope.add(qualifiedContent);
                }
            }
        }

        // process main scope.
        if (mainScope.isEmpty()) {
            throw new RuntimeException("Empty Main scope for " + getName());
        }

        if (mainScope.size() == 1) {
            QualifiedContent content = mainScope.get(0);
            if (content instanceof JarInput) {
                copyJarWithContentFilter(content.getFile(), mainClassLocation, patterns);
            } else {
                jarFolderToRootLocation(content.getFile(), patterns);
            }
        } else {
            mergeToRootLocation(mainScope, patterns);
        }

        // process local scope
        processLocalJars(locaJlJarScope);
    }

    private void mergeToRootLocation(
            @NonNull List<QualifiedContent> qualifiedContentList,
            @NonNull final List<Pattern> excludes)
            throws IOException {
        JarMerger jarMerger = new JarMerger(mainClassLocation);
        jarMerger.setFilter(new IZipEntryFilter() {
            @Override
            public boolean checkEntry(String archivePath)
                    throws ZipAbortException {
                return LibraryJarTransform.checkEntry(excludes, archivePath);
            }
        });

        for (QualifiedContent content : qualifiedContentList) {
            System.out.println(content);
            if (content instanceof JarInput) {
                jarMerger.addJar(content.getFile());
            } else {
                jarMerger.addFolder(content.getFile());
            }
        }

        jarMerger.close();
    }

    private void processLocalJars(@NonNull List<QualifiedContent> qualifiedContentList)
            throws IOException {

        // first copy the jars (almost) as is, and remove them from the list.
        // then we'll make a single jars that contains all the folders.
        // Note that we do need to remove the resources from the jars since they have been merged
        // somewhere else.
        // TODO: maybe do the folders separately to handle incremental?

        IZipEntryFilter classOnlyFilter = new IZipEntryFilter() {
            @Override
            public boolean checkEntry(String archivePath)
                    throws ZipAbortException {
                return archivePath.endsWith(SdkConstants.DOT_CLASS);
            }
        };

        Iterator<QualifiedContent> iterator = qualifiedContentList.iterator();

        while (iterator.hasNext()) {
            QualifiedContent content = iterator.next();
            if (content instanceof JarInput) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                copyJarWithContentFilter(
                        content.getFile(),
                        new File(localJarsLocation, content.getFile().getName()),
                        classOnlyFilter);
                iterator.remove();
            }
        }

        // now handle the folders.
        if (!qualifiedContentList.isEmpty()) {
            JarMerger jarMerger = new JarMerger(new File(localJarsLocation, "otherclasses.jar"));
            jarMerger.setFilter(classOnlyFilter);
            for (QualifiedContent content : qualifiedContentList) {
                jarMerger.addFolder(content.getFile());
            }
            jarMerger.close();
        }
    }

    private void jarFolderToRootLocation(@NonNull File file, @NonNull final List<Pattern> excludes)
            throws IOException {
        JarMerger jarMerger = new JarMerger(mainClassLocation);
        jarMerger.setFilter(new IZipEntryFilter() {
            @Override
            public boolean checkEntry(String archivePath)
                    throws ZipAbortException {
                return LibraryJarTransform.checkEntry(excludes, archivePath);
            }
        });
        jarMerger.addFolder(file);
        jarMerger.close();
    }

    public static void copyJarWithContentFilter(
            @NonNull File from,
            @NonNull File to,
            @NonNull final List<Pattern> excludes) throws IOException {
        copyJarWithContentFilter(from, to, new IZipEntryFilter() {
            @Override
            public boolean checkEntry(String archivePath)
                    throws ZipAbortException {
                return LibraryJarTransform.checkEntry(excludes, archivePath);
            }
        });
    }

    public static void copyJarWithContentFilter(
            @NonNull File from,
            @NonNull File to,
            @Nullable IZipEntryFilter filter) throws IOException {
        Closer closer = Closer.create();
        byte[] buffer = new byte[4096];

        try {
            FileOutputStream fos = closer.register(new FileOutputStream(to));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ZipOutputStream zos = closer.register(new ZipOutputStream(bos));

            FileInputStream fis = closer.register(new FileInputStream(from));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ZipInputStream zis = closer.register(new ZipInputStream(bis));

            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (filter != null && !filter.checkEntry(name)) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                // add the entry to the jar archive
                zos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        } catch (IZipEntryFilter.ZipAbortException e) {
            throw new IOException(e);
        } finally {
            closer.close();
        }
    }

    private static boolean checkEntry(
            @NonNull List<Pattern> patterns,
            @NonNull String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convenient way to attach exclude list providers that can provide their list at the end of
     * the build.
     */
    public interface ExcludeListProvider {
        @Nullable List<String> getExcludeList();
    }
}
