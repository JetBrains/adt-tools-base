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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.utils.LittleEndianUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

/**
 * The ZipField class represents a field in a record in a zip file. Zip files are made with records
 * that have fields. This class makes it easy to read, write and verify field values.
 * <p>
 * There are two main types of fields: 2-byte fields and 4-byte fields. We represent each one as
 * a subclass of {@code ZipField}, {@code F2} for the 2-byte field and {@code F4} for the 4-byte
 * field. Because Java's {@code int} data type is guaranteed to be 4-byte, all methods use Java's
 * native {@link int} as data type.
 * <p>
 * For each field we can either read, write or verify. Verification is used for fields whose value
 * we know. Some fields, <em>e.g.</em> signature fields, have fixed value. Other fields have
 * variable values, but in some situations we know which value they have. For example, the last
 * modification time of a file's local header will have to match the value of the file's
 * modification time as stored in the central directory.
 * <p>
 * Because records are compact, <em>i.e.</em> fields are stored sequentially with no empty spaces,
 * fields are generally created in the sequence they exist and the end offset of a field is used
 * as the offset of the next one. The end of a field can be obtained by invoking
 * {@link #endOffset()}. This allows creating fields in sequence without doing offset computation:
 * <pre>
 * ZipField.F2 firstField = new ZipField.F2(0, "First field");
 * ZipField.F4 secondField = new ZipField(firstField.endOffset(), "Second field");
 * </pre>
 */
abstract class ZipField {

    /**
     * Field name. Used for providing (more) useful error messages.
     */
    @NonNull
    private final String mName;

    /**
     * Offset of the file in the record.
     */
    protected final int mOffset;

    /**
     * Size of the field. Only 2 or 4 allowed.
     */
    private final int mSize;

    /**
     * If a fixed value exists for the field, then this attribute will contain that value.
     */
    @Nullable
    private final Long mExpected;

    /**
     * All invariants that this field must verify.
     */
    @NonNull
    private Set<ZipFieldInvariant> mInvariants;

    /**
     * Creates a new field that does not contain a fixed value.
     *
     * @param offset the field's offset in the record
     * @param size the field size
     * @param name the field's name
     * @param invariants the invariants that must be verified by the field
     */
    ZipField(int offset, int size, @NonNull String name, ZipFieldInvariant... invariants) {
        Preconditions.checkArgument(offset >= 0, "offset >= 0");
        Preconditions.checkArgument(size == 2 || size == 4, "size != 2 && size != 4");

        mName = name;
        mOffset = offset;
        mSize = size;
        mExpected = null;
        mInvariants = Sets.newHashSet(invariants);
    }

    /**
     * Creates a new field that contains a fixed value.
     *
     * @param offset the field's offset in the record
     * @param size the field size
     * @param expected the expected field value
     * @param name the field's name
     */
    ZipField(int offset, int size, long expected, @NonNull String name) {
        Preconditions.checkArgument(offset >= 0, "offset >= 0");
        Preconditions.checkArgument(size == 2 || size == 4, "size != 2 && size != 4");

        mName = name;
        mOffset = offset;
        mSize = size;
        mExpected = expected;
        mInvariants = Sets.newHashSet();
    }

    /**
     * Checks whether a value verifies the field's invariants. Nothing happens if the value verifies
     * the invariants.
     *
     * @param value the value
     * @throws IOException the invariants are not verified
     */
    private void checkVerifiesInvariants(long value) throws IOException {
        for (ZipFieldInvariant invariant : mInvariants) {
            if (!invariant.isValid(value)) {
                throw new IOException("Value " + value + " of field " + mName + " is invalid "
                        + "(fails '" + invariant.getName() + "').");
            }
        }
    }

    /**
     * Advances the position in the provided byte buffer by the size of this field.
     *
     * @param bytes the byte buffer; at the end of the method its position will be greater by
     * the size of this field
     * @throws IOException failed to advance the buffer
     */
    void skip(@NonNull ByteBuffer bytes) throws IOException {
        if (bytes.remaining() < mSize) {
            throw new IOException("Cannot skip field " + mName + " because only "
                    + bytes.remaining() + " remain in the buffer.");
        }

        bytes.position(bytes.position() + mSize);
    }

