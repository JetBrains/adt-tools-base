package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$FixedByteArray$$ChunkIO {
    static PSDDecoder.FixedByteArray read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.FixedByteArray fixedByteArray = new PSDDecoder.FixedByteArray();
        if (stack == null) stack = new LinkedList<Object>();
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
