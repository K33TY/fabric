package fabric.lang.security;

import fabric.common.RWLease;
import fabric.common.VersionWarranty;

public interface JoinIntegPolicy extends fabric.lang.security.IntegPolicy,
    fabric.lang.security.JoinPolicy {

  @Override
  public fabric.lang.security.IntegPolicy join(fabric.worker.Store store,
      fabric.lang.security.IntegPolicy p, java.util.Set s);

  @Override
  public fabric.lang.security.IntegPolicy join(
      fabric.lang.security.IntegPolicy p, java.util.Set s);

  @Override
  public fabric.lang.security.IntegPolicy join(fabric.worker.Store store,
      fabric.lang.security.IntegPolicy p);

  @Override
  public fabric.lang.security.IntegPolicy join(
      fabric.lang.security.IntegPolicy p);

  @Override
  public fabric.lang.security.IntegPolicy meet(fabric.worker.Store store,
      fabric.lang.security.IntegPolicy p, java.util.Set s);

  @Override
  public fabric.lang.security.IntegPolicy meet(
      fabric.lang.security.IntegPolicy p, java.util.Set s);

  @Override
  public fabric.lang.security.IntegPolicy meet(fabric.worker.Store store,
      fabric.lang.security.IntegPolicy p);

  @Override
  public fabric.lang.security.IntegPolicy meet(
      fabric.lang.security.IntegPolicy p);

  public static class _Proxy extends fabric.lang.security.JoinPolicy._Proxy
      implements fabric.lang.security.JoinIntegPolicy {

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.worker.Store arg1, fabric.lang.security.IntegPolicy arg2,
        java.util.Set arg3);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.lang.security.IntegPolicy arg1, java.util.Set arg2);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.worker.Store arg1, fabric.lang.security.IntegPolicy arg2);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.lang.security.IntegPolicy arg1);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.worker.Store arg1, fabric.lang.security.IntegPolicy arg2,
        java.util.Set arg3);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.lang.security.IntegPolicy arg1, java.util.Set arg2);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.worker.Store arg1, fabric.lang.security.IntegPolicy arg2);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.lang.security.IntegPolicy arg1);

    public _Proxy(JoinIntegPolicy._Impl impl) {
      super(impl);
    }

    public _Proxy(fabric.worker.Store store, long onum) {
      super(store, onum);
    }
  }

  final public static class _Impl extends fabric.lang.security.JoinPolicy._Impl
      implements fabric.lang.security.JoinIntegPolicy {

    _Impl(fabric.worker.Store $location, fabric.util.Set policies) {
      super($location, policies);
    }

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.worker.Store store, fabric.lang.security.IntegPolicy p,
        java.util.Set s);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.lang.security.IntegPolicy p, java.util.Set s);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.worker.Store store, fabric.lang.security.IntegPolicy p);

    @Override
    native public fabric.lang.security.IntegPolicy join(
        fabric.lang.security.IntegPolicy p);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.worker.Store store, fabric.lang.security.IntegPolicy p,
        java.util.Set s);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.lang.security.IntegPolicy p, java.util.Set s);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.worker.Store store, fabric.lang.security.IntegPolicy p);

    @Override
    native public fabric.lang.security.IntegPolicy meet(
        fabric.lang.security.IntegPolicy p);

    @Override
    native protected fabric.lang.Object._Proxy $makeProxy();

    @Override
    native public void $serialize(java.io.ObjectOutput out,
        java.util.List refTypes, java.util.List intraStoreRefs,
        java.util.List interStoreRefs) throws java.io.IOException;

    public _Impl(fabric.worker.Store store, long onum, int version,
        VersionWarranty warranty, RWLease lease, long label, long accessLabel,
        java.io.ObjectInput in, java.util.Iterator refTypes,
        java.util.Iterator intraStoreRefs, java.util.Iterator interStoreRefs)
        throws java.io.IOException, java.lang.ClassNotFoundException {
      super(store, onum, version, warranty, lease, label, accessLabel, in,
          refTypes, intraStoreRefs, interStoreRefs);
    }
  }

  interface _Static extends fabric.lang.Object, Cloneable {
    final class _Proxy extends fabric.lang.Object._Proxy implements
        fabric.lang.security.JoinIntegPolicy._Static {

      public _Proxy(fabric.lang.security.JoinIntegPolicy._Static._Impl impl) {
        super(impl);
      }

      public _Proxy(fabric.worker.Store store, long onum) {
        super(store, onum);
      }
    }

    class _Impl extends fabric.lang.Object._Impl implements
        fabric.lang.security.JoinIntegPolicy._Static {

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
