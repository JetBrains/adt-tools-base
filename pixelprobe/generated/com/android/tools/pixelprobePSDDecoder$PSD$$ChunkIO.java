package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$PSD$$ChunkIO {
    static PSDDecoder.PSD read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.PSD pSD = new PSDDecoder.PSD();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(pSD);

        int size = 0;
        long byteCount = 0;

        pSD.header = PSDDecoder$FileHeader$$ChunkIO.read(in, stack);
        pSD.colorData = PSDDecoder$ColorData$$ChunkIO.read(in, stack);
        pSD.resources = PSDDecoder$ImageResources$$ChunkIO.read(in, stack);
        pSD.layersInfo = PSDDecoder$LayersInformation$$ChunkIO.read(in, stack);
        pSD.imageData = PSDDecoder$ImageData$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return pSD;
    }
}
