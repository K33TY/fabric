package fabric.util;

public interface Collection extends fabric.util.Iterable, fabric.lang.Object {

  boolean add(fabric.lang.Object o);

  boolean addAll(fabric.util.Collection c);

  void clear();

  boolean contains(fabric.lang.Object o);

  boolean containsAll(fabric.util.Collection c);

  @Override
  boolean equals(fabric.lang.Object o);

  @Override
  int hashCode();

  boolean isEmpty();

  @Override
  fabric.util.Iterator iterator(fabric.worker.Store store);

  boolean remove(fabric.lang.Object o);

  boolean removeAll(fabric.util.Collection c);

  boolean retainAll(fabric.util.Collection c);

  int size();

  fabric.lang.arrays.ObjectArray toArray();

  fabric.lang.arrays.ObjectArray toArray(fabric.lang.arrays.ObjectArray a);

  public static class _Proxy extends fabric.lang.Object._Proxy implements
      fabric.util.Collection {

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

    @Override
    native public fabric.util.Iterator iterator();

    public _Proxy(fabric.worker.Store store, long onum) {
      super(store, onum);
    }
  }

}
