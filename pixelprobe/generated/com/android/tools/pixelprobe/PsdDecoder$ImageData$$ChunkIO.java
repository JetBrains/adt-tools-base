package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ImageData$$ChunkIO {
    static PsdDecoder.ImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ImageData imageData = new PsdDecoder.ImageData();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(imageData);

        int size = 0;
        long byteCount = 0;

        {
            int index = in.readUnsignedShort();
            if (index > PsdDecoder.CompressionMethod.values().length) index = 0;
            imageData.compression = PsdDecoder.CompressionMethod.values()[index];
        }
        imageData.data = ChunkUtils.readUnboundedByteArray(in, 131072);

        stack.removeFirst();
        return imageData;
    }
}
