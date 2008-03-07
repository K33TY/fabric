// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//
// $Id: ComplexAssembly.java,v 1.2 2008-03-07 19:46:24 jed Exp $

package OO7;

import OO7.traversals.Traversal;

import java.util.Collection;
import java.util.LinkedList;

public class ComplexAssembly extends Assembly {
  LinkedList subAssemblies;

  public ComplexAssembly(Benchmark db) {
    super(db);

    subAssemblies = new LinkedList();
  }

  public void addSubAssembly(Assembly x) {
    subAssemblies.addLast(x);
  }

  public Collection subAssemblies() {
    return subAssemblies;
  }

  public void accept(Traversal t) {
    t.visitComplexAssembly(this);
  }
}

/*
 * * vim: ts=2 sw=2 et cindent cino=\:0 syntax=java
 */
