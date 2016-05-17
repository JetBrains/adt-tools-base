package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$UnitDouble$$ChunkIO {
    static PSDDecoder.DescriptorItem.UnitDouble read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.UnitDouble unitDouble = new PSDDecoder.DescriptorItem.UnitDouble();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(unitDouble);

        int size = 0;
        long byteCount = 0;

        unitDouble.unit = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        unitDouble.value = in.readDouble();

        stack.removeFirst();
        return unitDouble;
    }
}
