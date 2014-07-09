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
package fabric.translate;

import jif.translate.ExprToJavaExt_c;
import jif.translate.JifToJavaRewriter;
import polyglot.ast.Expr;
import polyglot.types.SemanticException;
import polyglot.util.Position;
import fabil.ast.FabILNodeFactory;
import fabric.ast.RemoteWorkerGetter;

public class RemoteWorkerGetterToFabilExt_c extends ExprToJavaExt_c {
  /**
   * @throws SemanticException
   */
  @Override
  public Expr exprToJava(JifToJavaRewriter rw) throws SemanticException {
    FabILNodeFactory nf = (FabILNodeFactory) rw.java_nf();

    RemoteWorkerGetter rcg = (RemoteWorkerGetter) node();

    return nf.Call(rcg.position(), rw.qq().parseExpr("Worker.getWorker()"),
        nf.Id(Position.compilerGenerated(), "getWorker"),
        rcg.remoteWorkerName());
  }
}
