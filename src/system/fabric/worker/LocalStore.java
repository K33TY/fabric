package fabric.worker;

import static fabric.common.Logging.WORKER_LOCAL_STORE_LOGGER;
import static fabric.common.Logging.SEMANTIC_WARRANTY_LOGGER;

import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import fabric.common.Logging;
import fabric.common.ONumConstants;
import fabric.common.SemanticWarranty;
import fabric.common.SerializedObject;
import fabric.common.TransactionID;
import fabric.common.VersionWarranty;
import fabric.common.exceptions.InternalError;
import fabric.common.util.LongKeyHashMap;
import fabric.common.util.LongKeyMap;
import fabric.common.util.Pair;
import fabric.lang.Object;
import fabric.lang.Object._Impl;
import fabric.lang.security.ConfPolicy;
import fabric.lang.security.IntegPolicy;
import fabric.lang.security.Label;
import fabric.lang.security.LabelUtil;
import fabric.lang.security.NodePrincipal;
import fabric.lang.security.Principal;
import fabric.lang.security.PrincipalUtil.TopPrincipal;
import fabric.store.PrepareWritesResult;
import fabric.util.HashMap;
import fabric.util.Map;
import fabric.worker.memoize.CallCache;
import fabric.worker.memoize.CallInstance;
import fabric.worker.memoize.WarrantiedCallResult;
import fabric.worker.memoize.SemanticWarrantyRequest;
import fabric.worker.transaction.Log;
import fabric.worker.transaction.TransactionManager;

public final class LocalStore implements Store, Serializable {

  private long freshOID = ONumConstants.FIRST_UNRESERVED;

  private Map rootMap;
  private Principal topPrincipal;
  private ConfPolicy topConfidPolicy;
  private ConfPolicy bottomConfidPolicy;
  private IntegPolicy topIntegPolicy;
  private IntegPolicy bottomIntegPolicy;
  private Label emptyLabel;
  private Label publicReadonlyLabel;

  /**
   * Only used to obtain ObjectCache.Entry objects so we can satisfy the
   * contract for readFromCache(long).
   */
  private final ObjectCache cache;

  /**
   * Used to obtain locally stored call results
   */
  private final CallCache callCache;

  private Set<Pair<Principal, Principal>> localDelegates;

  @Override
  public PrepareWritesResult prepareTransactionWrites(long tid,
      Collection<Object._Impl> toCreate, Collection<Object._Impl> writes,
      Set<SemanticWarrantyRequest> calls) {
    // TODO: Currently we don't handle local memoized calls.
    WORKER_LOCAL_STORE_LOGGER.fine("Local transaction preparing writes");
    return new PrepareWritesResult(0, null);
  }

  @Override
  public Pair<LongKeyMap<VersionWarranty>, java.util.Map<CallInstance,
         SemanticWarranty>> prepareTransactionReads(long tid,
             boolean readOnly, LongKeyMap<Integer> reads,
             java.util.Map<CallInstance, WarrantiedCallResult> calls, long
             commitTime) {
    // Note: since we assume local single threading we can ignore reads
    // (conflicts are impossible)
    WORKER_LOCAL_STORE_LOGGER.fine("Local transaction preparing reads");
    return new Pair<LongKeyMap<VersionWarranty>, java.util.Map<CallInstance,
         SemanticWarranty>>(EMPTY_VERSION_WARRANTY_MAP, new
             java.util.HashMap<CallInstance, SemanticWarranty>());
  }

  private static final LongKeyMap<VersionWarranty> EMPTY_VERSION_WARRANTY_MAP =
      new LongKeyHashMap<VersionWarranty>();

  @Override
  public void abortTransaction(TransactionID tid) {
    WORKER_LOCAL_STORE_LOGGER.fine("Local transaction aborting");
  }

  @Override
  public void commitTransaction(long transactionID, long commitTime,
      boolean readOnly) {
    WORKER_LOCAL_STORE_LOGGER.fine("Local transaction committing");
  }

  @Override
  public synchronized long createOnum() {
    return freshOID++;
  }

  @Override
  public ObjectCache.Entry readObject(long onum) {
    return readObjectNoDissem(onum);
  }

