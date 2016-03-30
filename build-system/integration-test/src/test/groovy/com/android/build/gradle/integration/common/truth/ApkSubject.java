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

package com.android.build.gradle.integration.common.truth;

import static com.android.SdkConstants.FN_APK_CLASSES_N_DEX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.LineProcessor;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.SubjectFactory;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Truth support for apk files.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class ApkSubject extends AbstractAndroidSubject<ApkSubject> {

    private static final Pattern PATTERN_CLASS_DESC = Pattern.compile(
            "^Class descriptor\\W*:\\W*'(L.+;)'$");

    private static final Pattern PATTERN_MAX_SDK_VERSION = Pattern.compile(
            "^maxSdkVersion\\W*:\\W*'(.+)'$");

    public static final SubjectFactory<ApkSubject, File> FACTORY =
            new SubjectFactory<ApkSubject, File> () {
                @Override
                public ApkSubject getSubject(
                        @NonNull FailureStrategy failureStrategy,
                        @NonNull File subject) {
                    return new ApkSubject(failureStrategy, subject);
                }
            };


    public ApkSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull File subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public List<String> entries() throws IOException {
        ImmutableList.Builder<String> entryList = ImmutableList.builder();
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                entryList.add(entries.nextElement().getName());
            }
        }
        return entryList.build();
    }

    @NonNull
    public IndirectSubject<DexFileSubject> hasMainDexFile() throws IOException {
        contains("classes.dex");
        return new IndirectSubject<DexFileSubject>() {
            @Override
            @NonNull
            public DexFileSubject that() {
                return DexFileSubject.FACTORY.getSubject(failureStrategy, getSubject());
            }
        };
    }

    @NonNull
    public IterableSubject<? extends IterableSubject<?, String, List<String>>, String, List<String>> locales() throws ProcessException {
        File apk = getSubject();
        List<String> locales = ApkHelper.getLocales(apk);

        if (locales == null) {
            Assert.fail(String.format("locales not found in badging output for %s", apk));
        }

        return check().that(locales);
    }

    public void hasPackageName(@NonNull String packageName) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualPackageName = apkInfo.getPackageName();

        if (!actualPackageName.equals(packageName)) {
            failWithBadResults("has packageName", packageName, "is", actualPackageName);
        }
    }

    public void hasVersionCode(int versionCode) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        Integer actualVersionCode = apkInfo.getVersionCode();
        if (actualVersionCode == null) {
            failWithRawMessage("Unable to query %s for versionCode", getDisplaySubject());
        }

        if (!apkInfo.getVersionCode().equals(versionCode)) {
            failWithBadResults("has versionCode", versionCode, "is", actualVersionCode);
        }
    }

    public void hasVersionName(@NonNull String versionName) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualVersionName = apkInfo.getVersionName();
        if (actualVersionName == null) {
            failWithRawMessage("Unable to query %s for versionName", getDisplaySubject());
        }

        if (!apkInfo.getVersionName().equals(versionName)) {
            failWithBadResults("has versionName", versionName, "is", actualVersionName);
        }
    }

    public void hasMaxSdkVersion(int maxSdkVersion) throws ProcessException {

        List<String> output = ApkHelper.getApkBadging(getSubject());

        checkMaxSdkVersion(output, maxSdkVersion);
    }

    @NonNull
    public IndirectSubject<DexClassSubject> hasClass(
            @NonNull final String expectedClassName,
            @NonNull final ClassFileScope scope) throws ProcessException, IOException {
        if (!expectedClassName.startsWith("L") || !expectedClassName.endsWith(";")) {
            throw new RuntimeException("class name must be in the format Lcom/foo/Main;");
        }
        IndirectSubject<DexClassSubject> classSubject;
        switch (scope) {
            case MAIN:
                classSubject =
                        extractEntryAndRunAction("classes.dex", findClassAction(expectedClassName));
                if (classSubject != null) {
                    return classSubject;
                }
                break;
            case INSTANT_RUN:
                // check first in the instant-run.zip file.
                classSubject = extractEntryAndRunAction("instant-run.zip",
                        allEntriesAction(findClassAction(expectedClassName)));
                if (classSubject != null) {
                    return classSubject;
                }
                break;
            case ALL:
                classSubject = extractEntryAndRunAction(
                        "classes.dex", findClassAction(expectedClassName));
                if (classSubject != null) {
                    return classSubject;
                }
                // intended fall-through
            case SECONDARY:
                // while dexdump supports receiving directly an apk, this doesn't work for
                // multi-dex.
                // We're going to extract all the classes<N>.dex we find until one of them
                // contains the class we're searching for.
                try (ZipFile zipFile = new ZipFile(getSubject())) {
                    int index = 2;
                    String dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    while (zipFile.getEntry(dexFileName) != null) {
                        classSubject = extractEntryAndRunAction(dexFileName,
                                findClassAction(expectedClassName));
                        if (classSubject != null) {
                            return classSubject;
                        }

                        // not found? switch to next index.
                        index++;
                        dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    }
                }
                break;
        }
        fail("contains class", expectedClassName);
        return new IndirectSubject<DexClassSubject>() {
            @NonNull
            @Override
            public DexClassSubject that() {
                return DexClassSubject.FACTORY.getSubject(failureStrategy, null);
            }
        };
    }

    /**
     * Creates an {@link ZipEntryAction} that will consider each extracted entry as a zip file,
     * will enumerate such zip file entries and call an delegated action on each entry.
     */
    @Nullable
    protected <T> ZipEntryAction<T> allEntriesAction(final ZipEntryAction<T> action) {
        return new ZipEntryAction<T>() {
            @Nullable
            @Override
            public T doOnZipEntry(File extractedEntry) throws ProcessException {

                ZipFileSubject instantRunZip =
                        new ZipFileSubject(failureStrategy, extractedEntry);

                try {
                    try (ZipFile zipFile = new ZipFile(extractedEntry)) {
                        Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
                        while (zipFileEntries.hasMoreElements()) {
                            ZipEntry zipEntry = zipFileEntries.nextElement();
                            T result = instantRunZip.extractEntryAndRunAction(
                                    zipEntry.getName(), action);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new ProcessException(e);
                }
                return null;
            }
        };
    }

    private ZipEntryAction<IndirectSubject<DexClassSubject>> findClassAction(
            final String expectedClassName) {

        return new ZipEntryAction<IndirectSubject<DexClassSubject>>() {
            @Nullable
            @Override
            public IndirectSubject<DexClassSubject> doOnZipEntry(File extractedEntry)
                    throws ProcessException {

                if (!checkFileForClassWithDexDump(
                        expectedClassName, extractedEntry, SdkHelper.getDexDump())) {
                    return null;
                }
                IndirectSubject<DexFileSubject> dexFile = getDexFile(extractedEntry);
                try {
                    return dexFile.that().hasClass(expectedClassName);
                } catch (IOException e) {
                    throw new ProcessException(e);
                }
            }
        };
    }

    private static ZipEntryAction<Boolean> hasClassAction(final String expectedClassName) {
        return new ZipEntryAction<Boolean>() {
            @Nullable
            @Override
            public Boolean doOnZipEntry(File extractedEntry) throws ProcessException {
                return checkFileForClassWithDexDump(
                        expectedClassName, extractedEntry, SdkHelper.getDexDump())
                        ? Boolean.TRUE : null;

            }
        };
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     * @param scope the scope in which to search for the class.
     */
    @Override
    protected boolean checkForClass(
            @NonNull final String expectedClassName,
            @NonNull final ClassFileScope scope)
            throws ProcessException, IOException {
        if (!expectedClassName.startsWith("L") || !expectedClassName.endsWith(";")) {
            throw new RuntimeException("class name must be in the format Lcom/foo/Main;");
        }

        File apkFile = getSubject();

        // get the dexdump exec
        final File dexDumpExe = SdkHelper.getDexDump();

        switch (scope) {
            case MAIN:
                return checkFileForClassWithDexDump(expectedClassName, apkFile, dexDumpExe);
            case INSTANT_RUN:
                // check first in the instant-run.zip file.
                Boolean result = extractEntryAndRunAction("instant-run.zip",
                        allEntriesAction(hasClassAction(expectedClassName)));
                if (result != null && result) {
                    return true;
                }
                break;
            case ALL:
                if (checkFileForClassWithDexDump(expectedClassName, apkFile, dexDumpExe)) {
                    return true;
                }
                // intended fall-through
            case SECONDARY:
                // while dexdump supports receiving directly an apk, this doesn't work for
                // multi-dex.
                // We're going to extract all the classes<N>.dex we find until one of them
                // contains the class we're searching for.
                try (ZipFile zipFile = new ZipFile(getSubject())) {
                    int index = 2;
                    String dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    while (zipFile.getEntry(dexFileName) != null) {
                        result = extractEntryAndRunAction(dexFileName,
                                hasClassAction(expectedClassName));
                        if (result != null && result) {
                            return true;
                        }
                        // not found? switch to next index.
                        index++;
                        dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    }
                }
                break;
        }

        return false;
    }

    @Override
    protected boolean checkForJavaResource(@NonNull String resourcePath)
            throws ProcessException, IOException {
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            return zipFile.getEntry(resourcePath) != null;
        }
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull String content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified
     * byte array content.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    /**
     * Run dex dump on a file (apk or dex file) to check for the presence of a given class.
     * @param expectedClassName the name of the class to search for
     * @param file the file to search
     * @param dexDumpExe the dex dump exe
     * @return true if the class was found
     * @throws ProcessException
     */
    private static boolean checkFileForClassWithDexDump(
            final @NonNull String expectedClassName,
            @NonNull File file,
            @NonNull File dexDumpExe) throws ProcessException {
        ProcessExecutor executor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(dexDumpExe);
        builder.addArgs(file.getAbsolutePath());

        LineProcessor<Boolean> classDescriptor = new LineProcessor<Boolean>() {
            Boolean result = false;

            @Override
            public boolean processLine(String line) throws IOException {
                Matcher m = PATTERN_CLASS_DESC.matcher(line.trim());
                if (m.matches()) {
                    String className = m.group(1);
                    if (expectedClassName.equals(className)) {
                        result = true;
                        return false; // stop processing
                    }
                }
                return true; // continue processing
            }

            @Override
            public Boolean getResult() {
                return result;
            }
        };

        return ApkHelper.runAndProcessOutput(builder.createProcess(), executor, classDescriptor);
    }

    @NonNull
    private static ApkInfoParser.ApkInfo getApkInfo(@NonNull File apk) throws ProcessException {
        ProcessExecutor processExecutor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), processExecutor);
        return parser.parseApk(apk);
    }

    @VisibleForTesting
    void checkMaxSdkVersion(@NonNull List<String> output, int maxSdkVersion) {
        for (String line : output) {
            Matcher m = PATTERN_MAX_SDK_VERSION.matcher(line.trim());
            if (m.matches()) {
                String actual = m.group(1);
                try {
                    Integer i = Integer.parseInt(actual);
                    if (!i.equals(maxSdkVersion)) {
                        failWithBadResults("has maxSdkVersion", maxSdkVersion, "is", i);
                    }
                    return;
                } catch (NumberFormatException e) {
                    failureStrategy.fail(
                            String.format(
                                    "maxSdkVersion in badging for %s is not a number: %s",
                                    getDisplaySubject(), actual),
                            e);
                }
            }
        }

        failWithRawMessage("maxSdkVersion not found in badging output for %s", getDisplaySubject());
    }
}
