package fabric.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import fabric.common.exceptions.AccessException;
import fabric.common.exceptions.ProtocolError;
import fabric.lang.security.Principal;
import fabric.worker.memoize.CallInstance;
import fabric.worker.memoize.WarrantiedCallResult;

/**
 * A <code>ReuseCallMessage</code> represents a request from a worker to reuse a
 * value of a previously computed call at a store.
 */
public class ReuseCallMessage extends Message<ReuseCallMessage.Response,
       AccessException> {
  // ////////////////////////////////////////////////////////////////////////////
  // message contents //
  // ////////////////////////////////////////////////////////////////////////////

  /** The call to reuse. */
  public final CallInstance call;

  public ReuseCallMessage(CallInstance call) {
    super(MessageType.REUSE_CALL, AccessException.class);
    this.call = call;
  }

  // ////////////////////////////////////////////////////////////////////////////
  // response contents //
  // ////////////////////////////////////////////////////////////////////////////

  public static class Response implements Message.Response {

    public final WarrantiedCallResult result;

    public Response(WarrantiedCallResult result) {
      this.result = result;
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
    call.write(out);
  }

  /* readMessage */
  protected ReuseCallMessage(DataInput in) throws IOException {
    super(MessageType.REUSE_CALL, AccessException.class);
    this.call = new CallInstance(in);
  }

  @Override
  protected void writeResponse(DataOutput out, Response r) throws IOException {
    if (r.result != null) {
      out.writeBoolean(true);
      r.result.write(out);
    } else out.writeBoolean(false);
  }

  @Override
  protected Response readResponse(DataInput in) throws IOException {
    WarrantiedCallResult result = in.readBoolean() ? new WarrantiedCallResult(in) : null;
    return new Response(result);
  }
}
