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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.builder.model.SyncIssue;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for computing class paths to use for compilation.
 */
public class BootClasspathBuilder {

    @NonNull
    public static ImmutableList<File> computeFullBootClasspath(
            @NonNull IAndroidTarget target,
            @NonNull File annotationsJar) {
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(annotationsJar);

        ImmutableList.Builder<File> classpath = ImmutableList.builder();

        for (String p : target.getBootClasspath()) {
            classpath.add(new File(p));
        }

        // add additional libraries if any
        List<IAndroidTarget.OptionalLibrary> libs = target.getAdditionalLibraries();
        for (IAndroidTarget.OptionalLibrary lib : libs) {
            File jar = lib.getJar();
            Verify.verify(jar != null, "Jar missing from additional library %s.", lib.getName());
            classpath.add(jar);
        }

        // add optional libraries if any
        List<IAndroidTarget.OptionalLibrary> optionalLibraries = target.getOptionalLibraries();
        for (IAndroidTarget.OptionalLibrary lib : optionalLibraries) {
            File jar = lib.getJar();
            Verify.verify(jar != null, "Jar missing from optional library %s.", lib.getName());
            classpath.add(jar);
        }

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(annotationsJar);
        }

        return classpath.build();
    }

    @NonNull
    public static ImmutableList<File> computeFilteredClasspath(
            @NonNull IAndroidTarget target,
            @NonNull List<LibraryRequest> libraryRequestsArg,
            @NonNull ErrorReporter errorReporter,
            @NonNull File annotationsJar) {
        List<File> classpath = Lists.newArrayList();

        for (String p : target.getBootClasspath()) {
            classpath.add(new File(p));
        }

        List<LibraryRequest> requestedLibs = Lists.newArrayList(libraryRequestsArg);

        // add additional libraries if any
        List<IAndroidTarget.OptionalLibrary> libs = target.getAdditionalLibraries();
        for (IAndroidTarget.OptionalLibrary lib : libs) {
            // add it always for now
            classpath.add(lib.getJar());

            // remove from list of requested if match
            Optional<LibraryRequest> requestedLib = findMatchingLib(lib.getName(), requestedLibs);
            if (requestedLib.isPresent()) {
                requestedLibs.remove(requestedLib.get());
            }
        }

        // add optional libraries if needed.
        List<IAndroidTarget.OptionalLibrary> optionalLibraries = target.getOptionalLibraries();
        for (IAndroidTarget.OptionalLibrary lib : optionalLibraries) {
            // search if requested
            Optional<LibraryRequest> requestedLib = findMatchingLib(lib.getName(), requestedLibs);
            if (requestedLib.isPresent()) {
                // add to classpath
                classpath.add(lib.getJar());

                // remove from requested list.
                requestedLibs.remove(requestedLib.get());
            }
        }

        // look for not found requested libraries.
        for (LibraryRequest library : requestedLibs) {
            errorReporter.handleSyncError(
                    library.getName(),
                    SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND,
                    "Unable to find optional library: " + library.getName());
        }

        // add annotations.jar if needed.
        if (target.getVersion().getApiLevel() <= 15) {
            classpath.add(annotationsJar);
        }

        return ImmutableList.copyOf(classpath);
    }

    @NonNull
    private static Optional<LibraryRequest> findMatchingLib(
            @NonNull String name,
            @NonNull List<LibraryRequest> libraries) {
        return libraries.stream().filter(l -> name.equals(l.getName())).findFirst();
    }
}
