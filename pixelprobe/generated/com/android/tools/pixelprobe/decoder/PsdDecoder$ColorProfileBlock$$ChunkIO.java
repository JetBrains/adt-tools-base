package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ColorProfileBlock$$ChunkIO {
    static PsdDecoder.ColorProfileBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ColorProfileBlock colorProfileBlock = new PsdDecoder.ColorProfileBlock();
        stack.addFirst(colorProfileBlock);

        int size = 0;
        long byteCount = 0;

        colorProfileBlock.icc = ChunkUtils.readUnboundedByteArray(in, 4096);

        stack.removeFirst();
        return colorProfileBlock;
    }
}
