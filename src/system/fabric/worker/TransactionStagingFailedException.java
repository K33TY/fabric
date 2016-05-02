package fabric.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fabric.common.SerializedObject;
import fabric.common.exceptions.FabricException;
import fabric.common.util.LongKeyHashMap;
import fabric.common.util.LongKeyMap;
import fabric.net.RemoteNode;

public class TransactionStagingFailedException extends FabricException {
  /**
   * A set of objects used by the transaction and were in conflict. If the
   * object's oid maps to a non-null value, then the value will be a updated
   * version of the object.
   */
  public final LongKeyMap<SerializedObject> versionConflicts;

  public final List<String> messages;

  public TransactionStagingFailedException(
      TransactionRestartingException cause) {
    this.messages = null;
    this.versionConflicts = null;
  }

  public TransactionStagingFailedException(
      LongKeyMap<SerializedObject> versionConflicts) {
    this.versionConflicts = versionConflicts;
    this.messages = null;
  }

  public TransactionStagingFailedException(
      Map<RemoteNode<?>, TransactionStagingFailedException> failures) {
    this.versionConflicts = null;

    messages = new ArrayList<>();
    for (Map.Entry<RemoteNode<?>, TransactionStagingFailedException> entry : failures
        .entrySet()) {
      TransactionStagingFailedException exn = entry.getValue();

      if (exn.messages != null) {
        for (String s : exn.messages)
          messages.add(entry.getKey() + ": " + s);
      }
    }
  }

  public TransactionStagingFailedException(
      List<TransactionStagingFailedException> causes) {
    this.versionConflicts = new LongKeyHashMap<>();

    messages = new ArrayList<>();
    for (TransactionStagingFailedException exc : causes) {
      if (exc.versionConflicts != null)
        versionConflicts.putAll(exc.versionConflicts);

      if (exc.messages != null) messages.addAll(exc.messages);
    }
  }

  public TransactionStagingFailedException(
      LongKeyMap<SerializedObject> versionConflicts, String message) {
    this.versionConflicts = versionConflicts;
    messages = java.util.Collections.singletonList(message);
  }

  public TransactionStagingFailedException(String message) {
    this(null, message);
  }

  @Override
  public String getMessage() {
    String result = "Transaction failed to prepare.";

    if (messages != null) {
      for (String m : messages) {
        result += System.getProperty("line.separator") + "    " + m;
      }
    }

    return result;
  }

}
