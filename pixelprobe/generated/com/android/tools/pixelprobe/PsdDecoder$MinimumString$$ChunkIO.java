package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$MinimumString$$ChunkIO {
    static PsdDecoder.MinimumString read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.MinimumString minimumString = new PsdDecoder.MinimumString();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(minimumString);

        int size = 0;
        long byteCount = 0;

        minimumString.length = in.readInt() & 0xffffffffL;
        byteCount = Math.max(minimumString.length, 4);
        minimumString.value = ChunkUtils.readString(in, byteCount, Charset.forName("ISO-8859-1"));

        stack.removeFirst();
        return minimumString;
    }
}
