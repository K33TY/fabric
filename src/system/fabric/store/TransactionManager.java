package fabric.store;

import static fabric.common.Logging.STORE_TRANSACTION_LOGGER;
import static fabric.store.db.ObjectDB.UpdateType.CREATE;
import static fabric.store.db.ObjectDB.UpdateType.WRITE;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import fabric.common.AuthorizationUtil;
import fabric.common.ONumConstants;
import fabric.common.ObjectGroup;
import fabric.common.SerializedObject;
import fabric.common.VersionWarranty;
import fabric.common.WarrantyGroup;
import fabric.common.exceptions.AccessException;
import fabric.common.exceptions.InternalError;
import fabric.common.exceptions.RuntimeFetchException;
import fabric.common.net.RemoteIdentity;
import fabric.common.util.LongIterator;
import fabric.common.util.LongKeyHashMap;
import fabric.common.util.LongKeyMap;
import fabric.common.util.LongKeyMap.Entry;
import fabric.common.util.LongSet;
import fabric.common.util.Pair;
import fabric.dissemination.ObjectGlob;
import fabric.dissemination.WarrantyGlob;
import fabric.lang.security.Label;
import fabric.lang.security.Principal;
import fabric.store.db.GroupContainer;
import fabric.store.db.ObjectDB;
import fabric.store.db.ObjectDB.ExtendWarrantyStatus;
import fabric.worker.AbortException;
import fabric.worker.Store;
import fabric.worker.TransactionCommitFailedException;
import fabric.worker.TransactionPrepareFailedException;
import fabric.worker.Worker;
import fabric.worker.Worker.Code;
import fabric.worker.remote.RemoteWorker;

public class TransactionManager {

  /**
   * The object database of the store for which we're managing transactions.
   */
  private final ObjectDB database;

  /**
   * The subscription manager associated with the store for which we're managing
   * transactions.
   */
  private final SubscriptionManager sm;

  public TransactionManager(ObjectDB database, PrivateKey signingKey) {
    this.database = database;
    this.sm = new SubscriptionManager(database.getName(), this, signingKey);
  }

  public final SubscriptionManager subscriptionManager() {
    return sm;
  }

  /**
   * Instructs the transaction manager that the given transaction is aborting
   */
  public void abortTransaction(Principal worker, long transactionID)
      throws AccessException {
    database.abort(transactionID, worker);
    STORE_TRANSACTION_LOGGER.fine("Aborted transaction " + transactionID);
  }

  /**
   * Executes the COMMIT phase of the three-phase commit.
   */
  public void commitTransaction(RemoteIdentity workerIdentity,
      long transactionID, long commitTime)
      throws TransactionCommitFailedException {
    try {
      database.commit(transactionID, commitTime, workerIdentity, sm);
      STORE_TRANSACTION_LOGGER.fine("Committed transaction " + transactionID);
    } catch (final RuntimeException e) {
      throw new TransactionCommitFailedException(
          "something went wrong; store experienced a runtime exception during "
              + "commit: " + e.getMessage(), e);
    }
  }

