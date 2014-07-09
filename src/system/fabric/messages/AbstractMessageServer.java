/**
 * Copyright (C) 2010-2014 Fabric project group, Cornell University
 *
 * This file is part of Fabric.
 *
 * Fabric is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * Fabric is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 */
package fabric.messages;

import static fabric.common.Logging.NETWORK_CONNECTION_LOGGER;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import fabric.common.Logging;
import fabric.common.Threading;
import fabric.common.exceptions.FabricException;
import fabric.common.exceptions.InternalError;
import fabric.common.net.RemoteIdentity;
import fabric.common.net.SubServerSocket;
import fabric.common.net.SubSocket;
import fabric.worker.remote.RemoteWorker;

/**
 * Abstracts a server loop that listens for and processes messages from the
 * network.
 */
public abstract class AbstractMessageServer implements Runnable, MessageHandler {

  public final String name;
  public final Logger logger;

  public AbstractMessageServer(String name, Logger logger) {
    this.name = name;
    this.logger = logger;
  }

  /**
   * Creates and returns a SubServerSocket for listening to requests.
   */
  protected abstract SubServerSocket createServerSocket();

  /**
   * The main execution body of a message processor node.
   */
  @Override
  public final void run() {
    SubServerSocket server = createServerSocket();

    try {
      // Start listening.
      server.bind(name);

      // The main server loop.
      while (true) {
        // Accept a connection and handle it.
        final SubSocket<RemoteWorker> connection = server.accept();

        Threading.getPool()
            .submit(
                new Threading.NamedRunnable(
                    "Fabric network message handler thread") {
                  @Override
                  protected void runImpl() {
                    RemoteIdentity<RemoteWorker> client;
                    final int streamID;
                    try {
                      client = connection.getRemoteIdentity();
                      streamID = connection.getStreamID();
                    } catch (IOException e) {
                      throw new InternalError(e);
                    }

                    try {
                      // Handle the connection.
                      DataInputStream in =
                          new DataInputStream(connection.getInputStream());
                      DataOutputStream out =
                          new DataOutputStream(connection.getOutputStream());

                      while (true) {
                        Message<?, ?> message = Message.receive(in);
                        try {
                          Message.Response response =
                              message.dispatch(client,
                                  AbstractMessageServer.this);
                          message.respond(out, response);
                        } catch (FabricException e) {
                          message.respond(out, e);
                        }

                        out.flush();
                      }
                    } catch (EOFException e) {
                      try {
                        connection.close();
                      } catch (IOException e1) {
                      }
                      Logging.log(NETWORK_CONNECTION_LOGGER, Level.INFO,
                          "Stream #{0} reset ({1})", streamID, client);
                    } catch (IOException e) {
                      try {
                        connection.close();
                      } catch (IOException e1) {
                      }
                      logger.log(Level.WARNING,
                          "Network error while handling request on stream #"
                              + streamID, e);
                    } catch (RuntimeException e) {
                      logger.log(Level.SEVERE,
                          "Message-handler thread for stream #" + streamID
                              + " exited with exception: " + e.getMessage(), e);
                    }
                  }
                });
      }
    } catch (final IOException e) {
      logger.log(Level.WARNING, name + " (" + getClass().getSimpleName()
          + ") suffered communications failure, shutting down", e);
    }
  }
}
