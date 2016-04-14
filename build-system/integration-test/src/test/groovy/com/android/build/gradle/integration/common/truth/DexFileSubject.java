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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.XmlHelper;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexFileSubject extends Subject<DexFileSubject, File> {

    private Node mainDexDump;

    public static final SubjectFactory<DexFileSubject, File> FACTORY =
            new SubjectFactory<DexFileSubject, File>() {
                @Override
                public DexFileSubject getSubject(@NonNull FailureStrategy fs, @Nullable File that) {
                    return new DexFileSubject(fs, that);
                }
            };

    private DexFileSubject(@NonNull FailureStrategy fs, @Nullable File that) {
        super(fs, that);
    }

    /**
     * @deprecated TODO: Separate out utils for dealing with dex files from truth subjects.
     */
    @Deprecated
    public boolean containsClass(String className) throws IOException, ProcessException {
        return getClassDexDump(className) != null;
    }

    public IndirectSubject<DexClassSubject> hasClass(@NonNull String className)
            throws ProcessException, IOException {
        final Node classNode = getClassDexDump(className);
        if (assertSubjectIsNonNull() && classNode == null) {
            fail("contains class", getSubject(), className);
        }
        return new IndirectSubject<DexClassSubject>() {
            @NonNull
            @Override
            public DexClassSubject that() {
                return DexClassSubject.FACTORY.getSubject(failureStrategy, classNode);
            }
        };
    }

    @Nullable
    private Node getClassDexDump(@NonNull String className) throws ProcessException, IOException {
        if (!className.startsWith("L") || !className.endsWith(";")) {
            throw new IllegalArgumentException(
                    "class name must be in the format L" + "com/foo/Main;");
        }
        if (getSubject() == null) {
            return null;
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
            name = className.substring(lastDot + 1).replace('$', '.');
        }
        Node mainDexDump = getMainDexDump();
        Node packageNode = XmlHelper
                .findChildWithTagAndAttrs(mainDexDump, "package", "name", pkg);
        if (packageNode == null) {
            return null;
        }

        return XmlHelper.findChildWithTagAndAttrs(packageNode, "class", "name", name);
    }

    /**
     * Should not be called when the subject is null.
     */
    @NonNull
    private Node getMainDexDump() throws ProcessException, IOException {
        if (mainDexDump != null) {
            return mainDexDump;
        }
        mainDexDump = loadDexDump(Preconditions.checkNotNull(getSubject()), SdkHelper.getDexDump());
        return mainDexDump;
    }

    /**
     * Exports the dex information in XML format and returns it as a Document.
     */
    @NonNull
    private static Node loadDexDump(@NonNull File file, @NonNull File dexDumpExe)
            throws IOException, ProcessException {
        ProcessExecutor executor = new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(dexDumpExe);
        builder.addArgs("-l", "xml", "-d", file.getAbsolutePath());

        String output = ApkHelper.runAndGetRawOutput(builder.createProcess(), executor);
        try {
            return XmlUtils.parseDocument(output, false).getChildNodes().item(0);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        }
    }

    private boolean assertSubjectIsNonNull() {
        if (getSubject() == null) {
            fail("Cannot assert about the contents of a dex file that does not exist.");
            return false;
        }
        return true;
    }
}
