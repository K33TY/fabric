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
package fabric.visit;

import java.util.LinkedList;
import java.util.List;

import polyglot.ast.Block;
import polyglot.ast.ClassBody;
import polyglot.ast.ClassDecl;
import polyglot.ast.ClassMember;
import polyglot.ast.ConstructorCall;
import polyglot.ast.ConstructorDecl;
import polyglot.ast.MethodDecl;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.Stmt;
import polyglot.frontend.Job;
import polyglot.types.TypeSystem;
import polyglot.visit.NodeVisitor;

/**
 * Outputs a fabil skeleton for bootstrapping.
 */
public class FabILSkeletonCreator extends NodeVisitor {

  public FabILSkeletonCreator(Job job, TypeSystem ts, NodeFactory nf) {
  }

  @Override
  public Node leave(Node parent, Node old, Node n, NodeVisitor v) {
    if (n instanceof ClassBody) {
      List<ClassMember> members = ((ClassBody) n).members();

      List<ClassMember> stubmembers = new LinkedList<ClassMember>();
      for (ClassMember m : members) {
        if (m instanceof ClassDecl) {
          stubmembers.add(m);
        } else if (m instanceof MethodDecl) {
          MethodDecl pd = (MethodDecl) m;
          if (pd.body() != null) {
            pd = (MethodDecl) pd.body(null);
            // Make this method native if not already
            if (!pd.flags().isNative())
              pd = (MethodDecl) pd.flags(pd.flags().Native());
          }
          stubmembers.add(pd);
        } else if (m instanceof ConstructorDecl) {
          ConstructorDecl cd = (ConstructorDecl) m;
          Block b = cd.body();
          if (b != null && b.statements() != null
              && b.statements().get(0) instanceof ConstructorCall) {
            List<Stmt> l = new LinkedList<Stmt>();
            l.add(b.statements().get(0));
            cd = (ConstructorDecl) cd.body(b.statements(l));
          } else cd = (ConstructorDecl) cd.body(null);
          stubmembers.add(cd);
        }
      }

      return ((ClassBody) n).members(stubmembers);
    } else return n;
  }
}
