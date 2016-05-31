package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$ValueList$$ChunkIO {
    static PsdDecoder.DescriptorItem.ValueList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.ValueList valueList = new PsdDecoder.DescriptorItem.ValueList();
        stack.addFirst(valueList);

        int size = 0;
        long byteCount = 0;

        valueList.count = in.readInt();
        valueList.items = new ArrayList<PsdDecoder.DescriptorItem.Value>();
        size = valueList.count;
        PsdDecoder.DescriptorItem.Value value;
        for (int i = 0; i < size; i++) {
            value = PsdDecoder$DescriptorItem$Value$$ChunkIO.read(in, stack);
            valueList.items.add(value);
        }

        stack.removeFirst();
        return valueList;
    }
}
