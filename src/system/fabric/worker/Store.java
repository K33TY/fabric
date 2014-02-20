package fabric.worker;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import fabric.common.SemanticWarranty;
import fabric.common.SerializedObject;
import fabric.common.TransactionID;
import fabric.common.VersionWarranty;
import fabric.common.exceptions.AccessException;
import fabric.common.util.LongKeyMap;
import fabric.common.util.Pair;
import fabric.lang.Object._Impl;
import fabric.lang.security.NodePrincipal;
import fabric.net.UnreachableNodeException;
import fabric.store.PrepareWritesResult;
import fabric.worker.memoize.CallInstance;
import fabric.worker.memoize.WarrantiedCallResult;
import fabric.worker.memoize.SemanticWarrantyRequest;

public interface Store extends Serializable {
  /**
   * Returns this store's host name.
   */
  public String name();

  /**
   * Returns the NodePrincipal associated with this store.
   */
  public NodePrincipal getPrincipal();

  /**
   * Determines whether this is the local store.
   */
  public boolean isLocalStore();

  /**
   * Notifies the store that the transaction is entering the write-prepare phase.
   * 
   * @return a minimum commit time.
   */
  PrepareWritesResult prepareTransactionWrites(long tid,
      Collection<_Impl> toCreate, Collection<_Impl> writes,
      Set<SemanticWarrantyRequest> calls) throws
    UnreachableNodeException, TransactionPrepareFailedException;

  /**
   * Notifies the store that the transaction is entering the read-prepare phase.
   * 
   * @return the set of new version warranties.
   */
  Pair<LongKeyMap<VersionWarranty>, Map<CallInstance, WarrantiedCallResult>>
    prepareTransactionReads(long tid, boolean readOnly, LongKeyMap<Integer>
        reads, Map<CallInstance, WarrantiedCallResult> calls, long commitTime)
    throws UnreachableNodeException, TransactionPrepareFailedException;

  /**
   * Returns the cache entry for the given onum. If the object is not resident,
   * it is fetched from the store via dissemination.
   * 
   * @param onum
   *          The identifier of the requested object
   * @return cache entry for the requested object.
   */
  ObjectCache.Entry readObject(long onum) throws AccessException;

  /**
   * Returns the cache entry for the requested object. If the object is not
   * resident, it is fetched directly from the store.
   * 
   * @param onum
   *          The identifier of the requested object
   * @return the cache entry for the requested object.
   */
  ObjectCache.Entry readObjectNoDissem(long onum) throws AccessException;

  /**
   * Returns the cache entry for the given onum.
   * 
   * @param onum
   *          The identifier of the requested object.
   * @return The entry if it exists in the object cache; otherwise, null.
   */
  ObjectCache.Entry readFromCache(long onum);

  /**
   * Returns the pair of SemanticWarranty and value for the given CallInstance.
   */
  WarrantiedCallResult lookupCall(CallInstance call);

  /**
   * Insert a CallResult into the CallCache.
   */
  void insertResult(CallInstance call, WarrantiedCallResult result);

  /**
   * Notifies the store that the transaction is being Aborted.
   * 
   * @param tid
   *          the ID of the aborting transaction. This is assumed to specify a
   *          top-level transaction.
   * @throws AccessException
   */
  void abortTransaction(TransactionID tid) throws AccessException;

  /**
   * Notifies the Store that the transaction should be committed.
   * 
   * @param transactionID
   *          the ID of the transaction to commit.
   * @param commitTime
   *          the time after which the commit should take effect.
   * @throws UnreachableNodeException
   * @throws TransactionCommitFailedException
   */
  void commitTransaction(long transactionID, long commitTime, boolean readOnly)
    throws UnreachableNodeException, TransactionCommitFailedException;

  /**
   * Determines whether the given set of objects are stale.
   * 
   * @return true iff stale objects were found.
   */
  boolean checkForStaleObjects(LongKeyMap<Integer> reads);

  /**
   * Obtains a new, unused object number from the Store.
   * 
   * @throws UnreachableNodeException
   */
  long createOnum() throws UnreachableNodeException;

  /**
   * Returns the root map of the Store
   */
  public fabric.util.Map getRoot();

  /**
   * Evicts the object with the given onum from cache.
   */
  public void evict(long onum);

  /**
   * Adds the given object to the cache.
   */
  public void cache(_Impl impl);

  /**
   * Adds the given object to the cache.
   * 
   * @return the resulting cache entry.
   */
  public ObjectCache.Entry cache(Pair<SerializedObject, VersionWarranty> obj);

  /**
   * XXX gross hack for nsdi deadline
   */
  public void addWarrantedRead(long onum);

}
