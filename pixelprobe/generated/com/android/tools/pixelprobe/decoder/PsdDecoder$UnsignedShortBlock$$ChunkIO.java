package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$UnsignedShortBlock$$ChunkIO {
    static PsdDecoder.UnsignedShortBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.UnsignedShortBlock unsignedShortBlock = new PsdDecoder.UnsignedShortBlock();
        stack.addFirst(unsignedShortBlock);

        int size = 0;
        long byteCount = 0;

        unsignedShortBlock.data = in.readUnsignedShort();

        stack.removeFirst();
        return unsignedShortBlock;
    }
}
