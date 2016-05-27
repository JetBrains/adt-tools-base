package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$GuideBlock$$ChunkIO {
    static PSDDecoder.GuideBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.GuideBlock guideBlock = new PSDDecoder.GuideBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(guideBlock);

        int size = 0;
        long byteCount = 0;

        guideBlock.location = in.readInt();
        {
            int index = in.readUnsignedByte();
            if (index > Guide.Orientation.values().length) index = 0;
            guideBlock.orientation = Guide.Orientation.values()[index];
        }

        stack.removeFirst();
        return guideBlock;
    }
}
