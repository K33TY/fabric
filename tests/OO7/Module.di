// You can redistribute this software and/or modify it under the terms of
// the Ozone Core License version 1 published by ozone-db.org.
//
// The original code and portions created by Thorsten Fiebig are
// Copyright (C) 2000-@year@ by Thorsten Fiebig. All rights reserved.
// Code portions created by SMB are
// Copyright (C) 1997-@year@ by SMB GmbH. All rights reserved.
//
// $Id: Module.di,v 1.3 2007-08-16 23:02:52 jed Exp $

package OO7;

import java.util.Collection;

public interface Module extends DesignObject {

  void setManual(Manual x);

  Manual manual();

  void addAssembly(Assembly x);

  Collection assembly();

  void setDesignRoot(ComplexAssembly x);

  ComplexAssembly designRoot();
}
