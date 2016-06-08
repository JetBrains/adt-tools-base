package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$Value$$ChunkIO {
    static PsdFile.DescriptorItem.Value read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Value value = new PsdFile.DescriptorItem.Value();
        stack.addFirst(value);

        int size = 0;
        long byteCount = 0;

        value.type = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        if (value.type.equals("alis")) {
            value.data = PsdFile$FixedString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("bool")) {
            value.data = in.readByte() != 0;
        } else if (value.type.equals("comp")) {
            value.data = in.readLong();
        } else if (value.type.equals("doub")) {
            value.data = in.readDouble();
        } else if (value.type.equals("enum")) {
            value.data = PsdFile$DescriptorItem$Enumerated$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbC")) {
            value.data = PsdFile$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("GlbO")) {
            value.data = PsdFile$Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("long")) {
            value.data = in.readInt();
        } else if (value.type.equals("obj" )) {
            value.data = PsdFile$DescriptorItem$Reference$$ChunkIO.read(in, stack);
        } else if (value.type.equals("Objc")) {
            value.data = PsdFile$Descriptor$$ChunkIO.read(in, stack);
        } else if (value.type.equals("TEXT")) {
            value.data = PsdFile$UnicodeString$$ChunkIO.read(in, stack);
        } else if (value.type.equals("tdta")) {
            value.data = PsdFile$FixedByteArray$$ChunkIO.read(in, stack);
        } else if (value.type.equals("type")) {
            value.data = PsdFile$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UnFl")) {
            value.data = PsdFile$DescriptorItem$UnitFloat$$ChunkIO.read(in, stack);
        } else if (value.type.equals("UntF")) {
            value.data = PsdFile$DescriptorItem$UnitDouble$$ChunkIO.read(in, stack);
        } else if (value.type.equals("VlLs")) {
            value.data = PsdFile$DescriptorItem$ValueList$$ChunkIO.read(in, stack);
        }

        stack.removeFirst();
        return value;
    }
}