    /**
     * Reads a field value.
     *
     * @param bytes the byte buffer with the record data; after this method finishes, the buffer
     * will be positioned at the first byte after the field
     * @return the value of the field
     * @throws IOException failed to read the field
     */
    long read(@NonNull ByteBuffer bytes) throws IOException {
        if (bytes.remaining() < mSize) {
            throw new IOException("Cannot skip field " + mName + " because only "
                    + bytes.remaining() + " remain in the buffer.");
        }

        bytes.order(ByteOrder.LITTLE_ENDIAN);

        long r;
        if (mSize == 2) {
            r = LittleEndianUtils.readUnsigned2Le(bytes);
        } else {
            r =  LittleEndianUtils.readUnsigned4Le(bytes);
        }

        checkVerifiesInvariants(r);
        return r;
    }

    /**
     * Verifies that the field at the current buffer position has the expected value. The field
     * must have been created with the constructor that defines the expected value.
     *
     * @param bytes the byte buffer with the record data; after this method finishes, the buffer
     * will be positioned at the first byte after the field
     * @throws IOException failed to read the field or the field does not have the expected value
     */
    void verify(@NonNull ByteBuffer bytes) throws IOException {
        Preconditions.checkState(mExpected != null, "mExpected == null");
        verify(bytes, mExpected);
    }

    /**
     * Verifies that the field has an expected value.
     *
     * @param bytes the byte buffer with the record data; after this method finishes, the buffer
     * will be positioned at the first byte after the field
     * @param expected the value we expect the field to have; if this field has invariants, the
     * value must verify them
     * @throws IOException failed to read the data or the field does not have the expected value
     */
    void verify(@NonNull ByteBuffer bytes, long expected) throws IOException {
        checkVerifiesInvariants(expected);
        long r = read(bytes);
        if (r != expected) {
            throw new IOException("Incorrect value for field '" + mName + "': value is " +
                    r + " but " + expected + " expected.");
        }
    }

    /**
     * Writes the value of the field.
     *
     * @param output where to write the field; the field will be written at the current position
     * of the buffer
     * @param value the value to write
     * @throws IOException failed to write the value in the stream
     */
    void write(@NonNull ByteBuffer output, long value) throws IOException {
        checkVerifiesInvariants(value);

        Preconditions.checkArgument(value >= 0, "value (%s) < 0", value);

        if (mSize == 2) {
            Preconditions.checkArgument(value <= 0x0000ffff, "value (%s) > 0x0000ffff", value);
            LittleEndianUtils.writeUnsigned2Le(output, Ints.checkedCast(value));
        } else {
            Verify.verify(mSize == 4);
            Preconditions.checkArgument(value <= 0x00000000ffffffffL,
                    "value (%s) > 0x00000000ffffffffL", value);
            LittleEndianUtils.writeUnsigned4Le(output, value);
        }
    }

    /**
     * Writes the value of the field. The field must have an expected value set in the constructor.
     *
     * @param output where to write the field; the field will be written at the current position
     * of the buffer
     * @throws IOException failed to write the value in the stream
     */
    void write(@NonNull ByteBuffer output) throws IOException {
        Preconditions.checkState(mExpected != null, "mExpected == null");
        write(output, mExpected);
    }

    /**
     * Obtains the offset at which the field ends. This is the exact offset at which the next
     * field starts.
     *
     * @return the end offset
     */
    int endOffset() {
        return mOffset + mSize;
    }

    /**
     * Concrete implementation of {@link ZipField} that represents a 2-byte field.
     */
    static class F2 extends ZipField {

        /**
         * Creates a new field.
         *
         * @param offset the field's offset in the record
         * @param name the field's name
         * @param invariants the invariants that must be verified by the field
         */
        F2(int offset, @NonNull String name, ZipFieldInvariant... invariants) {
            super(offset, 2, name, invariants);
        }

        /**
         * Creates a new field that contains a fixed value.
         *
         * @param offset the field's offset in the record
         * @param expected the expected field value
         * @param name the field's name
         */
        F2(int offset, long expected, @NonNull String name) {
            super(offset, 2, expected, name);
        }
    }

    /**
     * Concrete implementation of {@link ZipField} that represents a 4-byte field.
     */
    static class F4 extends ZipField {
        /**
         * Creates a new field.
         *
         * @param offset the field's offset in the record
         * @param name the field's name
         * @param invariants the invariants that must be verified by the field
         */
        F4(int offset, @NonNull String name, ZipFieldInvariant... invariants) {
            super(offset, 4, name, invariants);
        }

        /**
         * Creates a new field that contains a fixed value.
         *
         * @param offset the field's offset in the record
         * @param expected the expected field value
         * @param name the field's name
         */
        F4(int offset, long expected, @NonNull String name) {
            super(offset, 4, expected, name);
        }
    }
}
