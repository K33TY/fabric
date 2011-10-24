package fabric.lang.arrays.internal;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;

import fabric.common.RefTypeEnum;
import fabric.common.util.Pair;
import fabric.lang.Object;
import fabric.lang.security.Label;
import fabric.worker.Store;
import fabric.worker.transaction.TransactionManager;

public interface _byteArray extends Object {
  int get$length();

  byte set(int i, byte value);

  byte get(int i);

  public static class _Impl extends Object._Impl implements _byteArray,
      _InternalArrayImpl {
    private byte[] value;

    /**
     * Creates a new byte array at the given Store with the given length.
     * 
     * @param store
     *                The store on which to allocate the array.
     * @param length
     *                The length of the array.
     */
    public _Impl(Store store, Label label, Label accessLabel, int length) {
      this(store, label, accessLabel, new byte[length]);
    }

    /**
     * Creates a new byte array at the given Store using the given backing
     * array.
     * 
     * @param store
     *                The store on which to allocate the array.
     * @param value
     *                The backing array to use.
     */
    public _Impl(Store store, Label label, Label accessLabel, byte[] value) {
      super(store, label, accessLabel);
      this.value = value;
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
      value = new byte[in.readInt()];
      for (int i = 0; i < value.length; i++)
        value[i] = in.readByte();
    }

    @Override
    public int get$length() {
      TransactionManager.getInstance().registerRead(this);
      return value.length;
    }

    @Override
    public byte get(int i) {
      TransactionManager.getInstance().registerRead(this);
      return this.value[i];
    }

    @Override
    public byte set(int i, byte value) {
      boolean transactionCreated =
          TransactionManager.getInstance().registerWrite(this);
      byte result = this.value[i] = value;
      if (transactionCreated) TransactionManager.getInstance().commitTransaction();
      return result;
    }

    @Override
    public void $copyAppStateFrom(Object._Impl other) {
      super.$copyAppStateFrom(other);
      _byteArray._Impl src = (_byteArray._Impl) other;
      value = src.value;
    }

    @Override
    public void cloneValues() {
      value = value.clone();
    }

    @Override
    protected _byteArray._Proxy $makeProxy() {
      return new _byteArray._Proxy(this);
    }

    @Override
    public void $serialize(ObjectOutput out, List<RefTypeEnum> refTypes,
        List<Long> intraStoreRefs, List<Pair<String, Long>> interStoreRefs)
        throws IOException {
      super.$serialize(out, refTypes, intraStoreRefs, interStoreRefs);
      out.writeInt(value.length);
      for (int i = 0; i < value.length; i++)
        out.writeByte(value[i]);
    }
  }

  public static class _Proxy extends Object._Proxy implements _byteArray {

    public _Proxy(Store store, long onum) {
      super(store, onum);
    }

    public _Proxy(_byteArray._Impl impl) {
      super(impl);
    }

    @Override
    public int get$length() {
      return ((_byteArray) fetch()).get$length();
    }

    @Override
    public byte get(int i) {
      return ((_byteArray) fetch()).get(i);
    }

    @Override
    public byte set(int i, byte value) {
      return ((_byteArray) fetch()).set(i, value);
    }
  }
}
