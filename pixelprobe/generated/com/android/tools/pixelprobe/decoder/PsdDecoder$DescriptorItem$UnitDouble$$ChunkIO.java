package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$UnitDouble$$ChunkIO {
    static PsdDecoder.DescriptorItem.UnitDouble read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.UnitDouble unitDouble = new PsdDecoder.DescriptorItem.UnitDouble();
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
