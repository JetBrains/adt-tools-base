package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$GuideBlock$$ChunkIO {
    static PsdDecoder.GuideBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.GuideBlock guideBlock = new PsdDecoder.GuideBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(guideBlock);

        int size = 0;
        long byteCount = 0;

        guideBlock.location = in.readInt();
        {
            int index = in.readUnsignedByte();
            if (index > PsdDecoder.Orientation.values().length) index = 0;
            guideBlock.orientation = PsdDecoder.Orientation.values()[index];
        }

        stack.removeFirst();
        return guideBlock;
    }
}
