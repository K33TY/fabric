package fabric.common;

import fabric.lang.Object._Impl;
import fabric.worker.Store;

/**
 * Objects bundling a serialized object along with any runtime tokens (like
 * warranties or leases).
 */
public class SerializedObjectAndTokens {
  private SerializedObject serializedObject;
  private VersionWarranty warranty;
  private RWLease lease;

  public SerializedObjectAndTokens(SerializedObject object,
      VersionWarranty warranty, RWLease lease) {
    this.serializedObject = object;
    this.warranty = warranty;
    this.lease = lease;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SerializedObjectAndTokens)) return false;

    SerializedObjectAndTokens s = (SerializedObjectAndTokens) o;
    return equals(serializedObject, s.serializedObject)
        && equals(warranty, s.warranty) && equals(lease, s.lease);
  }

  private boolean equals(Object o1, Object o2) {
    if (o1 == o2) return true;
    if (o1 == null || o2 == null) return false;
    return o1.equals(o2);
  }

  @Override
  public int hashCode() {
    // This hash code implementation could probably be improved.
    return (serializedObject == null ? 0 : serializedObject.hashCode())
        ^ (warranty == null ? 0 : warranty.hashCode())
        ^ (lease == null ? 0 : lease.hashCode());
  }

  /**
   * @return the object
   */
  public SerializedObject getSerializedObject() {
    return serializedObject;
  }

  /**
   * @return the warranty
   */
  public VersionWarranty getWarranty() {
    return warranty;
  }

  /**
   * @param warranty the warranty to set
   *
   * Does <strong>not</strong> check if the new warranty is strictly newer than
   * the original warranty.
   */
  public void setWarranty(VersionWarranty warranty) {
    this.warranty = warranty;
  }

  /**
   * @return the lease
   */
  public RWLease getLease() {
    return lease;
  }

  /**
   * @param lease the lease to set
   *
   * Does <strong>not</strong> check if the new lease is strictly newer than the
   * original lease.
   */
  public void setLease(RWLease lease) {
    this.lease = lease;
  }

  /**
   * Get the deserialized version of the object with the current tokens.
   *
   * @param store Store this object comes from (same as argument for
   * {@link SerializedObject#deserialize}).
   */
  public _Impl getDeserializedObject(Store store) {
    return serializedObject.deserialize(store, warranty, lease);
  }

  /**
   * Get the deserialized version of the object with the current tokens.
   *
   * @param store Store this object comes from (same as argument for
   * {@link SerializedObject#deserialize}).
   * @param chaseSurrogates Store this object comes from (same as argument for
   * {@link SerializedObject#deserialize}).
   */
  public _Impl getDeserializedObject(Store store, boolean chaseSurrogates) {
    return serializedObject.deserialize(store, warranty, lease, chaseSurrogates);
  }
}
