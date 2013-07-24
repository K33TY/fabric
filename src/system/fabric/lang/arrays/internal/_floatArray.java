package fabric.lang.arrays.internal;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fabric.common.RefTypeEnum;
import fabric.common.util.Pair;
import fabric.lang.Object;
import fabric.lang.security.ConfPolicy;
import fabric.lang.security.Label;
import fabric.worker.Store;
import fabric.worker.transaction.TransactionManager;

public interface _floatArray extends Object {
  int get$length();

  float set(int i, float value);

  float get(int i);

  public static class _Impl extends Object._Impl implements _floatArray,
      _InternalArrayImpl {
    private float[] value;

    /**
     * Creates a new float array at the given Store with the given length.
     * 
     * @param store
     *          The store on which to allocate the array.
     * @param length
     *          The length of the array.
     */
    public _Impl(Store store, Label updateLabel, ConfPolicy accessPolicy,
        int length) {
      this(store, updateLabel, accessPolicy, new float[length]);
    }

    /**
     * Creates a new float array at the given Store using the given backing
     * array.
     * 
     * @param store
     *          The store on which to allocate the array.
     * @param value
     *          The backing array to use.
     */
    public _Impl(Store store, Label updateLabel, ConfPolicy accessPolicy,
        float[] value) {
      super(store);
      this.value = value;

      set$$updateLabel(updateLabel);
      set$$accessPolicy(accessPolicy);
    }

    /**
     * Used for deserializing.
     */
    public _Impl(Store store, long onum, int version, long expiry, long label,
        long accessLabel, ObjectInput in, Iterator<RefTypeEnum> refTypes,
        Iterator<Long> intraStoreRefs) throws IOException,
        ClassNotFoundException {
      super(store, onum, version, expiry, label, accessLabel, in, refTypes,
          intraStoreRefs);
      value = new float[in.readInt()];
      for (int i = 0; i < value.length; i++)
        value[i] = in.readFloat();
    }

    @Override
    public int get$length() {
      TransactionManager.getInstance().registerRead(this);
      return value.length;
    }

    @Override
    public float get(int i) {
      TransactionManager.getInstance().registerRead(this);
      return this.value[i];
    }

    @Override
    public float set(int i, float value) {
      boolean transactionCreated =
          TransactionManager.getInstance().registerWrite(this);
      float result = this.value[i] = value;
      if (transactionCreated)
        TransactionManager.getInstance().commitTransaction();
      return result;
    }

    @Override
    public void $copyAppStateFrom(Object._Impl other) {
      super.$copyAppStateFrom(other);
      _floatArray._Impl src = (_floatArray._Impl) other;
      value = src.value;
    }

    @Override
    public void cloneValues() {
      value = value.clone();
    }

    @Override
    protected _floatArray._Proxy $makeProxy() {
      return new _floatArray._Proxy(this);
    }

    @Override
    public void $serialize(ObjectOutput out, List<RefTypeEnum> refTypes,
        List<Long> intraStoreRefs, List<Pair<String, Long>> interStoreRefs)
        throws IOException {
      super.$serialize(out, refTypes, intraStoreRefs, interStoreRefs);
      out.writeInt(value.length);
      for (float element : value)
        out.writeFloat(element);
    }

    @Override
    public Object $initLabels() {
      return $getProxy();
    }

    public _floatArray._Impl $makeSemiDeepCopy(_floatArray._Impl copy,
        Map<Long, Object> oldSet, Map<Long, Object> oldToNew) {
      oldToNew.put(this.$getOnum(), copy);
      copy.value = new float[this.value.length];
      for (int i = 0; i < this.value.length; i++) {
        copy.value[i] = this.value[i];
      }
      return copy;
    }
  }

  public static class _Proxy extends Object._Proxy implements _floatArray {

    public _Proxy(Store store, long onum) {
      super(store, onum);
    }

    public _Proxy(_floatArray._Impl impl) {
      super(impl);
    }

    @Override
    public int get$length() {
      return ((_floatArray) fetch()).get$length();
    }

    @Override
    public float get(int i) {
      return ((_floatArray) fetch()).get(i);
    }

    @Override
    public float set(int i, float value) {
      return ((_floatArray) fetch()).set(i, value);
    }
  }
}
