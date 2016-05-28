package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$Reference$$ChunkIO {
    static PsdDecoder.DescriptorItem.Reference read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.Reference reference = new PsdDecoder.DescriptorItem.Reference();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(reference);

        int size = 0;
        long byteCount = 0;

        reference.count = in.readInt();
        reference.items = new ArrayList<PsdDecoder.DescriptorItem.Reference.Item>();
        size = reference.count;
        PsdDecoder.DescriptorItem.Reference.Item item;
        for (int i = 0; i < size; i++) {
            item = PsdDecoder$DescriptorItem$Reference$Item$$ChunkIO.read(in, stack);
            reference.items.add(item);
        }

        stack.removeFirst();
        return reference;
    }
}