  /**
   * Executes the PREPARE_WRITES phase of the three-phase commit.
   * 
   * @return a minimum commit time, specifying a time after which the warranties
   *     on all modified objects will expire.
   * @throws TransactionPrepareFailedException
   *           If the transaction would cause a conflict or if the worker is
   *           insufficiently privileged to execute the transaction.
   */
  public long prepareWrites(Principal worker, PrepareWritesRequest req)
      throws TransactionPrepareFailedException {
    final long tid = req.tid;
    VersionWarranty longestWarranty = null;

    // First, check write permissions. We do this before we attempt to do the
    // actual prepare because we want to run the permissions check in a
    // transaction outside of the worker's transaction.
    Store store = Worker.getWorker().getStore(database.getName());
    if (worker == null || worker.$getStore() != store
        || worker.$getOnum() != ONumConstants.STORE_PRINCIPAL) {
      try {
        checkPerms(worker, LongSet.EMPTY, req.writes);
      } catch (AccessException e) {
        throw new TransactionPrepareFailedException(e.getMessage());
      } catch (AbortException e) {
        throw new TransactionPrepareFailedException(e.getMessage());
      }
    }

    database.beginPrepareWrites(tid, worker);

    try {
      // This will store the set of onums of objects that were out of date.
      LongKeyMap<Pair<SerializedObject, VersionWarranty>> versionConflicts =
          new LongKeyHashMap<Pair<SerializedObject, VersionWarranty>>();

      // Prepare writes.
      for (SerializedObject o : req.writes) {
        VersionWarranty warranty =
            database.registerUpdate(tid, worker, o, versionConflicts, WRITE);
        if (longestWarranty == null || warranty.expiresAfter(longestWarranty))
          longestWarranty = warranty;
      }

      // Prepare creates.
      for (SerializedObject o : req.creates) {
        database.registerUpdate(tid, worker, o, versionConflicts, CREATE);
      }

      if (!versionConflicts.isEmpty()) {
        throw new TransactionPrepareFailedException(versionConflicts);
      }

      database.finishPrepareWrites(tid, worker);

      STORE_TRANSACTION_LOGGER.fine("Prepared writes for transaction " + tid);

      return longestWarranty == null ? 0 : longestWarranty.expiry();
    } catch (TransactionPrepareFailedException e) {
      database.abortPrepareWrites(tid, worker);
      throw e;
    } catch (RuntimeException e) {
      e.printStackTrace();
      database.abortPrepareWrites(tid, worker);
      throw e;
    }
  }

  /**
   * Executes the PREPARE_READS phase of the three-phase commit.
   * 
   * @param workerIdentity
   *          The worker requesting the prepare
   * @throws TransactionPrepareFailedException
   *           If the transaction would cause a conflict or if the worker is
   *           insufficiently privileged to execute the transaction.
   */
  public LongKeyMap<VersionWarranty> prepareReads(
      RemoteIdentity workerIdentity, long tid, LongKeyMap<Integer> reads,
      long commitTime) throws TransactionPrepareFailedException {

    Principal worker = workerIdentity.principal;

    try {
      // First, check read permissions. We do this before we attempt to do the
      // actual prepare because we want to run the permissions check in a
      // transaction outside of the worker's transaction.
      Store store = Worker.getWorker().getStore(database.getName());
      if (worker == null || worker.$getStore() != store
          || worker.$getOnum() != ONumConstants.STORE_PRINCIPAL) {
        try {
          checkPerms(worker, reads.keySet(),
              Collections.<SerializedObject> emptyList());
        } catch (AccessException e) {
          throw new TransactionPrepareFailedException(e.getMessage());
        }
      }

      // This will store the set of onums of objects that were out of date.
      LongKeyMap<Pair<SerializedObject, VersionWarranty>> versionConflicts =
          new LongKeyHashMap<Pair<SerializedObject, VersionWarranty>>();

      // This will store the warranties that will be sent back to the worker as
      // a result of the prepare.
      LongKeyMap<VersionWarranty> prepareResult =
          new LongKeyHashMap<VersionWarranty>();

      // This will store the new warranties we get.
      List<VersionWarranty.Binding> newWarranties =
          new ArrayList<VersionWarranty.Binding>();

      // Check reads
      for (LongKeyMap.Entry<Integer> entry : reads.entrySet()) {
        long onum = entry.getKey();
        int version = entry.getValue().intValue();

        // Attempt to extend the object's warranty.
        try {
          Pair<ExtendWarrantyStatus, VersionWarranty> status =
              database.extendWarrantyForReadPrepare(worker, onum, version,
                  commitTime);
          switch (status.first) {
          case NEW:
            newWarranties.add(status.second.new Binding(onum, version));
            //$FALL-THROUGH$
          case OLD:
            prepareResult.put(onum, status.second);
            break;

          case BAD_VERSION:
            SerializedObject obj = database.read(onum);
            status = database.refreshWarranty(onum);
            versionConflicts
                .put(onum, new Pair<SerializedObject, VersionWarranty>(obj,
                    status.second));
            continue;

          case DENIED:
            sm.notifyNewWarranties(newWarranties, null);
            throw new TransactionPrepareFailedException(versionConflicts,
                "Unable to extend warranty for object " + onum);
          }
        } catch (AccessException e) {
          sm.notifyNewWarranties(newWarranties, null);
          throw new TransactionPrepareFailedException(versionConflicts,
              e.getMessage());
        }
      }

      if (!versionConflicts.isEmpty()) {
        sm.notifyNewWarranties(newWarranties, null);
        throw new TransactionPrepareFailedException(versionConflicts);
      }

      STORE_TRANSACTION_LOGGER.fine("Prepared transaction " + tid);
      sm.notifyNewWarranties(newWarranties, (RemoteWorker) workerIdentity.node);
      return prepareResult;
    } catch (TransactionPrepareFailedException e) {
      // Roll back the transaction.
      try {
        abortTransaction(worker, tid);
      } catch (AccessException ae) {
        throw new InternalError(ae);
      }
      throw e;
    }
  }

