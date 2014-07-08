/**
 * Copyright (C) 2010-2013 Fabric project group, Cornell University
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
package fabric.extension;

import jif.extension.JifBranchExt;
import jif.translate.ToJavaExt;
import jif.types.JifContext;
import jif.types.JifTypeSystem;
import jif.types.PathMap;
import jif.visit.LabelChecker;
import polyglot.ast.Node;
import fabric.ast.RetryStmt;

public class RetryJifExt_c extends JifBranchExt {
  public RetryJifExt_c(ToJavaExt toJava) {
    super(toJava);
  }

  @Override
  public Node labelCheckStmt(LabelChecker lc) {
    RetryStmt retry = (RetryStmt) node();

    JifTypeSystem ts = lc.jifTypeSystem();
    JifContext A = lc.jifContext();
    A = (JifContext) retry.del().enterScope(A);

    PathMap X = ts.pathMap();
    // prevent the single path rule from being used.
    X = X.set(ts.gotoPath(retry.kind(), retry.label()), ts.topLabel());

    return updatePathMap(retry, X);
  }
}
