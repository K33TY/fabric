// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//

package OO7;

public class Connection {
  String type;
  long length;

  AtomicPart from;
  AtomicPart to;

  public Connection() {
    type = new String("");
  }

  public Connection(AtomicPart from, AtomicPart to) {
    setFrom(from);
    from.addFrom(this);
    setTo(to);
    to.addTo(this);
  }

  public void setType(String type) {
    this.type = type;
  }

  public String type() {
    return this.type;
  }

  public void setLength(long l) {
    this.length = l;
  }

  public long length() {
    return this.length;
  }

  public void setFrom(AtomicPart x) {
    this.from = x;
  }

  public AtomicPart from() {
    return this.from;
  }

  public void setTo(AtomicPart x) {
    this.to = x;
  }

  public AtomicPart to() {
    return this.to;
  }
}

/*
 * * vim: ts=2 sw=2 et cindent cino=\:0 syntax=java
 */