  /**
   * Checks that the worker principal has permissions to read/write the given
   * objects. If it doesn't, an AccessException is thrown.
   */
  private void checkPerms(final Principal worker, final LongSet reads,
      final Collection<SerializedObject> writes) throws AccessException {
    // The code that does the actual checking.
    Code<AccessException> checker = new Code<AccessException>() {
      @Override
      public AccessException run() {
        Store store = Worker.getWorker().getStore(database.getName());

        for (LongIterator it = reads.iterator(); it.hasNext();) {
          long onum = it.next();

          fabric.lang.Object storeCopy =
              new fabric.lang.Object._Proxy(store, onum);

          Label label;
          try {
            label = storeCopy.get$$updateLabel();
          } catch (RuntimeFetchException e) {
            return new AccessException("Object at onum " + onum
                + " doesn't exist.");
          }

          // Check read permissions.
          if (!AuthorizationUtil.isReadPermitted(worker, label.$getStore(),
              label.$getOnum())) {
            return new AccessException("read", worker, storeCopy);
          }
        }

        for (SerializedObject o : writes) {
          long onum = o.getOnum();

          fabric.lang.Object storeCopy =
              new fabric.lang.Object._Proxy(store, onum);

          Label label;
          try {
            label = storeCopy.get$$updateLabel();
          } catch (RuntimeFetchException e) {
            return new AccessException("Object at onum " + onum
                + " doesn't exist.");
          }

          // Check write permissions.
          if (!AuthorizationUtil.isWritePermitted(worker, label.$getStore(),
              label.$getOnum())) {
            return new AccessException("write", worker, storeCopy);
          }
        }

        return null;
      }
    };

    AccessException failure = Worker.runInTopLevelTransaction(checker, true);

    if (failure != null) throw failure;
  }

  /**
   * Returns a GroupContainer containing the specified object, without
   * refreshing warranties.
   */
  GroupContainer getGroupContainer(long onum) throws AccessException {
    return getGroupContainerAndSubscribe(onum, null, false /* this argument doesn't matter */);
  }

  /**
   * Returns a GroupContainer containing the specified object. All surrogates
   * referenced by any object in the group will also be in the group. This
   * ensures that the worker will not reveal information when dereferencing
   * surrogates.
   * 
   * @param subscriber
   *          If non-null, then the given worker will be subscribed to the
   *          object and the object's group's warranties will be refreshed.
   * @param dissemSubscribe
   *          True if the subscriber is a dissemination node; false if it's a
   *          worker.
   */
  GroupContainer getGroupContainerAndSubscribe(long onum,
      RemoteWorker subscriber, boolean dissemSubscribe) throws AccessException {
    GroupContainer container = database.readGroup(onum);
    if (container == null) throw new AccessException(database.getName(), onum);

    if (subscriber != null) {
      sm.subscribe(onum, subscriber, dissemSubscribe);
      container.refreshWarranties(this);
    }

    return container;
  }

  /**
   * Returns a Glob containing the specified object. All surrogates referenced
   * by any object in the group will also be in the group. This ensures that the
   * worker will not reveal information when dereferencing surrogates.
   * 
   * @param subscriber
   *          If non-null, then the given worker will be subscribed to the
   *          object as a dissemination node.
   */
  public Pair<ObjectGlob, WarrantyGlob> getGlobs(long onum,
      RemoteWorker subscriber) throws AccessException {
    return getGroupContainerAndSubscribe(onum, subscriber, true).getGlobs();
  }

