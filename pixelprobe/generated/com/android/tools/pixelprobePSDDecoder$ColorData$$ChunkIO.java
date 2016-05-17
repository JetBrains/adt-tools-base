package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$ColorData$$ChunkIO {
    static PSDDecoder.ColorData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ColorData colorData = new PSDDecoder.ColorData();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(colorData);

        int size = 0;
        long byteCount = 0;

        colorData.length = in.readInt() & 0xffffffffL;
        byteCount = colorData.length;
        /* colorData.data */
        ChunkUtils.skip(in, byteCount);

        stack.removeFirst();
        return colorData;
    }
}
