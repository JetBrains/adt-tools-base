package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ThumbnailResourceBlock$$ChunkIO {
    static PsdDecoder.ThumbnailResourceBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ThumbnailResourceBlock thumbnailResourceBlock = new PsdDecoder.ThumbnailResourceBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(thumbnailResourceBlock);

        int size = 0;
        long byteCount = 0;

        thumbnailResourceBlock.format = in.readInt();
        ChunkUtils.checkState(thumbnailResourceBlock.format == (1),
                "Value read in format does not match expected value");
        thumbnailResourceBlock.width = in.readInt() & 0xffffffffL;
        thumbnailResourceBlock.height = in.readInt() & 0xffffffffL;
        thumbnailResourceBlock.rowBytes = in.readInt() & 0xffffffffL;
        thumbnailResourceBlock.size = in.readInt() & 0xffffffffL;
        thumbnailResourceBlock.compressedSize = in.readInt() & 0xffffffffL;
        thumbnailResourceBlock.bpp = in.readShort();
        ChunkUtils.checkState(thumbnailResourceBlock.bpp == (24),
                "Value read in bpp does not match expected value");
        thumbnailResourceBlock.planes = in.readShort();
        ChunkUtils.checkState(thumbnailResourceBlock.planes == (1),
                "Value read in planes does not match expected value");
        byteCount = thumbnailResourceBlock.compressedSize;
        thumbnailResourceBlock.thumbnail = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return thumbnailResourceBlock;
    }
}
