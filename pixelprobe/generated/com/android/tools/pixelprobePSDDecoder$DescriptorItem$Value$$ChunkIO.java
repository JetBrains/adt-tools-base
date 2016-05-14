package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$Value$$ChunkIO {
    static PSDDecoder.DescriptorItem.Value read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.Value value = new PSDDecoder.DescriptorItem.Value();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(value);

        int size = 0;
        long byteCount = 0;

        value.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (value.type.equals("alis")) {
            value.data = PSDDecoder$FixedString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("bool")) {
            value.data = in.readByte() != 0;
        } else if (value.type.equals("comp")) {
            value.data = in.readLong();
        } else if (value.type.equals("doub")) {
            value.data = in.readDouble();
        } else if (value.type.equals("enum")) {
            value.data = PSDDecoder$DescriptorItem$Enumerated$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbC")) {
            value.data = PSDDecoder$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbO")) {
            value.data = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("long")) {
            value.data = in.readInt();
        } else if (value.type.equals("obj" )) {
            value.data = PSDDecoder$DescriptorItem$Reference$$ChunkIO.read(in, stack);
        } else if (value.type.equals("Objc")) {
            value.data = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("TEXT")) {
            value.data = PSDDecoder$UnicodeString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("tdta")) {
            value.data = PSDDecoder$FixedByteArray$$ChunkIO.read(in, stack);
        } else if (value.type.equals("type")) {
            value.data = PSDDecoder$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UnFl")) {
            value.data = PSDDecoder$DescriptorItem$UnitFloat$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UntF")) {
            value.data = PSDDecoder$DescriptorItem$UnitDouble$$ChunkIO.read(in, stack);
        } else if (value.type.equals("VlLs")) {
            value.data = PSDDecoder$DescriptorItem$ValueList$$ChunkIO.read(in, stack);
        }

        stack.removeFirst();
        return value;
    }
}
