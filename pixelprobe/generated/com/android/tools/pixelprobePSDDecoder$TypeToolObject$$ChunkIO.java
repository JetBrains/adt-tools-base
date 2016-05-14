package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$TypeToolObject$$ChunkIO {
    static PSDDecoder.TypeToolObject read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.TypeToolObject typeToolObject = new PSDDecoder.TypeToolObject();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(typeToolObject);

        int size = 0;
        long byteCount = 0;

        typeToolObject.version = in.readShort();
        typeToolObject.xx = in.readDouble();
        typeToolObject.xy = in.readDouble();
        typeToolObject.yx = in.readDouble();
        typeToolObject.yy = in.readDouble();
        typeToolObject.tx = in.readDouble();
        typeToolObject.ty = in.readDouble();
        typeToolObject.textVersion = in.readShort();
        typeToolObject.testDescriptorVersion = in.readInt();
        typeToolObject.text = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);
        typeToolObject.warpVersion = in.readShort();
        typeToolObject.warpDescriptorVersion = in.readInt();
        typeToolObject.warp = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);
        typeToolObject.left = in.readInt();
        typeToolObject.top = in.readInt();
        typeToolObject.right = in.readInt();
        typeToolObject.bottom = in.readInt();

        stack.removeFirst();
        return typeToolObject;
    }
}
