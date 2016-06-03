package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class FixedString$$ChunkIO {
    static FixedString read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        FixedString fixedString = new FixedString();
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
