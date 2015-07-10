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
import com.android.build.gradle.integration.common.utils.XmlHelper;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.SubjectFactory;

import org.junit.Assert;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Truth support for apk files.
 */
public class ApkSubject extends AbstractAndroidSubject<ApkSubject> {

    private static final Pattern PATTERN_CLASS_DESC = Pattern.compile(
            "^Class descriptor\\W*:\\W*'(L.+;)'$");

    private static final Pattern PATTERN_MAX_SDK_VERSION = Pattern.compile(
            "^maxSdkVersion\\W*:\\W*'(.+)'$");

    static class Factory extends SubjectFactory<ApkSubject, File> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public ApkSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull File subject) {
            return new ApkSubject(failureStrategy, subject);
        }
    }

    /**
     * XMLDump of the main dex file via dexdump
     */
    private Node mainDexDump;

    public ApkSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull File subject) {
        super(failureStrategy, subject);
    }

    public Node getClassDexDump(@NonNull String className)
            throws SAXException, ParserConfigurationException, ProcessException, IOException {
        if (!className.startsWith("L") || !className.endsWith(";")) {
            throw new RuntimeException("class name must be in the format Lcom/foo/Main;");
        }
        className = className.substring(1, className.length() - 1).replace('/', '.');
        final int lastDot = className.lastIndexOf('.');
        final String pkg;
        final String name;
        if (lastDot < 0) {
            name = className;
            pkg = "";
        } else {
            pkg = className.substring(0, lastDot);
            name = className.substring(lastDot + 1);
        }
        Node mainDexDump = getMainDexDump();
        Node packageNode = XmlHelper
                .findChildWithTagAndAttrs(mainDexDump, "package", "name", pkg);
        if (packageNode == null) {
            fail("%s does not contain package %s", getSubject(), pkg);
        }
        Node classNode = XmlHelper.findChildWithTagAndAttrs(packageNode, "class", "name", name);
        if (classNode == null) {
            fail("%s does not cointain class %s", getSubject(), className);
        }
        return classNode;
    }

    private Node getMainDexDump()
            throws SAXException, ParserConfigurationException, ProcessException, IOException {
        if (mainDexDump != null) {
            return mainDexDump;
        }

        File apkFile = getSubject();

        // get the dexdump exec
        File dexDumpExe = SdkHelper.getDexDump();
        mainDexDump = loadDexDump(apkFile, dexDumpExe);
        return mainDexDump;
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

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasPackageName(@NonNull String packageName) throws ProcessException {
        File apk = getSubject();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualPackageName = apkInfo.getPackageName();

        if (!actualPackageName.equals(packageName)) {
            failWithBadResults("has packageName", packageName, "is", actualPackageName);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
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

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
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

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasMaxSdkVersion(int maxSdkVersion) throws ProcessException {

        List<String> output = ApkHelper.getApkBadging(getSubject());

        checkMaxSdkVersion(output, maxSdkVersion);
    }

    @Override
    protected String getDisplaySubject() {
        String name = (internalCustomName() == null) ? "" : "\"" + internalCustomName() + "\" ";
        return name + "<" + getSubject().getName() + ">";
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     * @param scope the scope in which to search for the class.
     */
    @Override
    protected boolean checkForClass(
            @NonNull String expectedClassName,
            @NonNull ClassFileScope scope)
            throws ProcessException, IOException {
        if (!expectedClassName.startsWith("L") || !expectedClassName.endsWith(";")) {
            throw new RuntimeException("class name must be in the format Lcom/foo/Main;");
        }

        File apkFile = getSubject();

        // get the dexdump exec
        File dexDumpExe = SdkHelper.getDexDump();

        switch (scope) {
            case MAIN:
                return checkFileForClassWithDexDump(expectedClassName, apkFile, dexDumpExe);
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
                ZipFile zipFile = new ZipFile(getSubject());
                try {
                    InputStream classDexStream;
                    int index = 2;

                    while ((classDexStream = getLenientInputStream(zipFile,
                            String.format(FN_APK_CLASSES_N_DEX, index))) != null) {

                        byte[] content = ByteStreams.toByteArray(classDexStream);
                        // write into tmp file
                        File dexFile = File.createTempFile("dex", "");
                        dexFile.deleteOnExit();
                        Files.write(content, dexFile);

                        // run dexDump on it
                        if (checkFileForClassWithDexDump(expectedClassName, dexFile, dexDumpExe)) {
                            return true;
                        }

                        // not found? switch to next index.
                        index++;
                    }
                } finally {
                    zipFile.close();
                }
                break;
        }

        return false;
    }

    @Override
    protected boolean checkForJavaResource(@NonNull String resourcePath)
            throws ProcessException, IOException {
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            return zipFile.getEntry(resourcePath) != null;
        } finally {
            zipFile.close();
        }
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @Override
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull String content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified
     * byte array content.
     */
    @Override
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException, ProcessException {
        containsFileWithContent(path, content);
    }

    /**
     * Exports the dex information in XML format and returns it as a Document.
     */
    private static Node loadDexDump(@NonNull File file, @NonNull File dexDumpExe)
            throws IOException, SAXException, ParserConfigurationException, ProcessException {
        ProcessExecutor executor = new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(dexDumpExe);
        builder.addArgs("-l", "xml", "-d", file.getAbsolutePath());

        String output = ApkHelper.runAndGetRawOutput(builder.createProcess(), executor);
        return XmlUtils.parseDocument(output, false).getChildNodes().item(0);
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
            @NonNull String expectedClassName,
            @NonNull File file,
            @NonNull File dexDumpExe) throws ProcessException {
        ProcessExecutor executor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(dexDumpExe);
        builder.addArgs(file.getAbsolutePath());

        List<String> output = ApkHelper.runAndGetOutput(builder.createProcess(), executor);

        for (String line : output) {
            Matcher m = PATTERN_CLASS_DESC.matcher(line.trim());
            if (m.matches()) {
                String className = m.group(1);
                if (expectedClassName.equals(className)) {
                    return true;
                }
            }
        }
        return false;
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

    @Nullable
    private static InputStream getLenientInputStream(
            @NonNull ZipFile zipFile, @NonNull String path) throws IOException {
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null) {
            return null;
        }

        if (entry.isDirectory()) {
            return null;
        }

        return zipFile.getInputStream(entry);
    }
}
