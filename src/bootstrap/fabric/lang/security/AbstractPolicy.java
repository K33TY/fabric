package fabric.lang.security;

import fabric.common.VersionWarranty;

public interface AbstractPolicy extends fabric.lang.security.Policy,
    fabric.lang.Object {

  public fabric.lang.security.AbstractPolicy fabric$lang$security$AbstractPolicy$();

  @Override
  abstract public boolean equals(fabric.lang.Object that);

  @Override
  abstract public int hashCode();

  public static class _Proxy extends fabric.lang.Object._Proxy implements
      fabric.lang.security.AbstractPolicy {

    @Override
    native public fabric.lang.security.AbstractPolicy fabric$lang$security$AbstractPolicy$();

    @Override
    native public boolean equals(fabric.lang.Object arg1);

    @Override
    native public int hashCode();

    @Override
    native public boolean relabelsTo(fabric.lang.security.Policy arg1,
        java.util.Set arg2);

    public _Proxy(AbstractPolicy._Impl impl) {
      super(impl);
    }

    public _Proxy(fabric.worker.Store store, long onum) {
      super(store, onum);
    }
  }

  abstract public static class _Impl extends fabric.lang.Object._Impl implements
      fabric.lang.security.AbstractPolicy {

    @Override
    native public fabric.lang.security.AbstractPolicy fabric$lang$security$AbstractPolicy$();

    @Override
    abstract public boolean equals(fabric.lang.Object that);

    @Override
    abstract public int hashCode();

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
        VersionWarranty warranty, long label, long accessLabel,
        java.io.ObjectInput in, java.util.Iterator refTypes,
        java.util.Iterator intraStoreRefs, java.util.Iterator interStoreRefs)
        throws java.io.IOException, java.lang.ClassNotFoundException {
      super(store, onum, version, warranty, label, accessLabel, in, refTypes,
          intraStoreRefs, interStoreRefs);
    }
  }

  interface _Static extends fabric.lang.Object, Cloneable {
    final class _Proxy extends fabric.lang.Object._Proxy implements
        fabric.lang.security.AbstractPolicy._Static {

      public _Proxy(fabric.lang.security.AbstractPolicy._Static._Impl impl) {
        super(impl);
      }

      public _Proxy(fabric.worker.Store store, long onum) {
        super(store, onum);
      }
    }

    class _Impl extends fabric.lang.Object._Impl implements
        fabric.lang.security.AbstractPolicy._Static {

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
