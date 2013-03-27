package fabric.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import fabric.common.net.RemoteIdentity;
import fabric.worker.TransactionCommitFailedException;

public class CommitTransactionMessage
    extends
    Message<CommitTransactionMessage.Response, TransactionCommitFailedException> {
  // ////////////////////////////////////////////////////////////////////////////
  // message contents //
  // ////////////////////////////////////////////////////////////////////////////

  public final long transactionID;
  public final long commitTime;

  public CommitTransactionMessage(long transactionID, long commitTime) {
    super(MessageType.COMMIT_TRANSACTION,
        TransactionCommitFailedException.class);
    this.transactionID = transactionID;
    this.commitTime = commitTime;
  }

  // ////////////////////////////////////////////////////////////////////////////
  // response contents //
  // ////////////////////////////////////////////////////////////////////////////

  public static class Response implements Message.Response {
  }

  // ////////////////////////////////////////////////////////////////////////////
  // visitor methods //
  // ////////////////////////////////////////////////////////////////////////////

  @Override
  public Response dispatch(RemoteIdentity client, MessageHandler h)
      throws TransactionCommitFailedException {
    return h.handle(client, this);
  }

  // ////////////////////////////////////////////////////////////////////////////
  // serialization cruft //
  // ////////////////////////////////////////////////////////////////////////////

  @Override
  protected void writeMessage(DataOutput out) throws IOException {
    out.writeLong(transactionID);
    out.writeLong(commitTime);
  }

  /* readMessage */
  protected CommitTransactionMessage(DataInput in) throws IOException {
    this(in.readLong(), in.readLong());
  }

  @Override
  protected void writeResponse(DataOutput out, Response r) {
    // do nothing
  }

  @Override
  protected Response readResponse(DataInput in) {
    return new Response();
  }

}
