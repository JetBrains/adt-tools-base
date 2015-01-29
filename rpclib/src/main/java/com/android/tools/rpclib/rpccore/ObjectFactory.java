////////////////////////////////////////////////////////////////////////////////
// Automatically generated file. Do not modify!
////////////////////////////////////////////////////////////////////////////////
package com.android.tools.rpclib.rpccore;

import com.android.tools.rpclib.binary.*;

import java.io.IOException;

class ObjectFactory {
  public static byte[] RpcErrorIDBytes = {-26, -83, -92, -61, 22, 123, 123, -22, -48, 126, 53, -93, 23, -5, -83, -29, -8, -8, 125, 64,};
  public static ObjectTypeID RpcErrorID = new ObjectTypeID(RpcErrorIDBytes);

  static {
    ObjectTypeID.register(RpcErrorID, Entries.RpcErrorEnum);
  }

  public static void encode(Encoder e, RpcError o) throws IOException {
    e.string(o.msg);
  }

  public static void decode(Decoder d, RpcError o) throws IOException {
    o.msg = d.string();
  }

  public enum Entries implements BinaryObjectCreator {
    RpcErrorEnum {
      @Override
      public BinaryObject create() {
        return new RpcError();
      }
    },
  }

}

