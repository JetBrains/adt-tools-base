package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class GuidesResourceBlock$$ChunkIO {
    static GuidesResourceBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        GuidesResourceBlock guidesResourceBlock = new GuidesResourceBlock();
        stack.addFirst(guidesResourceBlock);

        int size = 0;
        long byteCount = 0;

        guidesResourceBlock.version = in.readInt();
        /* guidesResourceBlock.future */
        ChunkUtils.skip(in, 8);
        guidesResourceBlock.guideCount = in.readInt();
        guidesResourceBlock.guides = new ArrayList<GuideBlock>();
        size = guidesResourceBlock.guideCount;
        GuideBlock guideBlock;
        for (int i = 0; i < size; i++) {
            guideBlock = GuideBlock$$ChunkIO.read(in, stack);
            guidesResourceBlock.guides.add(guideBlock);
        }

        stack.removeFirst();
        return guidesResourceBlock;
    }
}
