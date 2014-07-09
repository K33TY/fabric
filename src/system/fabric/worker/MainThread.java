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
package fabric.worker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import fabric.common.FabricThread;
import fabric.common.Logging;

/**
 * The thread in which the Fabric program executes. This is here to ensure that
 * the program executes in a FabricThread.
 */
public final class MainThread extends FabricThread.Impl {
  private final Method main;
  private final Object args;
  private Throwable uncaughtException;

  private MainThread(Method main, Object args) {
    super("Main worker application");
    this.main = main;
    this.args = args;
    this.uncaughtException = null;

    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        uncaughtException = e;
      }
    });
  }

  @Override
  public void run() {
    try {
      main.invoke(null, args);

    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      // Trim the stack trace to omit stuff dealing with the worker framework.
      List<StackTraceElement> trace = new ArrayList<StackTraceElement>();
      for (StackTraceElement elt : cause.getStackTrace())
        trace.add(elt);

      String mainClassName = main.getDeclaringClass().getName();

      for (ListIterator<StackTraceElement> it =
          trace.listIterator(trace.size()); it.hasPrevious();) {
        StackTraceElement elt = it.previous();
        if (elt.getClassName().equals(mainClassName)) break;
        it.remove();
      }

      StackTraceElement[] traceArray = new StackTraceElement[trace.size()];
      cause.setStackTrace(trace.toArray(traceArray));
      uncaughtException = cause;
    } catch (Throwable t) {
      uncaughtException = t;
    }
  }

  public static void invoke(Method main, Object args) throws Throwable {
    MainThread thread = new MainThread(main, args);
    thread.start();
    while (true) {
      try {
        thread.join();
        break;
      } catch (InterruptedException e) {
        Logging.logIgnoredInterruptedException(e);
      }
    }

    Throwable uncaught = thread.uncaughtException;
    if (uncaught != null) throw uncaught;
  }
}
