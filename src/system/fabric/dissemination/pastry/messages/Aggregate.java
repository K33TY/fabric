package fabric.dissemination.pastry.messages;

import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;

public class Aggregate implements Message {

  private final NodeHandle sender;

  public Aggregate(NodeHandle sender) {
    this.sender = sender;
  }
  
  public NodeHandle sender() {
    return sender;
  }

  public int getPriority() {
    return LOW_PRIORITY;
  }
  
}
