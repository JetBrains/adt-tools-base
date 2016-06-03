package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class DescriptorItem$Value$$ChunkIO {
    static DescriptorItem.Value read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.Value value = new DescriptorItem.Value();
        stack.addFirst(value);

        int size = 0;
        long byteCount = 0;

        value.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (value.type.equals("alis")) {
            value.data = FixedString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("bool")) {
            value.data = in.readByte() != 0;
        } else if (value.type.equals("comp")) {
            value.data = in.readLong();
        } else if (value.type.equals("doub")) {
            value.data = in.readDouble();
        } else if (value.type.equals("enum")) {
            value.data = DescriptorItem$Enumerated$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbC")) {
            value.data = DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbO")) {
            value.data = Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("long")) {
            value.data = in.readInt();
        } else if (value.type.equals("obj" )) {
            value.data = DescriptorItem$Reference$$ChunkIO.read(in, stack);
        } else if (value.type.equals("Objc")) {
            value.data = Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("TEXT")) {
            value.data = UnicodeString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("tdta")) {
            value.data = FixedByteArray$$ChunkIO.read(in, stack);
        } else if (value.type.equals("type")) {
            value.data = DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UnFl")) {
            value.data = DescriptorItem$UnitFloat$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UntF")) {
            value.data = DescriptorItem$UnitDouble$$ChunkIO.read(in, stack);
        } else if (value.type.equals("VlLs")) {
            value.data = DescriptorItem$ValueList$$ChunkIO.read(in, stack);
        }

        stack.removeFirst();
        return value;
    }
}
