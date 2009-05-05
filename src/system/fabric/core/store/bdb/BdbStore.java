package fabric.core.store.bdb;

import static com.sleepycat.je.OperationStatus.SUCCESS;

import java.io.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.*;

import fabric.common.FastSerializable;
import fabric.common.ONumConstants;
import fabric.common.Resources;
import fabric.common.SerializedObject;
import fabric.common.exceptions.AccessException;
import fabric.common.exceptions.InternalError;
import fabric.common.util.Cache;
import fabric.common.util.LongKeyCache;
import fabric.common.util.OidKeyHashMap;
import fabric.core.store.ObjectStore;
import fabric.lang.NodePrincipal;

/**
 * An ObjectStore backed by a Berkeley Database.
 */
public class BdbStore extends ObjectStore {

  private Environment env;
  private Database meta;
  private Database store;
  private Database prepared;

  private final DatabaseEntry initializationStatus;
  private final DatabaseEntry onumCounter;

  private Logger log = Logger.getLogger("fabric.core.store.bdb");

  private long nextOnum;

  /**
   * To prevent touching BDB on every onum reservation request, we keep a bunch
   * of onums in reserve. If nextOnum > lastReservedOnum, it's time to touch BDB
   * again to reserve more onums.
   */
  private long lastReservedOnum;

  /**
   * Cache: maps onums to object versions of objects that are currently stored
   * in BDB.
   */
  private final LongKeyCache<Integer> cachedVersions;

  /**
   * Cache: maps BDB keys to prepared-transaction records.
   */
  private final Cache<ByteArray, PendingTransaction> preparedTransactions;

