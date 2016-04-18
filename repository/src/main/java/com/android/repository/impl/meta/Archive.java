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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SchemaModule;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A downloadable version of a {@link RepoPackage}, corresponding to a specific version number, and
 * optionally host OS, host bitness, JVM version or JVM bitness. Includes a complete version of the
 * package ({@link Archive.CompleteType}), and optionally binary patches from previous revisions to
 * this one ({@link Archive.PatchType}).
 *
 * Primarily a superclass for xjc-generated JAXB-compatible classes.
 */
@XmlTransient
public abstract class Archive {

    /**
     * Environment variable used to override the detected OS.
     */
    public static final String OS_OVERRIDE_ENV_VAR = "REPO_OS_OVERRIDE";

    /**
     * The detected bit size of the JVM.
     */
    private static int sJvmBits = 0;

    /**
     * The detected bit size of the host.
     */
    private static int sHostBits = 0;

    /**
     * The detected OS.
     */
    private static String sOs = null;

    /**
     * The detected JVM version.
     */
    private static Revision sJvmVersion = null;

    /**
     * @return {@code true} if this archive is compatible with the current system with respect to
     * the specified host os, bit size, jvm version, and jvm bit size (if any).
     */
    public boolean isCompatible() {
        if (getHostOs() != null) {
            if (sOs == null) {
                String os = System.getenv(OS_OVERRIDE_ENV_VAR);
                if (os == null) {
                    os = System.getProperty("os.name");
                }
                if (os.startsWith("Mac")) {
                    os = "macosx";
                } else if (os.startsWith("Windows")) {
                    os = "windows";
                } else if (os.startsWith("Linux")) {
                    os = "linux";
                }
                sOs = os;
            }
            if (!getHostOs().equals(sOs)) {
                return false;
            }
        }

        if (getJvmBits() != null || getHostBits() != null) {
            if (sJvmBits == 0) {
                int jvmBits = 0;
                String arch = System.getProperty("os.arch");

                if (arch.equalsIgnoreCase("x86_64") ||
                        arch.equalsIgnoreCase("ia64") ||
                        arch.equalsIgnoreCase("amd64")) {
                    jvmBits = 64;
                } else {
                    jvmBits = 32;
                }
                sJvmBits = jvmBits;
            }

            if (getJvmBits() != null && getJvmBits() != sJvmBits) {
                return false;
            }

            if (sHostBits == 0) {
                // TODO figure out the host bit size.
                // When jvmBits is 64 we know it's surely 64
                // but that's not necessarily obvious when jvmBits is 32.
                sHostBits = sJvmBits;
            }

            if (getHostBits() != null && getHostBits() != sHostBits) {
                return false;
            }
        }

        if (getMinJvmVersion() != null) {
            if (sJvmVersion == null) {
                Revision minJvmVersion = null;
                String javav = System.getProperty("java.version");              //$NON-NLS-1$
                // java Version is typically in the form "1.2.3_45" and we just need to keep up to
                // "1.2.3" since our revision numbers are in 3-parts form (1.2.3).
                Pattern p = Pattern.compile("((\\d+)(\\.\\d+)?(\\.\\d+)?).*");  //$NON-NLS-1$
                Matcher m = p.matcher(javav);
                if (m.matches()) {
                    minJvmVersion = Revision.parseRevision(m.group(1));
                }
                sJvmVersion = minJvmVersion;
            }
            if (getMinJvmVersion().toRevision().compareTo(sJvmVersion) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the full zip of this package.
     */
    @NonNull
    public abstract CompleteType getComplete();

    /**
     * Sets the full zip of this package.
     */
    public void setComplete(@NonNull CompleteType complete) {
        // Stub
    }

    /**
     * Gets the required host bit size (32 or 64), if any
     */
    @Nullable
    public Integer getHostBits() {
        // Stub
        return null;
    }

    /**
     * Sets the required host bit size (32 or 64), if any.
     */
    public void setHostBits(@Nullable Integer bits) {
        // Stub
    }

    /**
     * Gets the required JVM bit size (32 or 64), if any
     */
    @Nullable
    public Integer getJvmBits() {
        // Stub
        return null;
    }

    /**
     * Sets the required JVM bit size (32 or 64), if any.
     */
    public void setJvmBits(@Nullable Integer bits) {
        // Stub
    }

    /**
     * Gets the required host OS ("windows", "linux", "macosx"), if any.
     */
    @Nullable
    public String getHostOs() {
        // Stub
        return null;
    }

    /**
     * Sets the required host OS ("windows", "linux", "macosx"), if any.
     */
    public void setHostOs(@Nullable String os) {
        // Stub
    }

    /**
     * Gets all the version-to-version patches for this {@code Archive}.
     */
    @NonNull
    public List<PatchType> getAllPatches() {
        PatchesType patches = getPatches();
        if (patches == null) {
            return ImmutableList.of();
        }
        return patches.getPatch();
    }

    @Nullable
    public PatchType getPatch(Revision fromRevision) {
        for (PatchType p : getAllPatches()) {
            if (p.getBasedOn().toRevision().equals(fromRevision)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Gets the {@link PatchesType} for this Archive. Probably only needed internally.
     */
    @Nullable
    protected PatchesType getPatches() {
        // Stub
        return null;
    }

    /**
     * Sets the {@link PatchesType} for this Archive. Probably only needed internally.
     */
    protected void setPatches(@Nullable PatchesType patches) {
        // Stub
    }

    /**
     * Gets the minimum JVM version needed for this {@code Archive}, if any.
     */
    @Nullable
    public RevisionType getMinJvmVersion() {
        // Stub
        return null;
    }

    /**
     * Sets the minimum JVM version needed for this {@code Archive}, if any.
     */
    public void setMinJvmVersion(@Nullable RevisionType revision) {
        // Stub
    }

    /**
     * Create a {@link CommonFactory} corresponding to this instance's {@link
     * SchemaModule.SchemaModuleVersion}.
     */
    @NonNull
    public abstract CommonFactory createFactory();

    /**
     * General parent for the actual files referenced in an archive.
     */
    public abstract static class ArchiveFile {
        /**
         * Gets the checksum for the zip.
         */
        @NonNull
        public abstract String getChecksum();

        /**
         * Sets the checksum for this zip.
         */
        public void setChecksum(@NonNull String checksum) {
            // Stub
        }

        /**
         * Gets the URL to download from.
         */
        @NonNull
        public abstract String getUrl();

        /**
         * Sets the URL to download from.
         */
        public void setUrl(@NonNull String url) {
            // Stub
        }

        /**
         * Gets the size of the zip.
         */
        public abstract long getSize();

        /**
         * Sets the size of the zip;
         */
        public void setSize(long size) {
            // Stub
        }
    }

    /**
     * Parent for xjc-generated classes containing a complete zip of this archive.
     */
    @XmlTransient
    public abstract static class CompleteType extends ArchiveFile {

    }

    /**
     * A binary diff from a previous package version to this one. TODO: it's too bad that the code
     * from CompleteType must be duplicated here. Refactor.
     */
    @XmlTransient
    public abstract static class PatchType extends ArchiveFile {

        /**
         * Gets the source revision for this patch.
         */
        @NonNull
        public abstract RevisionType getBasedOn();

        /**
         * Sets the source revision for this patch.
         */
        protected void setBasedOn(@NonNull RevisionType revision) {
            // Stub
        }
    }

    /**
     * A list of {@link PatchType}s. Only used internally.
     */
    @XmlTransient
    public abstract static class PatchesType {
        @NonNull
        public List<PatchType> getPatch() {
            // Stub
            return ImmutableList.of();
        }
    }
}