  /**
   * Returns an ObjectGroup containing the specified object. All surrogates
   * referenced by any object in the group will also be in the group. This
   * ensures that the worker will not reveal information when dereferencing
   * surrogates.
   * 
   * @param principal
   *          The principal performing the read.
   * @param subscriber
   *          If non-null, then the given worker will be subscribed to the
   *          object as a worker.
   * @param onum
   *          The onum for an object that should be in the group.
   * @param handler
   *          Used to track read statistics.
   */
  public Pair<ObjectGroup, WarrantyGroup> getGroup(Principal principal,
      RemoteWorker subscriber, long onum) throws AccessException {
    Pair<ObjectGroup, WarrantyGroup> group =
        getGroupContainerAndSubscribe(onum, subscriber, false).getGroups(
            principal);
    if (group == null) throw new AccessException(database.getName(), onum);
    return group;
  }

  /**
   * Reads an object from the object database. No authorization checks are done
   * here.
   */
  SerializedObject read(long onum) {
    return database.read(onum);
  }

  /**
   * Refreshes the warranties on a group of objects, represented by a mapping
   * from onums to version numbers. The refresh is done by creating new
   * warranties for any objects whose warranty has expired.
   */
  public void refreshWarranties(LongKeyMap<Integer> onumsToVersions) {
    List<VersionWarranty.Binding> newWarranties =
        new ArrayList<VersionWarranty.Binding>();

    for (Entry<Integer> entry : onumsToVersions.entrySet()) {
      long onum = entry.getKey();
      Pair<ExtendWarrantyStatus, VersionWarranty> refreshResult =
          database.refreshWarranty(onum);

      if (refreshResult.first == ExtendWarrantyStatus.NEW) {
        newWarranties.add(refreshResult.second.new Binding(onum, entry
            .getValue()));
      }
    }

    sm.notifyNewWarranties(newWarranties, null);
  }

  /**
   * @throws AccessException
   *           if the principal is not allowed to create objects on this store.
   */
  public long[] newOnums(Principal worker, int num) throws AccessException {
    return database.newOnums(num);
  }

  /**
   * Creates new onums, bypassing authorization. This is for internal use by the
   * store.
   */
  long[] newOnums(int num) {
    return database.newOnums(num);
  }

  /**
   * Checks the given set of objects for staleness and returns a list of updates
   * for any stale objects found.
   */
  List<Pair<SerializedObject, VersionWarranty>> checkForStaleObjects(
      RemoteIdentity workerIdentity, LongKeyMap<Integer> versions)
      throws AccessException {
    Principal worker = workerIdentity.principal;

    // First, check read and write permissions.
    Store store = Worker.getWorker().getStore(database.getName());
    if (worker == null || worker.$getStore() != store
        || worker.$getOnum() != ONumConstants.STORE_PRINCIPAL) {
      checkPerms(worker, versions.keySet(),
          Collections.<SerializedObject> emptyList());
    }

    List<Pair<SerializedObject, VersionWarranty>> result =
        new ArrayList<Pair<SerializedObject, VersionWarranty>>();
    List<VersionWarranty.Binding> newWarranties =
        new ArrayList<VersionWarranty.Binding>();
    boolean success = false;

    try {
      for (LongKeyMap.Entry<Integer> entry : versions.entrySet()) {
        long onum = entry.getKey();
        int version = entry.getValue();

        int curVersion = database.getVersion(onum);
        if (curVersion != version) {
          Pair<ExtendWarrantyStatus, VersionWarranty> refreshWarrantyResult =
              database.refreshWarranty(onum);
          SerializedObject obj = database.read(onum);

          result.add(new Pair<SerializedObject, VersionWarranty>(obj,
              refreshWarrantyResult.second));

          if (refreshWarrantyResult.first == ExtendWarrantyStatus.NEW) {
            newWarranties.add(refreshWarrantyResult.second.new Binding(onum,
                version));
          }
        }
      }
      success = true;
    } finally {
      sm.notifyNewWarranties(newWarranties,
          success ? (RemoteWorker) workerIdentity.node : null);
    }

    return result;
  }

}