  @Override
  public ObjectCache.Entry readObjectNoDissem(long onum) {
    return readImplNoDissem(onum).$cacheEntry;
  }

  private _Impl readImplNoDissem(long onum) {
    if (!ONumConstants.isGlobalConstant(onum))
      throw new InternalError("Not supported.");

    if (Integer.MIN_VALUE <= onum && onum <= Integer.MAX_VALUE) {
      switch ((int) onum) {
      case ONumConstants.TOP_PRINCIPAL:
        return (_Impl) topPrincipal.fetch();

      case ONumConstants.TOP_CONFIDENTIALITY:
        return (_Impl) topConfidPolicy.fetch();

      case ONumConstants.BOTTOM_CONFIDENTIALITY:
        return (_Impl) bottomConfidPolicy.fetch();

      case ONumConstants.TOP_INTEGRITY:
        return (_Impl) topIntegPolicy.fetch();

      case ONumConstants.BOTTOM_INTEGRITY:
        return (_Impl) bottomIntegPolicy.fetch();

      case ONumConstants.EMPTY_LABEL:
        return (_Impl) emptyLabel.fetch();

      case ONumConstants.PUBLIC_READONLY_LABEL:
        return (_Impl) publicReadonlyLabel.fetch();
      }
    }

    throw new InternalError("Unknown global constant: onum " + onum);
  }

  @Override
  public ObjectCache.Entry readFromCache(long onum) {
    return cache.get(onum);
  }

  @Override
  public WarrantiedCallResult lookupCall(CallInstance call) {
    Logging.log(SEMANTIC_WARRANTY_LOGGER, Level.FINEST,
        "Looking up call id: {0}", call);

    WarrantiedCallResult result = null;
    Log cur = TransactionManager.getInstance().getCurrentLog();
    if (cur != null) {
      result = cur.getRequestResult(call);
    }
    if (result == null) result = callCache.get(call);
    
    // TODO: actually check the store itself.
    Logging.log(SEMANTIC_WARRANTY_LOGGER, Level.FINEST,
        "Call {0} found in local store: {1}", call, result);
    return result;
  }

  @Override
  public void insertResult(CallInstance call, WarrantiedCallResult result) {
    Logging.log(SEMANTIC_WARRANTY_LOGGER, Level.FINEST,
        "Putting call id: {0} -> {1}", call, result.getValue());
    callCache.put(call, result);
  }

  @Override
  public boolean checkForStaleObjects(LongKeyMap<Integer> reads) {
    return false;
  }

  /**
   * The singleton LocalStore object is managed by the Worker class.
   * 
   * @see fabric.worker.Worker.getLocalStore
   */
  protected LocalStore() {
    this.cache = new ObjectCache(name());
    this.callCache = new CallCache();
  }

  @Override
  public String toString() {
    return "LocalStore";
  }

  @Override
  public Map getRoot() {
    return rootMap;
  }

  public void addLocalDelegation(Principal p, Principal q) {
    localDelegates.add(new Pair<Principal, Principal>(p, q));
  }

  public void removeLocalDelegation(Principal p, Principal q) {
    localDelegates.remove(new Pair<Principal, Principal>(p, q));
  }

  public boolean localDelegatesTo(Principal p, Principal q) {
    return localDelegates.contains(new Pair<Principal, Principal>(p, q));
  }

  public Principal getTopPrincipal() {
    return topPrincipal;
  }

  public ConfPolicy getTopConfidPolicy() {
    return topConfidPolicy;
  }

  public ConfPolicy getBottomConfidPolicy() {
    return bottomConfidPolicy;
  }

  public IntegPolicy getTopIntegPolicy() {
    return topIntegPolicy;
  }

  public IntegPolicy getBottomIntegPolicy() {
    return bottomIntegPolicy;
  }

  public Label getEmptyLabel() {
    return emptyLabel;
  }

  public Label getPublicReadonlyLabel() {
    return publicReadonlyLabel;
  }

  @Override
  public String name() {
    return "local";
  }

  @Override
  public NodePrincipal getPrincipal() {
    return Worker.getWorker().getPrincipal();
  }

