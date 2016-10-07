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

package com.android.builder.internal.packaging.sign;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.builder.internal.packaging.zip.ZFileExtension;
import com.android.builder.internal.packaging.zip.utils.CachedSupplier;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.android.builder.packaging.ManifestAttributes;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Extension to {@link ZFile} that will generate a manifest. The extension will register
 * automatically with the {@link ZFile}.
 *
 * <p>Creating this extension will ensure a manifest for the zip exists.
 * This extension will generate a manifest if one does not exist and will update an existing
 * manifest, if one does exist. The extension will also provide access to the manifest so that
 * others may update the manifest.
 *
 * <p>Apart from standard manifest elements, this extension does not handle any particular manifest
 * features such as signing or adding custom attributes. It simply generates a plain manifest and
 * provides infrastructure so that other extensions can add data in the manifest.
 *
 * <p>The manifest itself will only be written when the {@link ZFileExtension#beforeUpdate()}
 * notification is received, meaning all manifest manipulation is done in-memory.
 */
public class ManifestGenerationExtension {

    /**
     * Name of META-INF directory.
     */
    public static final String META_INF_DIR = "META-INF";

    /**
     * Name of the manifest file.
     */
    public static final String MANIFEST_NAME = META_INF_DIR + "/MANIFEST.MF";

    /**
     * Who should be reported as the manifest builder.
     */
    @NonNull
    private final String mBuiltBy;

    /**
     * Who should be reported as the manifest creator.
     */
    @NonNull
    private final String mCreatedBy;

    /**
     * The file this extension is attached to. {@code null} if not yet registered.
     */
    @Nullable
    private ZFile mZFile;

    /**
     * The zip file's manifest.
     */
    @NonNull
    private final Manifest mManifest;

    /**
     * Byte representation of the manifest. This is important because there is no guarantee that
     * two writes of the manifest will yield the same byte array (there is no guaranteed order
     * of entries in the manifest).
     *
     * <p>However, we want to ensure that two writes of the manifest will, if the manifest is not
     * changed in-between, generate the exact same byte representation. Otherwise it would be
     * difficult to build a signing extension.
     */
    @NonNull
    private CachedSupplier<byte[]> mManifestBytes;

    /**
     * Has the current manifest been changed and not yet flushed? If {@link #mDirty} is
     * {@code true}, then {@link #mManifestBytes} should not be valid. This means that
     * marking the manifest as dirty should also invalidate {@link #mManifestBytes}. To avoid
     * breaking the invariant, instead of setting {@link #mDirty}, {@link #markDirty()} should
     * be called.
     */
    private boolean mDirty;

    /**
     * The extension to register with the {@link ZFile}. {@code null} if not registered.
     */
    @Nullable
    private ZFileExtension mExtension;

    /**
     * Creates a new extension. This will not register the extension with the provided
     * {@link ZFile}. Until {@link #register(ZFile)} is invoked, this extension is not used.
     *
     * @param builtBy who built the manifest?
     * @param createdBy who created the manifest?
     */
    public ManifestGenerationExtension(@NonNull String builtBy, @NonNull String createdBy) {
        mBuiltBy = builtBy;
        mCreatedBy = createdBy;
        mManifest = new Manifest();
        mDirty = false;
        mManifestBytes = new CachedSupplier<byte[]>() {
            @Override
            protected byte[] compute() throws IOException {
                ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
                mManifest.write(outBytes);
                return outBytes.toByteArray();
            }
        };
    }

    /**
     * Marks the manifest as being dirty, <i>i.e.</i>, its data has changed since it was last
     * read and/or written.
     */
    private void markDirty() {
        mDirty = true;
        mManifestBytes.reset();
    }

    /**
     * Registers the extension with the {@link ZFile} provided in the constructor.
     *
     * @param zFile the zip file to add the extension to
     * @throws IOException failed to analyze the zip
     */
    public void register(@NonNull ZFile zFile) throws IOException {
        Preconditions.checkState(mExtension == null, "register() has already been invoked.");
        mZFile = zFile;

        rebuildManifest();

        mExtension = new ZFileExtension() {
            @Nullable
            @Override
            public IOExceptionRunnable beforeUpdate() {
                return ManifestGenerationExtension.this::updateManifest;
            }
        };

        mZFile.addZFileExtension(mExtension);
    }

    /**
     * Rebuilds the zip file's manifest, if it needs changes.
     */
    private void rebuildManifest() throws IOException {
        Verify.verifyNotNull(mZFile, "mZFile == null");

        StoredEntry manifestEntry = mZFile.get(MANIFEST_NAME);

        if (manifestEntry != null) {
            /*
             * Read the manifest entry in the zip file. Make sure we store these byte sequence
             * because writing the manifest may not generate the same byte sequence, which may
             * trigger an unnecessary re-sign of the jar.
             */
            mManifest.clear();
            byte[] manifestBytes = manifestEntry.read();
            mManifest.read(new ByteArrayInputStream(manifestBytes));
            mManifestBytes.precomputed(manifestBytes);
        }

        Attributes mainAttributes = mManifest.getMainAttributes();
        String currentVersion = mainAttributes.getValue(ManifestAttributes.MANIFEST_VERSION);
        if (currentVersion == null) {
            setMainAttribute(
                    ManifestAttributes.MANIFEST_VERSION,
                    ManifestAttributes.CURRENT_MANIFEST_VERSION);
        } else {
            if (!currentVersion.equals(ManifestAttributes.CURRENT_MANIFEST_VERSION)) {
                throw new IOException("Unsupported manifest version: " + currentVersion + ".");
            }
        }

        /*
         * We "blindly" override all other main attributes.
         */
        setMainAttribute(ManifestAttributes.BUILT_BY, mBuiltBy);
        setMainAttribute(ManifestAttributes.CREATED_BY, mCreatedBy);
    }

    /**
     * Sets the value of a main attribute.
     *
     * @param attribute the attribute
     * @param value the value
     */
    private void setMainAttribute(@NonNull String attribute, @NonNull String value) {
        Attributes mainAttributes = mManifest.getMainAttributes();
        String current = mainAttributes.getValue(attribute);
        if (!value.equals(current)) {
            mainAttributes.putValue(attribute, value);
            markDirty();
        }
    }

    /**
     * Updates the manifest in the zip file, if it has been changed.
     *
     * @throws IOException failed to update the manifest
     */
    private void updateManifest() throws IOException {
        Verify.verifyNotNull(mZFile, "mZFile == null");

        if (!mDirty) {
            return;
        }

        mZFile.add(MANIFEST_NAME, new ByteArrayInputStream(mManifestBytes.get()));
        mDirty = false;
    }

    /**
     * Obtains the {@link ZFile} this extension is associated with. This method can only be invoked
     * after {@link #register(ZFile)} has been invoked.
     *
     * @return the {@link ZFile}
     */
    @NonNull
    public ZFile zFile() {
        Preconditions.checkNotNull(mZFile, "mZFile == null");
        return mZFile;
    }

    /**
     * Obtains the stored entry in the {@link ZFile} that contains the manifest. This method can
     * only be invoked after {@link #register(ZFile)} has been invoked.
     *
     * @return the entry, {@code null} if none
     */
    @Nullable
    public StoredEntry manifestEntry() {
        Preconditions.checkNotNull(mZFile, "mZFile == null");
        return mZFile.get(MANIFEST_NAME);
    }

    /**
     * Obtains an attribute of an entry.
     *
     * @param entryName the name of the entry
     * @param attr the name of the attribute
     * @return the attribute value or {@code null} if the entry does not have any attributes or
     * if it doesn't have the specified attribute
     */
    @Nullable
    public String getAttribute(@NonNull String entryName, @NonNull String attr) {
        Attributes attrs = mManifest.getAttributes(entryName);
        if (attrs == null) {
            return null;
        }

        return attrs.getValue(attr);
    }

    /**
     * Sets the value of an attribute of an entry. If this entry's attribute already has the given
     * value, this method does nothing.
     *
     * @param entryName the name of the entry
     * @param attr the name of the attribute
     * @param value the attribute value
     */
    public void setAttribute(@NonNull String entryName, @NonNull String attr,
            @NonNull String value) {
        Attributes attrs = mManifest.getAttributes(entryName);
        if (attrs == null) {
            attrs = new Attributes();
            markDirty();
            mManifest.getEntries().put(entryName, attrs);
        }

        String current = attrs.getValue(attr);
        if (!value.equals(current)) {
            attrs.putValue(attr, value);
            markDirty();
        }
    }

    /**
     * Obtains the current manifest.
     *
     * @return a byte sequence representation of the manifest that is guaranteed not to change if
     * the manifest is not modified
     * @throws IOException failed to compute the manifest's byte representation
     */
    @NonNull
    public byte[] getManifestBytes() throws IOException {
        return mManifestBytes.get();
    }

    /**
     * Obtains all entries and all attributes they have in the manifest.
     *
     * @return a map that relates entry names to entry attributes
     */
    @NonNull
    public Map<String, Attributes> allEntries() {
        return Maps.newHashMap(mManifest.getEntries());
    }

    /**
     * Removes an entry from the manifest. If no entry exists with the given name, this operation
     * does nothing.
     *
     * @param name the entry's name
     */
    public void removeEntry(@NonNull String name) {
        if (mManifest.getEntries().remove(name) != null) {
            markDirty();
        }
    }
}
