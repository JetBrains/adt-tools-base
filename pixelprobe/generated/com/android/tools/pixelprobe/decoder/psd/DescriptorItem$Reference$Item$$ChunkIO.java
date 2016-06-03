package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class DescriptorItem$Reference$Item$$ChunkIO {
    static DescriptorItem.Reference.Item read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.Reference.Item item = new DescriptorItem.Reference.Item();
        stack.addFirst(item);

        int size = 0;
        long byteCount = 0;

        item.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (item.type.equals("Enmr")) {
            item.data = DescriptorItem$Enumerated$$ChunkIO.read(in, stack);
        } else if (item.type.equals("Clss")) {
            item.data = DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (item.type.equals("Idnt")) {
            item.data = in.readInt();
        } else if (item.type.equals("indx")) {
            item.data = in.readInt();
        } else if (item.type.equals("name")) {
            item.data = UnicodeString$$ChunkIO.read(in, stack);
        } else if (item.type.equals("prop")) {
            item.data = DescriptorItem$Property$$ChunkIO.read(in, stack);
        } else if (item.type.equals("rele")) {
            item.data = in.readInt();
        }

        stack.removeFirst();
        return item;
    }
}
