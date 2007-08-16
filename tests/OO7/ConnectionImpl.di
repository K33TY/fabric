// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//
// $Id: ConnectionImpl.di,v 1.1 2007-08-16 23:02:52 jed Exp $

package OO7;

public class ConnectionImpl implements Connection {
  String theType;
  long theLength;
  AtomicPart theFrom;
  AtomicPart theTo;

  public ConnectionImpl() {
    theType = new String("");
  }

  public void setType(String x) {
    theType = x;
  }

  public String type() {
    return theType;
  }

  public void setLength(long x) {
    theLength = x;
  }

  public long length() {
    return theLength;
  }

  public void setFrom(AtomicPart x) {
    theFrom = x;
  }

  public AtomicPart from() {
    return theFrom;
  }

  public void setTo(AtomicPart x) {
    theTo = x;
  }

  public AtomicPart to() {
    return theTo;
  }
}
