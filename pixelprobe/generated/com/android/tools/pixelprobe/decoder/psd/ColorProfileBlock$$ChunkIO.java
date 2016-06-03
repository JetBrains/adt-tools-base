package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class ColorProfileBlock$$ChunkIO {
    static ColorProfileBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        ColorProfileBlock colorProfileBlock = new ColorProfileBlock();
        stack.addFirst(colorProfileBlock);

        int size = 0;
        long byteCount = 0;

        colorProfileBlock.icc = ChunkUtils.readUnboundedByteArray(in, 4096);

        stack.removeFirst();
        return colorProfileBlock;
    }
}
