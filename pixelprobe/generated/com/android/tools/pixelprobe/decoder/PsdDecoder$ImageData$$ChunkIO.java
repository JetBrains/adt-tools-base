package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ImageData$$ChunkIO {
    static PsdDecoder.ImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ImageData imageData = new PsdDecoder.ImageData();
        stack.addFirst(imageData);

        int size = 0;
        long byteCount = 0;

        imageData.compression = PsdDecoder.CompressionMethod.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.CompressionMethod.values().length - 1))];
        imageData.data = ChunkUtils.readUnboundedByteArray(in, 131072);

        stack.removeFirst();
        return imageData;
    }
}
