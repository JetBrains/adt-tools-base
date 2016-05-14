package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$ImageData$$ChunkIO {
    static PSDDecoder.ImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ImageData imageData = new PSDDecoder.ImageData();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(imageData);

        int size = 0;
        long byteCount = 0;

        imageData.compression = PSDDecoder.CompressionMethod.values()[in.readUnsignedShort()];
        imageData.data = ChunkUtils.readUnboundedByteArray(in, 131072);

        stack.removeFirst();
        return imageData;
    }
}
