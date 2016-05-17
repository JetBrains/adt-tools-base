package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$FixedString$$ChunkIO {
    static PSDDecoder.FixedString read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.FixedString fixedString = new PSDDecoder.FixedString();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(fixedString);

        int size = 0;
        long byteCount = 0;

        fixedString.length = in.readInt() & 0xffffffffL;
        byteCount = fixedString.length;
        fixedString.value = ChunkUtils.readString(in, byteCount, Charset.forName("ISO-8859-1"));

        stack.removeFirst();
        return fixedString;
    }
}
