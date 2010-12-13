package fabric.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import fabric.common.TransactionID;
import fabric.common.exceptions.AccessException;
import fabric.lang.security.NodePrincipal;

public class AbortTransactionMessage
     extends Message<AbortTransactionMessage.Response, AccessException>
{
  //////////////////////////////////////////////////////////////////////////////
  // message  contents                                                        //
  //////////////////////////////////////////////////////////////////////////////

  /** The tid for the transaction that is aborting.  */
  public final TransactionID tid;

  public AbortTransactionMessage(TransactionID tid) {
    super(MessageType.ABORT_TRANSACTION, AccessException.class);
    this.tid = tid;
  }


  //////////////////////////////////////////////////////////////////////////////
  // response contents                                                        //
  //////////////////////////////////////////////////////////////////////////////

  public static class Response implements Message.Response {
    public Response() {
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // visitor methods                                                          //
  //////////////////////////////////////////////////////////////////////////////

  @Override
  public Response dispatch(NodePrincipal p, MessageHandler h) throws AccessException {
    return h.handle(p, this);
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // serialization cruft                                                      //
  //////////////////////////////////////////////////////////////////////////////

  @Override
  protected void writeMessage(DataOutput out) throws IOException {
    tid.write(out);
  }

  /* readMessage */
  protected AbortTransactionMessage(DataInput in) throws IOException {
    this(new TransactionID(in));
  }

  @Override
  protected void writeResponse(DataOutput out, Response r) {
  }

  @Override
  protected Response readResponse(DataInput in) {
    return new Response();
  }
}
