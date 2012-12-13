package fabric.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fabric.common.SerializedObject;
import fabric.common.VersionWarranty;
import fabric.common.exceptions.AccessException;
import fabric.common.exceptions.ProtocolError;
import fabric.common.util.LongKeyHashMap;
import fabric.common.util.LongKeyMap;
import fabric.common.util.Pair;
import fabric.lang.security.Principal;

/**
 * A <code>StalenessCheckMessage</code> represents a request to a store to check
 * whether a given set of objects is still fresh.
 */
public final class StalenessCheckMessage extends
    Message<StalenessCheckMessage.Response, AccessException> {

  // ////////////////////////////////////////////////////////////////////////////
  // message contents //
  // ////////////////////////////////////////////////////////////////////////////

  public final LongKeyMap<Integer> versions;

  public StalenessCheckMessage(LongKeyMap<Integer> versions) {
    super(MessageType.STALENESS_CHECK, AccessException.class);
    this.versions = versions;
  }

  // ////////////////////////////////////////////////////////////////////////////
  // message contents //
  // ////////////////////////////////////////////////////////////////////////////

  public static class Response implements Message.Response {
    public final List<Pair<SerializedObject, VersionWarranty>> staleObjects;

    public Response(List<Pair<SerializedObject, VersionWarranty>> staleObjects) {
      this.staleObjects = staleObjects;
    }
  }

  // ////////////////////////////////////////////////////////////////////////////
  // visitor methods //
  // ////////////////////////////////////////////////////////////////////////////

  @Override
  public Response dispatch(Principal p, MessageHandler h) throws ProtocolError,
      AccessException {
    return h.handle(p, this);
  }

  // ////////////////////////////////////////////////////////////////////////////
  // serialization cruft //
  // ////////////////////////////////////////////////////////////////////////////

  @Override
  protected void writeMessage(DataOutput out) throws IOException {
    out.writeInt(versions.size());
    for (LongKeyMap.Entry<Integer> entry : versions.entrySet()) {
      out.writeLong(entry.getKey());
      out.writeInt(entry.getValue());
    }
  }

  /* readMessage */
  protected StalenessCheckMessage(DataInput in) throws IOException {
    this(readMap(in));
  }

  /* helper method for deserialization constructor */
  private static LongKeyHashMap<Integer> readMap(DataInput in)
      throws IOException {
    int size = in.readInt();
    LongKeyHashMap<Integer> versions = new LongKeyHashMap<Integer>(size);
    for (int i = 0; i < size; i++)
      versions.put(in.readLong(), in.readInt());

    return versions;
  }

  @Override
  protected void writeResponse(DataOutput out, Response r) throws IOException {
    out.writeInt(r.staleObjects.size());
    for (Pair<SerializedObject, VersionWarranty> obj : r.staleObjects) {
      obj.first.write(out);
      out.writeLong(obj.second.expiry());
    }
  }

  @Override
  protected Response readResponse(DataInput in) throws IOException {
    int size = in.readInt();
    List<Pair<SerializedObject, VersionWarranty>> staleObjects =
        new ArrayList<Pair<SerializedObject, VersionWarranty>>(size);
    for (int i = 0; i < size; i++) {
      SerializedObject obj = new SerializedObject(in);
      VersionWarranty warranty = new VersionWarranty(in.readLong());
      staleObjects.add(new Pair<SerializedObject, VersionWarranty>(obj,
          warranty));
    }

    return new Response(staleObjects);
  }

}
