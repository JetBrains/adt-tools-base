package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$ValueList$$ChunkIO {
    static PSDDecoder.DescriptorItem.ValueList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.ValueList valueList = new PSDDecoder.DescriptorItem.ValueList();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(valueList);

        int size = 0;
        long byteCount = 0;

        valueList.count = in.readInt();
        valueList.items = new ArrayList<PSDDecoder.DescriptorItem.Value>();
        size = valueList.count;
        PSDDecoder.DescriptorItem.Value value;
        for (int i = 0; i < size; i++) {
            value = PSDDecoder$DescriptorItem$Value$$ChunkIO.read(in, stack);
            valueList.items.add(value);
        }

        stack.removeFirst();
        return valueList;
    }
}
