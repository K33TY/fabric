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
package fabric.util;

public interface Set extends fabric.util.Collection, fabric.lang.Object {

  @Override
  boolean add(fabric.lang.Object o);

  @Override
  boolean addAll(fabric.util.Collection c);

  @Override
  void clear();

  @Override
  boolean contains(fabric.lang.Object o);

  @Override
  boolean containsAll(fabric.util.Collection c);

  @Override
  boolean equals(fabric.lang.Object o);

  @Override
  int hashCode();

  @Override
  boolean isEmpty();

  @Override
  fabric.util.Iterator iterator(fabric.worker.Store store);

  @Override
  fabric.util.Iterator iterator();

  @Override
  boolean remove(fabric.lang.Object o);

  @Override
  boolean removeAll(fabric.util.Collection c);

  @Override
  boolean retainAll(fabric.util.Collection c);

  @Override
  int size();

  @Override
  fabric.lang.arrays.ObjectArray toArray();

  @Override
  fabric.lang.arrays.ObjectArray toArray(fabric.lang.arrays.ObjectArray a);

  public static class _Proxy extends fabric.lang.Object._Proxy implements
      fabric.util.Set {

    @Override
    native public boolean add(fabric.lang.Object arg1);

    @Override
    native public boolean addAll(fabric.util.Collection arg1);

    @Override
    native public void clear();

    @Override
    native public boolean contains(fabric.lang.Object arg1);

    @Override
    native public boolean containsAll(fabric.util.Collection arg1);

    @Override
    native public boolean equals(fabric.lang.Object arg1);

    @Override
    native public int hashCode();

    @Override
    native public boolean isEmpty();

    @Override
    native public fabric.util.Iterator iterator(fabric.worker.Store arg1);

    @Override
    native public fabric.util.Iterator iterator();

    @Override
    native public boolean remove(fabric.lang.Object arg1);

    @Override
    native public boolean removeAll(fabric.util.Collection arg1);

    @Override
    native public boolean retainAll(fabric.util.Collection arg1);

    @Override
    native public int size();

    @Override
    native public fabric.lang.arrays.ObjectArray toArray();

    @Override
    native public fabric.lang.arrays.ObjectArray toArray(
        fabric.lang.arrays.ObjectArray arg1);

    public _Proxy(fabric.worker.Store store, long onum) {
      super(store, onum);
    }
  }

}
