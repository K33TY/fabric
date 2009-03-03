package fabric.lang;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;

import jif.lang.*;
import fabric.client.Client;
import fabric.client.Core;
import fabric.client.UnreachableNodeException;
import fabric.common.Pair;
import fabric.common.RefTypeEnum;

/**
 * This is implemented in Java so that the constructor can provide default
 * labels so that a Principal p can be labelled with {p→_; p←p}.
 */
public interface Principal extends Object {

  String name();

  boolean delegatesTo(final Principal p);

  boolean equals(final Principal p);

  boolean isAuthorized(final java.lang.Object authPrf, final Closure closure,
      final Label lb, final boolean executeNow);

  ActsForProof findProofUpto(final Core core, final Principal p,
      final java.lang.Object searchState);

  ActsForProof findProofDownto(final Core core, final Principal q,
      final java.lang.Object searchState);

  public static class $Proxy extends Object.$Proxy implements
      Principal {

    public $Proxy(Principal.$Impl impl) {
      super(impl);
    }

    public $Proxy(fabric.client.Core core, long onum) {
      super(core, onum);
    }

    public String name() {
      return ((Principal) fetch()).name();
    }

    public boolean delegatesTo(Principal p) {
      return ((Principal) fetch()).delegatesTo(p);
    }

    public boolean equals(Principal p) {
      return ((Principal) fetch()).equals(p);
    }

    public boolean isAuthorized(java.lang.Object authPrf, Closure closure,
        Label lb, boolean executeNow) {
      return ((Principal) fetch()).isAuthorized(authPrf, closure, lb,
          executeNow);
    }

    public ActsForProof findProofUpto(Core core, Principal p,
        java.lang.Object searchState) {
      return ((Principal) fetch()).findProofUpto(core, p, searchState);
    }

    public ActsForProof findProofDownto(Core core, Principal q,
        java.lang.Object searchState) {
      return ((Principal) fetch()).findProofDownto(core, q, searchState);
    }
  }

  abstract public static class $Impl extends Object.$Impl implements
      Principal {

    public $Impl(Core location, Label label) {
      // If the given label is null, temporarily create the object with an
      // overly restrictive label.
      super(location, label == null ? Client.getClient().getLocalCore()
          .getPublicReadonlyLabel() : label);

      if (label == null) {
        // Replace the temporary label with {this <- this}.
        ConfPolicy bottomConf =
            Client.getClient().getLocalCore().getBottomConfidPolicy();
        Principal.$Proxy thisProxy = (Principal.$Proxy) this.$getProxy();
        IntegPolicy integ =
            LabelUtil.$Impl.writerPolicy(location, thisProxy, thisProxy);
        this.$label = LabelUtil.$Impl.toLabel(location, bottomConf, integ);
      }
    }

    abstract public String name();

    abstract public boolean delegatesTo(final Principal p);

    abstract public boolean equals(final Principal p);

    abstract public boolean isAuthorized(final java.lang.Object authPrf,
        final Closure closure, final Label lb, final boolean executeNow);

    abstract public ActsForProof findProofUpto(final Core core,
        final Principal p, final java.lang.Object searchState);

    abstract public ActsForProof findProofDownto(final Core core,
        final Principal q, final java.lang.Object searchState);

    @Override
    protected Object.$Proxy $makeProxy() {
      return new Principal.$Proxy(this);
    }

    @Override
    public void $serialize(ObjectOutput out, List<RefTypeEnum> refTypes,
        List<Long> intracoreRefs, List<Pair<String, Long>> intercoreRefs)
        throws IOException {
      super.$serialize(out, refTypes, intracoreRefs, intercoreRefs);
    }

    public $Impl(Core core, long onum, int version, long expiry, long label,
        ObjectInput in, Iterator<RefTypeEnum> refTypes,
        Iterator<Long> intracoreRefs) throws java.io.IOException,
        ClassNotFoundException {
      super(core, onum, version, expiry, label, in, refTypes, intracoreRefs);
    }
  }

  interface $Static extends Object, Cloneable {
    final class $Proxy extends Object.$Proxy implements
        Principal.$Static {

      public $Proxy(Principal.$Static.$Impl impl) {
        super(impl);
      }

      public $Proxy(Core core, long onum) {
        super(core, onum);
      }

      final public static Principal.$Static $instance;

      static {
        Principal.$Static.$Impl impl =
            (Principal.$Static.$Impl) Object.$Static.$Proxy
                .$makeStaticInstance(Principal.$Static.$Impl.class);
        $instance = (Principal.$Static) impl.$getProxy();
        impl.$init();
      }
    }

    class $Impl extends Object.$Impl implements
        fabric.lang.Principal.$Static {

      public $Impl(Core core, Label label) throws UnreachableNodeException {
        super(core, label);
      }

      @Override
      protected Object.$Proxy $makeProxy() {
        return new fabric.lang.Principal.$Static.$Proxy(this);
      }

      private void $init() {
      }
    }
  }
}
