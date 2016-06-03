package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$$ChunkIO {
    static PsdFile read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile psdFile = new PsdFile();
        stack.addFirst(psdFile);

        int size = 0;
        long byteCount = 0;

        psdFile.header = Header$$ChunkIO.read(in, stack);
        psdFile.colorData = ColorData$$ChunkIO.read(in, stack);
        psdFile.resources = ImageResources$$ChunkIO.read(in, stack);
        psdFile.layersInfo = LayersInformation$$ChunkIO.read(in, stack);
        psdFile.imageData = ImageData$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return psdFile;
    }
}
