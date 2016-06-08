package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$Reference$$ChunkIO {
    static PsdFile.DescriptorItem.Reference read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Reference reference = new PsdFile.DescriptorItem.Reference();
        stack.addFirst(reference);

        int size = 0;
        long byteCount = 0;

        reference.count = in.readInt();
        reference.items = new ArrayList<PsdFile.DescriptorItem.Reference.Item>();
        size = reference.count;
        PsdFile.DescriptorItem.Reference.Item item;
        for (int i = 0; i < size; i++) {
            item = PsdFile$DescriptorItem$Reference$Item$$ChunkIO.read(in, stack);
            reference.items.add(item);
        }

        stack.removeFirst();
        return reference;
    }
}
