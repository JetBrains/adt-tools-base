package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$UnitFloat$$ChunkIO {
    static PsdDecoder.DescriptorItem.UnitFloat read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.UnitFloat unitFloat = new PsdDecoder.DescriptorItem.UnitFloat();
        stack.addFirst(unitFloat);

        int size = 0;
        long byteCount = 0;

        unitFloat.unit = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        unitFloat.value = in.readFloat();

        stack.removeFirst();
        return unitFloat;
    }
}