  /**
   * Creates a new BdbStore for the core specified. A new database will be
   * created if it does not exist.
   * 
   * @param name
   *          name of core to create store for.
   */
  public BdbStore(String name) {
    super(name);

    String path = Resources.relpathRewrite("var", "bdb", name);
    new File(path).mkdirs(); // create path if it does not exist

    try {
      EnvironmentConfig conf = new EnvironmentConfig();
      conf.setAllowCreate(true);
      conf.setTransactional(true);
      env = new Environment(new File(path), conf);

      log.info("Bdb env opened");

      DatabaseConfig dbconf = new DatabaseConfig();
      dbconf.setAllowCreate(true);
      dbconf.setTransactional(true);
      store = env.openDatabase(null, "store", dbconf);
      prepared = env.openDatabase(null, "prepared", dbconf);
      meta = env.openDatabase(null, "meta", dbconf);

      initRwCount();

      log.info("Bdb databases opened");
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in <init>: ", e);
      throw new InternalError(e);
    }

    try {
      initializationStatus =
          new DatabaseEntry("initialization_status".getBytes("UTF-8"));
      onumCounter = new DatabaseEntry("onum_counter".getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new InternalError(e);
    }

    this.nextOnum = -1;
    this.lastReservedOnum = -2;
    this.cachedVersions = new LongKeyCache<Integer>();
    this.preparedTransactions = new Cache<ByteArray, PendingTransaction>();
  }

  @Override
  public void finishPrepare(long tid, NodePrincipal client) {
    // Copy the transaction data into BDB.
    OidKeyHashMap<PendingTransaction> submap = pendingByTid.get(tid);
    PendingTransaction pending = submap.remove(client);
    if (submap.isEmpty()) pendingByTid.remove(tid);

    try {
      Transaction txn = env.beginTransaction(null, null);
      byte[] key = toBytes(tid, client);
      DatabaseEntry data = new DatabaseEntry(toBytes(pending));
      prepared.put(txn, new DatabaseEntry(key), data);
      txn.commit();

      preparedTransactions.put(new ByteArray(key), pending);
      log.finer("Bdb prepare success tid " + tid);
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in finishPrepare: ", e);
      throw new InternalError(e);
    }
  }

  @Override
  public void commit(long tid, NodePrincipal client) {
    log.finer("Bdb commit begin tid " + tid);

    try {
      Transaction txn = env.beginTransaction(null, null);
      PendingTransaction pending = remove(client, txn, tid);

      if (pending != null) {
        for (SerializedObject o : pending.modData) {
          long onum = o.getOnum();
          log.finest("Bdb committing onum " + onum);
          DatabaseEntry onumData = new DatabaseEntry(toBytes(onum));
          DatabaseEntry objData = new DatabaseEntry(toBytes(o));
          store.put(txn, onumData, objData);

          // Remove any cached globs containing the old version of this object.
          removeGlobByOnum(toLong(onumData.getData()));

          // Update the version-number cache.
          cachedVersions.put(onum, o.getVersion());
        }

        txn.commit();
        log.finer("Bdb commit success tid " + tid);
      } else {
        txn.abort();
        log.warning("Bdb commit not found tid " + tid);
        throw new InternalError("Unknown transaction id " + tid);
      }
    } catch (DatabaseException e) {
      // Problem. Clear out cached versions.
      cachedVersions.clear();

      log.log(Level.SEVERE, "Bdb error in commit: ", e);
      throw new InternalError(e);
    }
  }

  @Override
  public void rollback(long tid, NodePrincipal client) {
    log.finer("Bdb rollback begin tid " + tid);

    try {
      Transaction txn = env.beginTransaction(null, null);
      remove(client, txn, tid);
      txn.commit();
      log.finer("Bdb rollback success tid " + tid);
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in rollback: ", e);
      throw new InternalError(e);
    }
  }

  @Override
  public SerializedObject read(long onum) {
    log.finest("Bdb read onum " + onum);
    DatabaseEntry key = new DatabaseEntry(toBytes(onum));
    DatabaseEntry data = new DatabaseEntry();

    try {
      if (store.get(null, key, data, LockMode.DEFAULT) == SUCCESS) {
        SerializedObject result = toSerializedObject(data.getData());
        if (result != null) {
          cachedVersions.put(onum, result.getVersion());
        }

        return result;
      }
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in read: ", e);
      throw new InternalError(e);
    }

    return null;
  }

  @Override
  public int getVersion(long onum) throws AccessException {
    Integer ver = cachedVersions.get(onum);
    if (ver != null) return ver;

    return super.getVersion(onum);
  }

  @Override
  public boolean exists(long onum) {
    DatabaseEntry key = new DatabaseEntry(toBytes(onum));
    DatabaseEntry data = new DatabaseEntry();

    try {
      if (rwLocks.get(onum) != null
          || store.get(null, key, data, LockMode.DEFAULT) == SUCCESS) {
        return true;
      }
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in exists: ", e);
      throw new InternalError(e);
    }

    return false;
  }

  private final long ONUM_RESERVE_SIZE = 10240;

  @Override
  public long[] newOnums(int num) {
    log.fine("Bdb new onums begin");

    try {
      long[] onums = new long[num];
      for (int i = 0; i < num; i++) {
        if (nextOnum > lastReservedOnum) {
          // Reserve more onums from BDB.
          Transaction txn = env.beginTransaction(null, null);
          DatabaseEntry data = new DatabaseEntry();
          nextOnum = ONumConstants.FIRST_UNRESERVED;

          if (meta.get(txn, onumCounter, data, LockMode.DEFAULT) == SUCCESS) {
            nextOnum = toLong(data.getData());
          }

          lastReservedOnum = nextOnum + ONUM_RESERVE_SIZE + num - i - 1;

          data.setData(toBytes(lastReservedOnum + 1));
          meta.put(txn, onumCounter, data);
          txn.commit();

          log.fine("Bdb reserved onums " + nextOnum + "--" + lastReservedOnum);
        }

        onums[i] = nextOnum++;
      }

      return onums;
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in newOnums: ", e);
      throw new InternalError(e);
    }
  }

  /**
   * Clean up and close database.
   */
  @Override
  public void close() {
    try {
      if (store != null) store.close();
      if (prepared != null) prepared.close();
      if (env != null) env.close();
    } catch (DatabaseException e) {
    }
  }

  @Override
  public boolean isInitialized() {
    log.fine("Bdb is initialized begin");

    try {
      Transaction txn = env.beginTransaction(null, null);
      DatabaseEntry data = new DatabaseEntry();
      boolean result = false;

      if (meta.get(txn, initializationStatus, data, LockMode.DEFAULT) == SUCCESS) {
        result = toBoolean(data.getData());
      }

      txn.commit();

      return result;
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in isInitialized: ", e);
      throw new InternalError(e);
    }
  }

  @Override
  public void setInitialized() {
    log.fine("Bdb set initialized begin");

    try {
      Transaction txn = env.beginTransaction(null, null);
      DatabaseEntry data = new DatabaseEntry(toBytes(true));
      meta.put(txn, initializationStatus, data);
      txn.commit();
    } catch (DatabaseException e) {
      log.log(Level.SEVERE, "Bdb error in isInitialized: ", e);
      throw new InternalError(e);
    }
  }

  private void initRwCount() {
    // TODO Recover rwCount info from prepared
  }

  /**
   * Removes a PendingTransaction from the prepare log and returns it. If no
   * transaction with the given transaction id is found, null is returned.
   * 
   * @param client
   *          the principal under which this action is being executed.
   * @param txn
   *          the BDB Transaction instance that should be used to perform the
   *          retrieval.
   * @param tid
   *          the transaction id.
   * @return the PrepareRequest corresponding to tid
   * @throws DatabaseException
   *           if a database error occurs
   */
  private PendingTransaction remove(NodePrincipal client, Transaction txn,
      long tid) throws DatabaseException {
    byte[] key = toBytes(tid, client);
    DatabaseEntry bdbKey = new DatabaseEntry(key);
    DatabaseEntry data = new DatabaseEntry();

    PendingTransaction pending =
        preparedTransactions.remove(new ByteArray(key));

    if (pending == null
        && prepared.get(txn, bdbKey, data, LockMode.DEFAULT) == SUCCESS)
      pending = toPendingTransaction(data.getData());

    if (pending == null) return null;
    prepared.delete(txn, bdbKey);

    unpin(pending);
    return pending;
  }

  private byte[] toBytes(boolean b) {
    byte[] result = { (byte) (b ? 1 : 0) };
    return result;
  }

  private boolean toBoolean(byte[] data) {
    return data[0] == 1;
  }

  private byte[] toBytes(long i) {
    byte[] data = new byte[8];

    for (int j = 0; j < 8; j++) {
      data[7 - j] = (byte) (i & 0xff);
      i = i >>> 8;
    }

    return data;
  }

  private byte[] toBytes(long tid, NodePrincipal client) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(bos);
      dos.writeLong(tid);
      if (client != null) {
        dos.writeUTF(client.$getCore().name());
        dos.writeLong(client.$getOnum());
      }
      dos.flush();
      return bos.toByteArray();
    } catch (IOException e) {
      throw new InternalError(e);
    }
  }

  private long toLong(byte[] data) {
    long i = 0;

    for (int j = 0; j < 8; j++) {
      i = i << 8;
      i = i | (data[j] & 0xff);
    }

    return i;
  }

  private byte[] toBytes(FastSerializable obj) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      obj.write(oos);
      oos.flush();
      return bos.toByteArray();
    } catch (IOException e) {
      throw new InternalError(e);
    }
  }

  private PendingTransaction toPendingTransaction(byte[] data) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bis);
      return new PendingTransaction(ois);
    } catch (IOException e) {
      throw new InternalError(e);
    }
  }

  private SerializedObject toSerializedObject(byte[] data) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bis);
      return new SerializedObject(ois);
    } catch (IOException e) {
      throw new InternalError(e);
    }
  }

  private static class ByteArray {
    private final byte[] data;

    public ByteArray(byte[] data) {
      this.data = data;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ByteArray)) return false;

      byte[] data = ((ByteArray) obj).data;
      return Arrays.equals(data, ((ByteArray) obj).data);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(data);
    }

  }
}
