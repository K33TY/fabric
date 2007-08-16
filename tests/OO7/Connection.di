// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//
// $Id: Connection.di,v 1.3 2007-08-16 23:02:52 jed Exp $

package OO7;

public interface Connection {
  void setType(String x);

  String type();

  void setLength(long x);

  long length();

  void setFrom(AtomicPart x);

  AtomicPart from();

  void setTo(AtomicPart x);

  AtomicPart to();
}
