/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Map;

/**
 * Information on a specific build-tool folder.
 */
public class BuildToolInfo {

    public enum PathId {
        /** OS Path to the target's version of the aapt tool. */
        AAPT("1.0.0"),
        /** OS Path to the target's version of the aidl tool. */
        AIDL("1.0.0"),
        /** OS Path to the target's version of the dx tool. */
        DX("1.0.0"),
        /** OS Path to the target's version of the dx.jar file. */
        DX_JAR("1.0.0"),
        /** OS Path to the llvm-rs-cc binary for Renderscript. */
        LLVM_RS_CC("1.0.0"),
        /** OS Path to the Renderscript include folder. */
        ANDROID_RS("1.0.0"),
        /** OS Path to the Renderscript(clang) include folder. */
        ANDROID_RS_CLANG("1.0.0"),

        // --- NEW IN 18.1.0 ---

        /** OS Path to the bcc_compat tool. */
        BCC_COMPAT("18.1.0"),
        /** OS Path to the ARM linker. */
        LD_ARM("18.1.0"),
        /** OS Path to the X86 linker. */
        LD_X86("18.1.0"),
        /** OS Path to the MIPS linker. */
        LD_MIPS("18.1.0");

        /**
         * min revision this element was introduced.
         * Controls {@link BuildToolInfo#isValid(ILogger)}
         */
        private final FullRevision mMinRevision;

        /**
         * Creates the enum with a min revision in which this
         * tools appeared in the build tools.
         *
         * @param minRevision the min revision.
         */
        PathId(@NonNull String minRevision) {
            mMinRevision = FullRevision.parseRevision(minRevision);
        }

        /**
         * Returns whether the enum of present in a given rev of the build tools.
         *
         * @param fullRevision the build tools revision.
         * @return true if the tool is present.
         */
        boolean isPresentIn(@NonNull FullRevision fullRevision) {
            return fullRevision.compareTo(mMinRevision) >= 0;
        }
    }

    /** The build-tool revision. */
    private final FullRevision mRevision;
    /** The path to the build-tool folder specific to this revision. */
    private final File mPath;

    private final Map<PathId, String> mPaths = Maps.newEnumMap(PathId.class);

    public BuildToolInfo(@NonNull FullRevision revision, @NonNull File path) {
        mRevision = revision;
        mPath = path;

        add(PathId.AAPT, SdkConstants.FN_AAPT);
        add(PathId.AIDL, SdkConstants.FN_AIDL);
        add(PathId.DX, SdkConstants.FN_DX);
        add(PathId.DX_JAR, SdkConstants.FD_LIB + File.separator + SdkConstants.FN_DX_JAR);
        add(PathId.LLVM_RS_CC, SdkConstants.FN_RENDERSCRIPT);
        add(PathId.ANDROID_RS, SdkConstants.OS_FRAMEWORK_RS);
        add(PathId.ANDROID_RS_CLANG, SdkConstants.OS_FRAMEWORK_RS_CLANG);
        add(PathId.BCC_COMPAT, SdkConstants.FN_BCC_COMPAT);
        add(PathId.LD_ARM, SdkConstants.FN_LD_ARM);
        add(PathId.LD_X86, SdkConstants.FN_LD_X86);
        add(PathId.LD_MIPS, SdkConstants.FN_LD_MIPS);
    }

    public BuildToolInfo(FullRevision revision, @NonNull File mainPath,
            @NonNull File aapt,
            @NonNull File aidl,
            @NonNull File dx,
            @NonNull File dxJar,
            @NonNull File llmvRsCc,
            @NonNull File androidRs,
            @NonNull File androidRsClang,
            @Nullable File bccCompat,
            @Nullable File ldArm,
            @Nullable File ldX86,
            @Nullable File ldMips) {
        mRevision = revision;
        mPath = mainPath;
        add(PathId.AAPT, aapt);
        add(PathId.AIDL, aidl);
        add(PathId.DX, dx);
        add(PathId.DX_JAR, dxJar);
        add(PathId.LLVM_RS_CC, llmvRsCc);
        add(PathId.ANDROID_RS, androidRs);
        add(PathId.ANDROID_RS_CLANG, androidRsClang);

        if (bccCompat != null) {
            add(PathId.BCC_COMPAT, bccCompat);
        } else if (PathId.BCC_COMPAT.isPresentIn(revision)) {
            throw new IllegalArgumentException("BCC_COMPAT required in " + revision.toString());
        }
        if (ldArm != null) {
            add(PathId.LD_ARM, ldArm);
        } else if (PathId.LD_ARM.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_ARM required in " + revision.toString());
        }

        if (ldX86 != null) {
            add(PathId.LD_X86, ldX86);
        } else if (PathId.LD_X86.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_X86 required in " + revision.toString());
        }

        if (ldMips != null) {
            add(PathId.LD_MIPS, ldMips);
        } else if (PathId.LD_MIPS.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_MIPS required in " + revision.toString());
        }
    }

    private void add(PathId id, String leaf) {
        add(id, new File(mPath, leaf));
    }

    private void add(PathId id, File path) {
        String str = path.getAbsolutePath();
        if (path.isDirectory() && str.charAt(str.length() - 1) != File.separatorChar) {
            str += File.separatorChar;
        }
        mPaths.put(id, str);
    }

    /**
     * Returns the revision.
     */
    @NonNull
    public FullRevision getRevision() {
        return mRevision;
    }

    /**
     * Returns the build-tool revision-specific folder.
     * <p/>
     * For compatibility reasons, use {@link #getPath(PathId)} if you need the path to a
     * specific tool.
     */
    @NonNull
    public File getLocation() {
        return mPath;
    }

    /**
     * Returns the path of a build-tool component.
     *
     * @param pathId the id representing the path to return.
     * @return The absolute path for that tool, with a / separator if it's a folder.
     *         Null if the path-id is unknown.
     */
    public String getPath(PathId pathId) {
        assert pathId.isPresentIn(mRevision);

        return mPaths.get(pathId);
    }

    /**
     * Checks whether the build-tool is valid by verifying that the expected binaries
     * are actually present. This checks that all known paths point to a valid file
     * or directory.
     *
     * @param log An optional logger. If non-null, errors will be printed there.
     * @return True if the build-tool folder contains all the expected tools.
     */
    public boolean isValid(@Nullable ILogger log) {
        for (Map.Entry<PathId, String> entry : mPaths.entrySet()) {
            File f = new File(entry.getValue());
            // check if file is missing. It's only ok if the revision of the build-tools
            // is lower than the min rev of the element.
            if (!f.exists() && entry.getKey().isPresentIn(mRevision)) {
                if (log != null) {
                    log.warning("Build-tool %1$s is missing %2$s at %3$s",  //$NON-NLS-1$
                            mRevision.toString(),
                            entry.getKey(), f.getAbsolutePath());
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a debug representation suitable for unit-tests.
     * Note that unit-tests need to clean up the paths to avoid inconsistent results.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<BuildToolInfo rev=").append(mRevision);    //$NON-NLS-1$
        builder.append(", mPath=").append(mPath);                   //$NON-NLS-1$
        builder.append(", mPaths=").append(getPathString());        //$NON-NLS-1$
        builder.append(">");                                        //$NON-NLS-1$
        return builder.toString();
    }

    private String getPathString() {
        StringBuilder sb = new StringBuilder("{");

        for (Map.Entry<PathId, String> entry : mPaths.entrySet()) {
            if (entry.getKey().isPresentIn(mRevision)) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
        }

        sb.append('}');

        return sb.toString();
    }
}
