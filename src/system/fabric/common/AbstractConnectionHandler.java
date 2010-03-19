package fabric.common;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fabric.client.Client;
import fabric.client.Core;
import fabric.common.AbstractMessageHandlerThread.Pool;
import fabric.common.AbstractMessageHandlerThread.SessionAttributes;
import fabric.common.exceptions.InternalError;
import fabric.common.util.Pair;
import fabric.lang.NodePrincipal;
import fabric.net.ChannelMultiplexerThread;

/**
 * Abstraction for initializing incoming network connections and handing them
 * off to a ChannelMultiplexerThread.
 * 
 * @see fabric.net.CommManager#connect()
 * @param <Node>
 *          a class for representing the nodes that can be connected to.
 * @param <Session>
 *          a class of session objects.
 */
public abstract class AbstractConnectionHandler<Node, Session extends SessionAttributes, MessageHandlerThread extends AbstractMessageHandlerThread<Session, MessageHandlerThread>> {
  private boolean destroyed;
  private final AbstractMessageHandlerThread.Pool<MessageHandlerThread> threadPool;
  private final Set<ChannelMultiplexerThread> activeMuxThreads;

  protected AbstractConnectionHandler(int poolSize,
      AbstractMessageHandlerThread.Factory<MessageHandlerThread> handlerFactory) {
    this.threadPool = new Pool<MessageHandlerThread>(poolSize, handlerFactory);
    this.activeMuxThreads = new HashSet<ChannelMultiplexerThread>();
    this.destroyed = false;
  }

  /**
   * @return an object representing the node with the given name. Can be null if
   *         the node doesn't exist at this host.
   */
  protected abstract Node getNodeByName(String name);

  /**
   * @return a Session object representing an unauthenticated, unencrypted
   *         session. A null value is returned if this node doesn't support this
   *         type of session.
   * @param node
   *          the local node with which the session was established.
   * @param remoteNodeName
   *          the name of the remote node.
   */
  protected Session newUnauthenticatedSession(Node node, String remoteNodeName) {
    return null;
  }

  /**
   * @return a Session object representing an authenticated, encrypted session.
   * @param node
   *          the local node with which the session was established.
   * @param remoteNodeName
   *          the name of the remote node.
   * @param remoteNodePrincipalName
   *          the String representation of the remote node's principal.
   * @param remoteNodePrincipal
   *          the NodePrincipal corresponding to the remote node.
   */
  protected abstract Session newAuthenticatedSession(Node node,
      String remoteNodeName, String remoteNodePrincipalName,
      NodePrincipal remoteNodePrincipal);

  /**
   * Logs an authentication failure of the remote host.
   */
  protected abstract void logAuthenticationFailure();

  /**
   * Logs a successful connection.
   */
  protected abstract void logSession(SocketAddress remote, Session session);

  /**
   * Returns the name for the message-handler thread that will be handling the
   * given connection.
   */
  protected abstract String getThreadName(SocketAddress remote, Session session);

  public final void handle(final SocketChannel connection) {
    // XXX Dirty hack: start a new thread for initializing the connection.
    // Should really be using the muxer thread for this.

    new Thread("Connection initializer") {
      @Override
      public void run() {
        try {
          Session session = initializeConnection(connection);
          if (session == null) {
            // Connection setup failed.
            logAuthenticationFailure();
            connection.close();
            return;
          }

          SocketAddress remote = connection.socket().getRemoteSocketAddress();
          logSession(remote, session);

          synchronized (AbstractConnectionHandler.this) {
            if (destroyed) return;

            ChannelMultiplexerThread mux =
                new ChannelMultiplexerThread(new CallbackHandler(session),
                    getThreadName(remote, session), connection);
            activeMuxThreads.add(mux);
            mux.start();
          }
        } catch (IOException e) {
          throw new InternalError(e);
        }
      }
    }.start();
  }

  public synchronized final void shutdown() {
    destroyed = true;
    for (ChannelMultiplexerThread mux : activeMuxThreads) {
      mux.shutdown();
    }

    threadPool.shutdown();
  }

  private Session initializeConnection(SocketChannel connection)
      throws IOException {
    // Get the name of the node that the remote host is talking to and obtain a
    // representation of that node.
    DataInput dataIn =
        new DataInputStream(connection.socket().getInputStream());
    OutputStream out = connection.socket().getOutputStream();

    String nodeName = dataIn.readUTF();
    Node node = getNodeByName(nodeName);

    if (node == null) {
      // Indicate that the node doesn't exist here.
      out.write(0);
      out.flush();
      return null;
    }

    // Indicate that the node exists.
    out.write(1);
    out.flush();

    // Get the remote node object.
    String remoteNodeName = dataIn.readUTF();

    return initializeSession(node, remoteNodeName, dataIn);
  }

