package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$Reference$Item$$ChunkIO {
    static PSDDecoder.DescriptorItem.Reference.Item read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.Reference.Item item = new PSDDecoder.DescriptorItem.Reference.Item();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(item);

        int size = 0;
        long byteCount = 0;

        item.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (item.type.equals("Enmr")) {
            item.data = PSDDecoder$DescriptorItem$Enumerated$$ChunkIO.read(in, stack);
        } else if (item.type.equals("Clss")) {
            item.data = PSDDecoder$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (item.type.equals("Idnt")) {
            item.data = in.readInt();
        } else if (item.type.equals("indx")) {
            item.data = in.readInt();
        } else if (item.type.equals("name")) {
            item.data = PSDDecoder$UnicodeString$$ChunkIO.read(in, stack);
        } else if (item.type.equals("prop")) {
            item.data = PSDDecoder$DescriptorItem$Property$$ChunkIO.read(in, stack);
        } else if (item.type.equals("rele")) {
            item.data = in.readInt();
        }

        stack.removeFirst();
        return item;
    }
}
