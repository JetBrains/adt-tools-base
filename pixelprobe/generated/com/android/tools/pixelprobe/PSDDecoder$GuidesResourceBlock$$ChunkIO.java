package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PSDDecoder$GuidesResourceBlock$$ChunkIO {
    static PSDDecoder.GuidesResourceBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.GuidesResourceBlock guidesResourceBlock = new PSDDecoder.GuidesResourceBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(guidesResourceBlock);

        int size = 0;
        long byteCount = 0;

        guidesResourceBlock.version = in.readInt();
        /* guidesResourceBlock.future */
        ChunkUtils.skip(in, 8);
        guidesResourceBlock.guideCount = in.readInt();
        guidesResourceBlock.guides = new ArrayList<PSDDecoder.GuideBlock>();
        size = guidesResourceBlock.guideCount;
        PSDDecoder.GuideBlock guideBlock;
        for (int i = 0; i < size; i++) {
            guideBlock = PSDDecoder$GuideBlock$$ChunkIO.read(in, stack);
            guidesResourceBlock.guides.add(guideBlock);
        }

        stack.removeFirst();
        return guidesResourceBlock;
    }
}
