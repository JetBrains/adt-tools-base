package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$UnsignedShortBlock$$ChunkIO {
    static PsdFile.UnsignedShortBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.UnsignedShortBlock unsignedShortBlock = new PsdFile.UnsignedShortBlock();
        stack.addFirst(unsignedShortBlock);

        int size = 0;
        long byteCount = 0;

        unsignedShortBlock.data = in.readUnsignedShort();

        stack.removeFirst();
        return unsignedShortBlock;
    }
}
