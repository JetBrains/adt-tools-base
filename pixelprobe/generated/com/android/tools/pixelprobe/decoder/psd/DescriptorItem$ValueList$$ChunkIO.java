package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class DescriptorItem$ValueList$$ChunkIO {
    static DescriptorItem.ValueList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.ValueList valueList = new DescriptorItem.ValueList();
        stack.addFirst(valueList);

        int size = 0;
        long byteCount = 0;

        valueList.count = in.readInt();
        valueList.items = new ArrayList<DescriptorItem.Value>();
        size = valueList.count;
        DescriptorItem.Value value;
        for (int i = 0; i < size; i++) {
            value = DescriptorItem$Value$$ChunkIO.read(in, stack);
            valueList.items.add(value);
        }

        stack.removeFirst();
        return valueList;
    }
}