  @Override
  public boolean isLocalStore() {
    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public void evict(long onum) {
    // nothing to do
  }

  @Override
  public void cache(_Impl impl) {
    // nothing to do
  }

  @Override
  public ObjectCache.Entry cache(Pair<SerializedObject, VersionWarranty> obj) {
    throw new InternalError(
        "Unexpected attempt to cache a serialized local-store object.");
  }

  public void initialize() {
    // Bootstrap labels with some proxies. Any remaining references to these
    // proxies will be resolved by the hack in readObject().
    this.emptyLabel =
        new Label._Proxy(LocalStore.this, ONumConstants.EMPTY_LABEL);

    this.publicReadonlyLabel =
        new Label._Proxy(LocalStore.this, ONumConstants.PUBLIC_READONLY_LABEL);

    Worker.runInSubTransaction(new Worker.Code<Void>() {
      @Override
      @SuppressWarnings("deprecation")
      public Void run() {
        // Create global constant objects. We force renumbering on these because
        // references to these objects may leak to remote stores. (This leakage
        // is permitted since these objects are constant and are at well-known
        // onums.)

        // Create the object representing the top principal.
        topPrincipal =
            new TopPrincipal._Impl(LocalStore.this)
                .fabric$lang$security$PrincipalUtil$TopPrincipal$();
        topPrincipal.$forceRenumber(ONumConstants.TOP_PRINCIPAL);

        // Create the object representing the bottom confidentiality policy.
        bottomConfidPolicy =
            LabelUtil._Impl.readerPolicy(LocalStore.this, null,
                (Principal) null);
        bottomConfidPolicy.$forceRenumber(ONumConstants.BOTTOM_CONFIDENTIALITY);

        // Create the object representing the bottom integrity policy.
        bottomIntegPolicy =
            LabelUtil._Impl.writerPolicy(LocalStore.this, topPrincipal,
                topPrincipal);
        bottomIntegPolicy.$forceRenumber(ONumConstants.BOTTOM_INTEGRITY);

        // Create the object representing the public readonly label.
        publicReadonlyLabel =
            LabelUtil._Impl.toLabel(LocalStore.this, bottomConfidPolicy,
                bottomIntegPolicy);
        publicReadonlyLabel.$forceRenumber(ONumConstants.PUBLIC_READONLY_LABEL);

        // Create the object representing the top confidentiality policy.
        topConfidPolicy =
            LabelUtil._Impl.readerPolicy(LocalStore.this, topPrincipal,
                topPrincipal);
        topConfidPolicy.$forceRenumber(ONumConstants.TOP_CONFIDENTIALITY);

        // Create the object representing the top integrity policy.
        topIntegPolicy =
            LabelUtil._Impl.writerPolicy(LocalStore.this, null,
                (Principal) null);
        topIntegPolicy.$forceRenumber(ONumConstants.TOP_INTEGRITY);

        // Create the object representing the empty label.
        emptyLabel =
            LabelUtil._Impl.toLabel(LocalStore.this, bottomConfidPolicy,
                topIntegPolicy);
        emptyLabel.$forceRenumber(ONumConstants.EMPTY_LABEL);

        // Create the label {worker->_; worker<-_} for the root map.
        // No need to renumber this. References to the local store's root map
        // should not be leaking to remote stores.
        // XXX the above is not being done. HashMap needs to be parameterized on
        // labels.

        // Create root map.
        rootMap = new HashMap._Impl(LocalStore.this).fabric$util$HashMap$();
        localDelegates = new HashSet<Pair<Principal, Principal>>();

        return null;
      }
    });

    // Put global constants into the cache.
    this.cache.put((_Impl) topPrincipal.fetch());
    this.cache.put((_Impl) topConfidPolicy.fetch());
    this.cache.put((_Impl) bottomConfidPolicy.fetch());
    this.cache.put((_Impl) topIntegPolicy.fetch());
    this.cache.put((_Impl) bottomIntegPolicy.fetch());
    this.cache.put((_Impl) emptyLabel.fetch());
    this.cache.put((_Impl) publicReadonlyLabel.fetch());
  }

  // ////////////////////////////////
  // Java custom-serialization gunk
  // ////////////////////////////////

  private java.lang.Object writeReplace() throws ObjectStreamException {
    throw new NotSerializableException();
  }

}