  private Session initializeSession(Node node, String remoteNodeName,
      DataInput dataIn) throws IOException {
    boolean usingSSL = dataIn.readBoolean();
    if (!usingSSL) {
      return newUnauthenticatedSession(node, remoteNodeName);
    }

    // Encrypted connection.
    String remoteNodePrincipalName;
    if (!Options.DEBUG_NO_SSL) {
      // XXX TODO Start encrypting.
      // // Initiate the SSL handshake and initialize the fields.
      // SSLSocketFactory sslSocketFactory = node.getSSLSocketFactory(coreName);
      // synchronized (sslSocketFactory) {
      // sslSocket =
      // (SSLSocket) sslSocketFactory.createSocket(socket, null, 0, true);
      // }
      // sslSocket.setUseClientMode(false);
      // sslSocket.setNeedClientAuth(true);
      // sslSocket.startHandshake();
      // this.out =
      // new DataOutputStream(new BufferedOutputStream(sslSocket
      // .getOutputStream()));
      // this.out.flush();
      // this.in =
      // new DataInputStream(new BufferedInputStream(sslSocket
      // .getInputStream()));
      // this.clientName = sslSocket.getSession().getPeerPrincipal().getName();
      remoteNodePrincipalName = dataIn.readUTF();
    } else {
      remoteNodePrincipalName = dataIn.readUTF();
    }

    // Read in the pointer to the principal object.
    NodePrincipal remoteNodePrincipal = null;
    if (dataIn.readBoolean()) {
      String principalCoreName = dataIn.readUTF();
      Core principalCore = Client.getClient().getCore(principalCoreName);
      long principalOnum = dataIn.readLong();
      remoteNodePrincipal =
          new NodePrincipal._Proxy(principalCore, principalOnum);
    }

    Pair<Boolean, NodePrincipal> authResult =
        authenticateRemote(remoteNodePrincipal, remoteNodePrincipalName);
    if (authResult.first)
      return newAuthenticatedSession(node, remoteNodeName,
          remoteNodePrincipalName, authResult.second);

    return null;
  }

  /**
   * Determines whether the given node principal matches the given name.
   * 
   * @return a pair indicating whether the authentication succeeded, and the
   *         principal for which the node was successfully authenticated.
   */
  private Pair<Boolean, NodePrincipal> authenticateRemote(
      final NodePrincipal principal, final String name) {
    // Bypass authentication if we have a null client.
    // This is to allow bootstrapping the client principal.
    // This is safe because everyone acts for null anyway.
    if (principal == null) return new Pair<Boolean, NodePrincipal>(true, null);

    return Client
        .runInTransactionUnauthenticated(new Client.Code<Pair<Boolean, NodePrincipal>>() {
          public Pair<Boolean, NodePrincipal> run() {
            boolean success = false;
            NodePrincipal authenticatedPrincipal = null;
            try {
              if (principal.name().equals(name)) {
                success = true;
                authenticatedPrincipal = principal;
              }
            } catch (ClassCastException e) {
            } catch (InternalError e) {
              // XXX If the client principal doesn't exist, authenticate as the
              // XXX bottom principal. This is for ease of debugging so we don't
              // XXX need to keep editing client property files every time we
              // XXX re-create the client principal on the core.
              success = true;
            }

            return new Pair<Boolean, NodePrincipal>(success,
                authenticatedPrincipal);
          }
        });
  }

  private final class CallbackHandler implements
      ChannelMultiplexerThread.CallbackHandler {

    private final Session session;
    private final List<MessageHandlerThread> handlers;

    public CallbackHandler(Session session) {
      this.session = session;
      this.handlers = new ArrayList<MessageHandlerThread>();
    }

    public void connectionClosed() {
      for (MessageHandlerThread handler : handlers)
        handler.recycle();
      handlers.clear();
    }

    public void newStream(ChannelMultiplexerThread muxer, int streamID) {
      // Get a new message-handler thread and assign it to the new sub-stream.
      MessageHandlerThread handler = threadPool.get();
      handler.associateSession(session);
      handlers.add(handler);
      try {
        muxer.registerChannels(streamID, handler.source(), handler.sink());
      } catch (IOException e) {
        throw new InternalError(e);
      }
    }

    public void shutdown() {
      for (MessageHandlerThread handler : handlers)
        handler.interrupt();

      session.endSession();
    }
  }
}
