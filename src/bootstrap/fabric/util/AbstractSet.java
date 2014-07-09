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

public interface AbstractSet extends fabric.util.Set,
    fabric.util.AbstractCollection {

  public fabric.util.AbstractSet fabric$util$AbstractSet$();

  @Override
  public boolean equals(fabric.lang.Object o);

  @Override
  public int hashCode();

  @Override
  public boolean removeAll(fabric.util.Collection c);

  @Override
  public fabric.util.Iterator iterator();

  public static class _Proxy extends fabric.util.AbstractCollection._Proxy
      implements fabric.util.AbstractSet {

    @Override
    native public fabric.util.AbstractSet fabric$util$AbstractSet$();

    @Override
    native public boolean equals(fabric.lang.Object arg1);

    @Override
    native public int hashCode();

    public _Proxy(AbstractSet._Impl impl) {
      super(impl);
    }

    public _Proxy(fabric.worker.Store store, long onum) {
      super(store, onum);
    }
  }

  abstract public static class _Impl extends
      fabric.util.AbstractCollection._Impl implements fabric.util.AbstractSet {

    @Override
    native public fabric.util.AbstractSet fabric$util$AbstractSet$();

    @Override
    native public boolean equals(fabric.lang.Object o);

    @Override
    native public int hashCode();

    @Override
    native public boolean removeAll(fabric.util.Collection c);

    @Override
    native public fabric.util.Iterator iterator();

    public _Impl(fabric.worker.Store $location) {
      super($location);
    }

    @Override
    native protected fabric.lang.Object._Proxy $makeProxy();

    @Override
    native public void $serialize(java.io.ObjectOutput out,
        java.util.List refTypes, java.util.List intraStoreRefs,
        java.util.List interStoreRefs) throws java.io.IOException;

    public _Impl(fabric.worker.Store store, long onum, int version,
        long expiry, long label, long accessLabel, java.io.ObjectInput in,
        java.util.Iterator refTypes, java.util.Iterator intraStoreRefs,
	java.util.Iterator interStoreRefs)
        throws java.io.IOException, java.lang.ClassNotFoundException {
      super(store, onum, version, expiry, label, accessLabel, in, refTypes,
          intraStoreRefs, interStoreRefs);
    }
  }

  interface _Static extends fabric.lang.Object, Cloneable {
    final class _Proxy extends fabric.lang.Object._Proxy implements
        fabric.util.AbstractSet._Static {

      public _Proxy(fabric.util.AbstractSet._Static._Impl impl) {
        super(impl);
      }

      public _Proxy(fabric.worker.Store store, long onum) {
        super(store, onum);
      }
    }

    class _Impl extends fabric.lang.Object._Impl implements
        fabric.util.AbstractSet._Static {

      public _Impl(fabric.worker.Store store)
          throws fabric.net.UnreachableNodeException {
        super(store);
      }

      @Override
      native protected fabric.lang.Object._Proxy $makeProxy();

      native private void $init();
    }

  }

}
