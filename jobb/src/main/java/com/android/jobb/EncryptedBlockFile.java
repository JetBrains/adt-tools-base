/*
 * Copyright (C) 2012 The Android Open Source Project 
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

package com.android.jobb;

import Twofish.Twofish_Algorithm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.InvalidKeyException;
import java.util.Arrays;

public class EncryptedBlockFile extends RandomAccessFile {

    private final class EncryptedBlockFileChannel extends FileChannel {
        final FileChannel mFC;
        
        protected EncryptedBlockFileChannel(FileChannel wrappedFC) {
            super();
            mFC = wrappedFC;
        }

        @Override
        public void force(boolean metaData) throws IOException {
            mFC.force(metaData);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            throw new RuntimeException("Lock not implemented");
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            throw new RuntimeException("MappedByteBuffer not implemented");
        }

        @Override
        public long position() throws IOException {
            return mFC.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            mFC.position(newPosition);            
            return this;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            long position = position();
            int read = read(dst, position);
            if ( read >= 0 ) {
                position += read;
                position(position);
            }
            return read;
        }

        @Override
        public int read(ByteBuffer dest, long position) throws IOException {
            boolean isMisaligned;
            boolean isPartial;
            boolean doubleBuffer;

            int toRead = dest.remaining();
            int targetRead = toRead;
            int numSectors = toRead / BYTES_PER_SECTOR;
            if ((position + toRead) > length())
                throw new IOException(
                        "reading past end of device");

            int alignmentOff;
            int firstSector = (int) position / BYTES_PER_SECTOR;
            if ( 0 != (alignmentOff = (int)(position % BYTES_PER_SECTOR ))) {
                toRead += alignmentOff;
                numSectors = toRead/BYTES_PER_SECTOR;
                isMisaligned = true;
                doubleBuffer = true;
                System.out.println("Alignment off reading from sector: " + firstSector);
            } else {
                isMisaligned = false;
                doubleBuffer = false;
                alignmentOff = 0;
            }
            
            int partialReadSize;
            if ( 0 != (partialReadSize = (int)(toRead % BYTES_PER_SECTOR ))) {
                isPartial = true;
                doubleBuffer = true;
                numSectors = toRead/BYTES_PER_SECTOR + 1;
                System.out.println("Partial read from sector: " + firstSector);
            } else {
                isPartial = false;
            }

            ByteBuffer tempDest;
            if ( doubleBuffer ) {
                tempDest = ByteBuffer.allocate(BYTES_PER_SECTOR);
            } else {
                tempDest = null;
            }
            int lastSector = firstSector + numSectors;
            if ( isMisaligned ) {
                // first sector is misaligned. Read and decrypt into temp dest
                readDecryptedSector(firstSector++, tempDest);
                tempDest.position(alignmentOff);
                // special case -- small sector;
                if ( firstSector == lastSector && isPartial ) {
                    tempDest.limit(partialReadSize);
                }
                dest.put(tempDest);
            }
            for ( int i = firstSector; i < lastSector; i++ ) {
                if ( firstSector+1 == lastSector && isPartial ) {
                    readDecryptedSector(i, tempDest);
                    tempDest.rewind();
                    tempDest.limit(partialReadSize);
                    dest.put(tempDest);
                } else {
                    readDecryptedSector(i, dest);
                }
            }
            return targetRead;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            throw new RuntimeException("Scattering Channel Read not implemented");
        }

        @Override
        public long size() throws IOException {
            return mFC.size();
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count)
                throws IOException {
            throw new RuntimeException("File Channel transfer not implemented");
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target)
                throws IOException {
            throw new RuntimeException("File Channel transfer to not implemented");
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            mFC.truncate(size);
            return this;
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return mFC.tryLock(position, size, shared);            
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            long position = position();
            int write = write(src, position);
            if ( write >= 0 ) {
                position += write;
                position(position);
            }
            return write;
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            int toWrite = src.remaining();
            int targetWrite = toWrite;
            int firstSector = (int) position / BYTES_PER_SECTOR;
            int numSectors = toWrite / BYTES_PER_SECTOR;

            boolean fixAccess = false;
            long readOffset;
            if ( 0 != position % BYTES_PER_SECTOR ) {
                long alignmentOff = (position % BYTES_PER_SECTOR);
                readOffset = position - alignmentOff;            
                toWrite += alignmentOff;
                numSectors = toWrite/BYTES_PER_SECTOR;
                fixAccess = true;
                System.out.println("Alignment off writing to sector: " + firstSector);
            } else {
                readOffset = position;
            }
            
            if ( 0 != toWrite % BYTES_PER_SECTOR ) {
                numSectors = toWrite/BYTES_PER_SECTOR + 1;
                fixAccess = true;
                System.out.println("Partial Sector [" + toWrite % BYTES_PER_SECTOR + "] writing to sector: " + firstSector);
            }

            if ( fixAccess ) {
                ByteBuffer dest = ByteBuffer.allocate(numSectors * BYTES_PER_SECTOR);
                read(dest, readOffset);
                int bufOffset = (int)(position - readOffset);
                dest.position(bufOffset);
                dest.put(src);
                
                src = dest;
                src.rewind();
            }
                    
            int lastSector = firstSector + numSectors;
            
            for ( int i = firstSector; i < lastSector; i++ ) {
                writeEncryptedSector(i, src);
            }
            return targetWrite;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            throw new RuntimeException("Scattering Channel Write not implemented");
        }

        @Override
        protected void implCloseChannel() throws IOException {
            // TODO Auto-generated method stub
            
        }

        /**
         * plain: the initial vector is the 32-bit little-endian version of the
         * sector number, padded with zeros if necessary.
         */
        private void cryptIVPlainGen(int sector, byte[] out) {
          Arrays.fill(out, (byte)0);
          out[0] = (byte)(sector & 0xff);
          out[1] = (byte)(sector >> 8 & 0xff);
          out[2] = (byte)(sector >> 16 & 0xff);
          out[3] = (byte)(sector >>> 24);
        }
        
        private void readDecryptedSector(int sector, ByteBuffer dest) throws IOException {
            ByteBuffer temp = ByteBuffer.allocate(BYTES_PER_SECTOR);
            int toRead = BYTES_PER_SECTOR;
            int devOffset = BYTES_PER_SECTOR*sector;
            
            // number of chained twofish blocks
            int blockSize = Twofish_Algorithm.blockSize();
            byte[] bufLast = new byte[blockSize];
            int numBlocks = toRead / blockSize;
            
            // read unencrypted sector
            while (toRead > 0) {
                final int read = mFC.read(temp, devOffset);
                if (read < 0)
                    throw new IOException();
                toRead -= read;
                devOffset += read;
            }
            temp.rewind();

            // set initialization vector
            cryptIVPlainGen(sector, bufLast);
            
            byte[] buf = new byte[blockSize];
            for (int i = 0; i < numBlocks; i++) {
                temp.get(buf);
                // decrypt with chained blocks --- xor with the previous encrypted block
                byte[] decryptBuf = Twofish_Algorithm.blockDecrypt(buf, 0, mKey);
                for (int j = 0; j < blockSize; j++) {
                    decryptBuf[j] ^= bufLast[j];
                }
                System.arraycopy(buf, 0, bufLast, 0, blockSize);
                dest.put(decryptBuf);
            }
        }
        
        private void writeEncryptedSector(int sector, ByteBuffer src) throws IOException {
            byte[] sectorBuf = new byte[BYTES_PER_SECTOR];
            int toRead = BYTES_PER_SECTOR;
            int devOffset = BYTES_PER_SECTOR*sector;
            
            // number of chained twofish blocks
            int blockSize = Twofish_Algorithm.blockSize();
            byte[] bufLast = new byte[blockSize];
            int numBlocks = toRead / blockSize;

            // fetch unencrypted sector
            src.get(sectorBuf);
            
            // set initialization vector
            cryptIVPlainGen(sector, bufLast);
            
            int pos = 0;
            byte[] buf = new byte[blockSize];
            for (int i = 0; i < numBlocks; i++) {
                System.arraycopy(sectorBuf, pos, buf, 0, blockSize);
                // encrypt with chained blocks --- xor with the previous encrypted block
                for (int j = 0; j < blockSize; j++) {
                    buf[j] ^= bufLast[j];
                }
                byte[] encryptBuf = Twofish_Algorithm.blockEncrypt(buf, 0, mKey);
                bufLast = encryptBuf;
                int toWrite = blockSize;
                ByteBuffer encryptBuffer = ByteBuffer.wrap(encryptBuf);
                while (toWrite > 0) {
                    final int written = mFC.write(encryptBuffer, devOffset);
                    if (written < 0)
                        throw new IOException();
                    toWrite -= written;
                    devOffset += written;
                }
                pos += blockSize;
            }
        }
                
        
    }
    
    public EncryptedBlockFileChannel getEncryptedFileChannel() {
        return mEBFC;
    }
    
    /**
     * This will clear the file as well as set the length.  It would be easy enough
     * to preserve the blocks, but that is not the intention of this class.
     */
    @Override
    public void setLength(long newLength) throws IOException {
        int numsectors = (int)newLength/BYTES_PER_SECTOR;
        if ( newLength % BYTES_PER_SECTOR != 0 ) {
            throw new IOException("Invalid file size!");
        }
        super.setLength(newLength);
        // write encrypted empty sectors into the block storage
        byte[] byteBuf = new byte[BYTES_PER_SECTOR];
        ByteBuffer buf = ByteBuffer.wrap(byteBuf);
        for ( int i = 0; i < numsectors; i++ ) {
            buf.rewind();
            mEBFC.write(buf);
        }        
    }

    /**
     * The number of bytes per sector for all {@code FileDisk} instances.
     */
    public final static int BYTES_PER_SECTOR = 512;

    private final Object mKey;
    private final EncryptedBlockFileChannel mEBFC;

    public EncryptedBlockFile(byte[] key, File file, String mode) throws FileNotFoundException,
            InvalidKeyException {
        super(file, mode);
        mEBFC = new EncryptedBlockFileChannel(getChannel());
        if (!file.exists())
            throw new FileNotFoundException();

        mKey = Twofish_Algorithm.makeKey(key);
    }
}
