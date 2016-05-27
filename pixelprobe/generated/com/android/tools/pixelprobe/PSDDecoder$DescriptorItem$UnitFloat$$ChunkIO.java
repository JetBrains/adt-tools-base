package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$UnitFloat$$ChunkIO {
    static PSDDecoder.DescriptorItem.UnitFloat read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.UnitFloat unitFloat = new PSDDecoder.DescriptorItem.UnitFloat();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(unitFloat);

        int size = 0;
        long byteCount = 0;

        unitFloat.unit = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        unitFloat.value = in.readFloat();

        stack.removeFirst();
        return unitFloat;
    }
}
