// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//
// $Id: Document.java,v 1.2 2008-03-07 19:46:24 jed Exp $

package OO7;

public class Document {
  String title;
  int id;
  char[] text;

  CompositePart part;

  public Document(Benchmark db, int size) {
    this.id = db.newId();
    db.documentsById.put(new Integer(id()), this);

    // TODO: generate different strings, and index
    this.text = new char[size];
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String title() {
    return this.title;
  }

  public int id() {
    return this.id;
  }

  public void setText(char[] text) {
    this.text = text;
  }

  public char[] text() {
    return this.text;
  }
}

/*
 * * vim: ts=2 sw=2 et cindent cino=\:0 syntax=java
 */
