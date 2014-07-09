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
package fabric.parse;

import java.net.URI;

import jif.parse.Amb;
import polyglot.ast.Id;
import polyglot.ast.PackageNode;
import polyglot.ast.QualifierNode;
import polyglot.ast.TypeNode;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import codebases.ast.CodebaseNode;
import codebases.ast.CodebaseNodeFactory;
import codebases.types.CodebaseTypeSystem;
import fabric.ast.FabricNodeFactory;

public class Name extends jif.parse.Name {
  public final CodebaseTypeSystem ts;
  public final URI ns;

  public Name(Grm parser, Position pos, Id name) throws Exception {
    this(parser, pos, name.id());
  }

  public Name(Grm parser, Position pos, String name) throws Exception {
    this(parser, pos, null, name);
  }

  public Name(Grm parser, Position pos, Amb prefix, Id name) throws Exception {
    this(parser, pos, prefix, name.id());
  }

  public Name(Grm parser, Position pos, Amb prefix, String name)
      throws Exception {
    super(parser, pos, prefix, name);
    this.ns = parser.ns;
    this.ts = parser.ts;
  }

  @Override
  // This should only be used for the package declaration
  public PackageNode toPackage() throws Exception {
    return (PackageNode) toQualifier();
  }

  @Override
  public TypeNode toType() throws Exception {
    if (prefix == null) {
      return parser.nf.AmbTypeNode(pos, parser.nf.Id(pos, name));
    }

    return parser.nf.AmbTypeNode(pos, ((Name) prefix).toQualifier(),
        parser.nf.Id(pos, name));
  }

  @Override
  public QualifierNode toQualifier() throws Exception {
    if (prefix == null) {
      FabricNodeFactory nf = (FabricNodeFactory) parser.nf;
      URI cb = ts.namespaceResolver(ns).resolveCodebaseName(name);
      if (cb != null) {
        return nf.CodebaseNode(pos, ns, name, cb);
      } else {
        return parser.nf
            .PackageNode(pos, ts.createPackage(this.ns, null, name));
      }
    } else {
      Name p = (Name) prefix;
      QualifierNode qn = p.toQualifier();
      if (qn instanceof CodebaseNode) {
        CodebaseNode cn = (CodebaseNode) qn;
        CodebaseNodeFactory nf = (CodebaseNodeFactory) parser.nf;
        return nf.CodebaseNode(pos, ns, cn.alias(), cn.externalNamespace(),
            ts.createPackage(cn.externalNamespace(), null, name));
      } else if (qn instanceof PackageNode) {
        PackageNode pn = (PackageNode) qn;
        return parser.nf.PackageNode(pos,
            ts.createPackage(this.ns, pn.package_(), name));
      } else {
        throw new InternalCompilerError("Unexpected qualifier " + qn);
      }
    }
  }

}
