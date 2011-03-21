package fabric.lang.security;

import fabric.worker.Store;
import fabric.lang.Object;

public interface LabelUtil extends Object {
  
  public static class _Impl extends Object._Impl implements LabelUtil {

    protected _Impl(Store store, Label label) {
      super(store, label);
    }
    
    public static native boolean isReadableBy(Label lbl, Principal p);
    
    public static native boolean isWritableBy(Label lbl, Principal p);
    
    public static native ConfPolicy readerPolicy(Store store, Principal owner,
        Principal reader);
    
    public static native IntegPolicy writerPolicy(Store store, Principal owner,
        Principal writer);
    
    public static native Label toLabel(Store store, ConfPolicy confidPolicy,
        IntegPolicy integPolicy);
    
    public static native Label toLabel(Store store, ConfPolicy cPolicy);

    
    public static native Label join(Store store, Label l1, Label l2);
    
    public static native boolean relabelsTo(Label from, Label to);

    public static native boolean acts_for(Label actor, Principal granter);

    public static native boolean actsFor(Label actor, Principal granter);
  }
}
