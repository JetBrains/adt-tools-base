package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class DescriptorItem$Reference$$ChunkIO {
    static DescriptorItem.Reference read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.Reference reference = new DescriptorItem.Reference();
        stack.addFirst(reference);

        int size = 0;
        long byteCount = 0;

        reference.count = in.readInt();
        reference.items = new ArrayList<DescriptorItem.Reference.Item>();
        size = reference.count;
        DescriptorItem.Reference.Item item;
        for (int i = 0; i < size; i++) {
            item = DescriptorItem$Reference$Item$$ChunkIO.read(in, stack);
            reference.items.add(item);
        }

        stack.removeFirst();
        return reference;
    }
}
