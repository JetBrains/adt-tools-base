package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$PSD$$ChunkIO {
    static PsdDecoder.PSD read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.PSD pSD = new PsdDecoder.PSD();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(pSD);

        int size = 0;
        long byteCount = 0;

        pSD.header = PsdDecoder$FileHeader$$ChunkIO.read(in, stack);
        pSD.colorData = PsdDecoder$ColorData$$ChunkIO.read(in, stack);
        pSD.resources = PsdDecoder$ImageResources$$ChunkIO.read(in, stack);
        pSD.layersInfo = PsdDecoder$LayersInformation$$ChunkIO.read(in, stack);
        pSD.imageData = PsdDecoder$ImageData$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return pSD;
    }
}
