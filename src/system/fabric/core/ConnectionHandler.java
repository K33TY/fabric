package fabric.core;

import java.net.SocketAddress;
import java.util.logging.Logger;

import fabric.common.AbstractConnectionHandler;
import fabric.lang.NodePrincipal;

/**
 * <p>
 * Manages a connection with a remote worker. The connection is used to receive
 * requests from and to send responses to the node.
 * </p>
 * <p>
 * XXX Assumes connections never get dropped.
 * </p>
 */
class ConnectionHandler extends
    AbstractConnectionHandler<Node.Core, SessionAttributes, Worker> {
  private final Node node;
  private static final Logger LOGGER = Logger.getLogger("fabric.core.worker");

  public ConnectionHandler(Node node) {
    super(node.opts.threadPool, new Worker.Factory());
    this.node = node;
  }

  @Override
  protected Node.Core getNodeByName(String name) {
    return node.getCore(name);
  }

  @Override
  protected SessionAttributes newAuthenticatedSession(Node.Core core,
      String clientName, NodePrincipal clientPrincipal) {
    return new SessionAttributes(core, clientName, clientPrincipal);
  }

  @Override
  protected SessionAttributes newUnauthenticatedSession(Node.Core core) {
    return new SessionAttributes(core);
  }

  @Override
  protected void logAuthenticationFailure() {
    LOGGER.info("Core rejected connection: client failed authentication.");
  }

  @Override
  protected void logSession(SocketAddress remote, SessionAttributes session) {
    LOGGER.info("Core " + session.core.name + " accepted connection from "
        + remote);

    if (session.clientIsDissem) {
      LOGGER.info("Client connected as dissemination node");
    } else {
      LOGGER.info("Client principal is " + session.clientName
          + (session.client == null ? " (acting as null)" : ""));
    }
  }

  @Override
  protected String getThreadName(SocketAddress remote, SessionAttributes session) {
    if (session.clientIsDissem) {
      return "Connection handler for dissemination node at " + remote
          + " talking to core " + session.core.name;
    }

    return "Connection handler for client " + session.clientName
        + " talking to core " + session.core.name;

  }
}
