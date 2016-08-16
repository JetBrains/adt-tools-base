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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.LoggingManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import proguard.ParseException;

/**
 * Transform for multi-dex.
 *
 * This does not actually consume anything, rather it only reads streams and extract information
 * from them.
 */
public class MultiDexTransform extends BaseProguardAction {

    // Inputs
    @NonNull
    private final File manifestKeepListProguardFile;
    @Nullable
    private final File userMainDexKeepProguard;
    @Nullable
    private final File userMainDexKeepFile;
    @NonNull
    private final VariantScope variantScope;
    @Nullable
    private final File includeInMainDexJarFile;

    private final boolean keepRuntimeAnnotatedClasses;

    // Outputs
    @NonNull
    private final File configFileOut;
    @NonNull
    private final File mainDexListFile;

    public MultiDexTransform(
            @NonNull VariantScope variantScope,
            @NonNull DexOptions dexOptions,
            @Nullable File includeInMainDexJarFile) {
        super(variantScope);
        this.manifestKeepListProguardFile = variantScope.getManifestKeepListProguardFile();
        this.userMainDexKeepProguard = variantScope.getVariantConfiguration().getMultiDexKeepProguard();
        this.userMainDexKeepFile = variantScope.getVariantConfiguration().getMultiDexKeepFile();
        this.variantScope = variantScope;
        this.includeInMainDexJarFile = includeInMainDexJarFile;
        configFileOut = new File(variantScope.getGlobalScope().getBuildDir() + "/" + FD_INTERMEDIATES
                + "/multi-dex/" + variantScope.getVariantConfiguration().getDirName()
                + "/components.flags");
        mainDexListFile = variantScope.getMainDexListFile();
        keepRuntimeAnnotatedClasses = dexOptions.getKeepRuntimeAnnotatedClasses();
    }

    @NonNull
    @Override
    public String getName() {
        return "multidexlist";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return Arrays.asList(
                        manifestKeepListProguardFile,
                        userMainDexKeepFile,
                        userMainDexKeepProguard,
                        includeInMainDexJarFile)
                .stream()
                .filter(file -> file != null)
                .map(SecondaryFile::nonIncremental)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> params = ImmutableMap.builder();
        params.put("keepRuntimeAnnotatedClasses", keepRuntimeAnnotatedClasses);
        TargetInfo targetInfo = variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo();
        if (targetInfo != null) {
            params.put("build_tools", targetInfo.getBuildTools().getRevision().toString());
        }
        return params.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return Lists.newArrayList(mainDexListFile, configFileOut);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        // Re-direct the output to appropriate log levels, just like the official ProGuard task.
        LoggingManager loggingManager = invocation.getContext().getLogging();
        loggingManager.captureStandardOutput(LogLevel.INFO);
        loggingManager.captureStandardError(LogLevel.WARN);

        try {
            File input = verifyInputs(invocation.getReferencedInputs());
            shrinkWithProguard(input);
            computeList(input);
        } catch (ParseException | ProcessException e) {
            throw new TransformException(e);
        }
    }

    private static File verifyInputs(@NonNull Collection<TransformInput> inputs) {
        // Collect the inputs. There should be only one.
        List<File> inputFiles = Lists.newArrayList();

        for (TransformInput transformInput : inputs) {
            for (JarInput jarInput : transformInput.getJarInputs()) {
                inputFiles.add(jarInput.getFile());
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                inputFiles.add(directoryInput.getFile());
            }
        }

        return Iterables.getOnlyElement(inputFiles);
    }

    private void shrinkWithProguard(@NonNull File input) throws IOException, ParseException {
        dontobfuscate();
        dontoptimize();
        dontpreverify();
        dontwarn();
        dontnote();
        forceprocessing();

        applyConfigurationFile(manifestKeepListProguardFile);
        if (userMainDexKeepProguard != null) {
            applyConfigurationFile(userMainDexKeepProguard);
        }

        // add a couple of rules that cannot be easily parsed from the manifest.
        keep("public class * extends android.app.Instrumentation { <init>(); }");
        keep("public class * extends android.app.Application { "
                + "  <init>(); "
                + "  void attachBaseContext(android.content.Context);"
                + "}");
        keep("public class * extends android.app.backup.BackupAgent { <init>(); }");
        keep("public class * extends java.lang.annotation.Annotation { *;}");
        keep("class com.android.tools.fd.** {*;}"); // Instant run.

        // handle inputs
        libraryJar(findShrinkedAndroidJar());
        inJar(input);

        // outputs.
        outJar(variantScope.getProguardComponentsJarFile());
        printconfiguration(configFileOut);

        // run proguard
        runProguard();
    }

    @NonNull
    private File findShrinkedAndroidJar() {
        Preconditions.checkNotNull(
                variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo());
        File shrinkedAndroid = new File(
                variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo()
                        .getBuildTools()
                        .getLocation(),
                "lib" + File.separatorChar + "shrinkedAndroid.jar");

        if (!shrinkedAndroid.isFile()) {
            shrinkedAndroid = new File(
                    variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo()
                            .getBuildTools().getLocation(),
                    "multidex" + File.separatorChar + "shrinkedAndroid.jar");
        }
        return shrinkedAndroid;
    }

    private void computeList(File _allClassesJarFile) throws ProcessException, IOException {
        // manifest components plus immediate dependencies must be in the main dex.
        Set<String> mainDexClasses = callDx(
                _allClassesJarFile,
                variantScope.getProguardComponentsJarFile());

        // add additional classes specified via a jar file.
        if (includeInMainDexJarFile != null) {
            // proguard shrinking is overly aggressive when it comes to removing
            // interface classes: even if an interface is implemented by a concrete
            // class, if no code actually references the interface class directly
            // (i.e., code always references the concrete class), proguard will
            // remove the interface class when shrinking.  This is problematic,
            // as the runtime verifier still needs the interface class to be
            // present, or the concrete class won't be valid.  Use a
            // ClassReferenceListBuilder here (only) to pull in these missing
            // interface classes.  Note that doing so brings in other unnecessary
            // stuff, too; next time we're low on main dex space, revisit this!
            mainDexClasses.addAll(callDx(_allClassesJarFile, includeInMainDexJarFile));
        }

        if (userMainDexKeepFile != null) {
            mainDexClasses = ImmutableSet.<String>builder()
                    .addAll(mainDexClasses)
                    .addAll(Files.readLines(userMainDexKeepFile, Charsets.UTF_8))
                    .build();
        }

        String fileContent = Joiner.on(System.getProperty("line.separator")).join(mainDexClasses);

        Files.write(fileContent, mainDexListFile, Charsets.UTF_8);

    }

    private Set<String> callDx(File allClassesJarFile, File jarOfRoots) throws ProcessException {
        EnumSet<AndroidBuilder.MainDexListOption> mainDexListOptions =
                EnumSet.noneOf(AndroidBuilder.MainDexListOption.class);
        if (!keepRuntimeAnnotatedClasses) {
            mainDexListOptions.add(
                    AndroidBuilder.MainDexListOption.DISABLE_ANNOTATION_RESOLUTION_WORKAROUND);
            Logging.getLogger(MultiDexTransform.class).warn(
                    "Not including classes with runtime retention annotations in the main dex.\n"
                            + "This can cause issues with reflection in older platforms.");
        }


        return variantScope.getGlobalScope().getAndroidBuilder().createMainDexList(
                allClassesJarFile, jarOfRoots, mainDexListOptions);
    }
}
