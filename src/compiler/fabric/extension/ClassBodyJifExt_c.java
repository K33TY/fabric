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
package fabric.extension;

import java.util.ArrayList;
import java.util.List;

import jif.extension.JifClassBodyExt;
import jif.translate.ToJavaExt;
import jif.types.JifContext;
import jif.types.JifTypeSystem;
import jif.visit.LabelChecker;
import polyglot.ast.ClassBody;
import polyglot.ast.ClassMember;
import polyglot.ast.Node;
import polyglot.types.SemanticException;
import fabric.types.SilenceableSolverGLB;

public class ClassBodyJifExt_c extends JifClassBodyExt {
  protected List<ClassMember> remote_wrappers;

  public ClassBodyJifExt_c(ToJavaExt toJava) {
    super(toJava);
    this.remote_wrappers = new ArrayList<ClassMember>();
  }

  @Override
  public Node labelCheck(LabelChecker lc) {
    ClassBody n = (ClassBody) node();

    JifTypeSystem jts = lc.typeSystem();

    JifContext A = lc.context();
    A = (JifContext) n.del().enterScope(A);
    A.setCurrentCodePCBound(jts.notTaken());
    lc = lc.context(A);

    List<ClassMember> members = new ArrayList<ClassMember>();
    // label check each member, but mute reporting of errors on
    // remote wrappers.
    for (ClassMember cm : n.members()) {
      try {
        ClassMember toAdd = (ClassMember) lc.context(A).labelCheck(cm);
        if (toAdd != null) {
          members.add(toAdd);
        }
      } catch (SemanticException e) {
        // report it and keep going.
        lc.reportSemanticException(e);
      }
    }

    List<ClassMember> new_wrappers = new ArrayList<ClassMember>();
    SilenceableSolverGLB.mute(true);
    for (ClassMember cm : remoteWrappers()) {
      try {
        ClassMember toAdd = (ClassMember) lc.context(A).labelCheck(cm);
        if (toAdd != null) {
          new_wrappers.add(toAdd);
        }
      } catch (SemanticException e) {
        // report it and keep going.
        lc.reportSemanticException(e);
      }
    }
    SilenceableSolverGLB.mute(false);

    setRemoteWrappers(new_wrappers);
    return n.members(members);
  }

  public List<ClassMember> remoteWrappers() {
    return remote_wrappers;
  }

  public void setRemoteWrappers(List<ClassMember> remote_wrappers) {
    this.remote_wrappers = remote_wrappers;
  }
}
