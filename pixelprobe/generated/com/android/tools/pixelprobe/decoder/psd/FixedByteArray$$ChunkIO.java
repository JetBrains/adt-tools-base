package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class FixedByteArray$$ChunkIO {
    static FixedByteArray read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        FixedByteArray fixedByteArray = new FixedByteArray();
        stack.addFirst(fixedByteArray);

        int size = 0;
        long byteCount = 0;

        fixedByteArray.length = in.readInt() & 0xffffffffL;
        byteCount = fixedByteArray.length;
        fixedByteArray.value = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return fixedByteArray;
    }
}
