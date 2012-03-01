package fabric.dissemination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fabric.common.exceptions.AccessException;
import fabric.common.util.Pair;
import fabric.worker.RemoteStore;
import fabric.worker.Store;

/**
 * The cache object used by the disseminator to store globs. Essentially a
 * hashtable specialized for globs; it also fetches globs directly from stores
 * when needed.
 */
public class Cache {

  /**
   * Cache of globs, indexed by the oid of the glob's head object.
   */
  private fabric.common.util.Cache<Pair<Store, Long>, Glob> map =
      new fabric.common.util.Cache<Pair<Store, Long>, Glob>();

  /**
   * Retrieves a glob from the cache, without trying to fetch it from the store.
   * 
   * @param store
   *          the store of the object to retrieve.
   * @param onum
   *          the onum of the object.
   * @return the glob, if it is in the cache; null otherwise.
   */
  public Glob get(RemoteStore store, long onum) {
    return get(store, onum, false);
  }

  /**
   * Retrieves a glob from the cache or fetches it from the store.
   * 
   * @param store
   *          the store of the object to retrieve.
   * @param onum
   *          the onum of the object.
   * @param fetch
   *          whether the glob should be should fetched from store in the event
   *          of a cache miss.
   * @return the glob, or null if fetch is false and glob does not exists in
   *         cache.
   */
  public Glob get(RemoteStore store, long onum, boolean fetch) {
    Pair<Store, Long> key = new Pair<Store, Long>(store, onum);

    synchronized (map) {
      Glob g = map.get(key);
      if (g == null) g = fetch(store, onum);
      return g;
    }
  }

  /**
   * Fetches a glob from the store and caches it.
   */
  private Glob fetch(RemoteStore c, long onum) {
    Glob g = null;

    try {
      g = c.readEncryptedObjectFromStore(onum);
    } catch (AccessException e) {
    }

    if (g != null) {
      Pair<Store, Long> key = new Pair<Store, Long>(c, onum);
      map.put(key, g);
    }

    return g;
  }

  /**
   * Put given glob into the cache.
   * 
   * @param store
   *          the store of the object.
   * @param onum
   *          the onum of the object.
   * @param g
   *          the glob.
   */
  public void put(RemoteStore store, long onum, Glob g) {
    Pair<Store, Long> key = new Pair<Store, Long>(store, onum);

    synchronized (map) {
      Glob old = get(store, onum);

      if (old == null || old.isOlderThan(g)) {
        map.put(key, g);
      }
    }
  }

  /**
   * Updates a cache entry with the given glob. If the cache has no entry for
   * the given oid, then nothing is changed.
   * 
   * @return true iff there was a cache entry for the given oid.
   */
  public boolean updateEntry(RemoteStore store, long onum, Glob g) {
    Pair<Store, Long> key = new Pair<Store, Long>(store, onum);

    synchronized (map) {
      Glob old = get(store, onum);
      if (old == null) return false;

      if (old.isOlderThan(g)) {
        map.put(key, g);
      }
    }

    return true;
  }

  /**
   * Returns a snapshot of the timestamp for each OID currently in the cache.
   * This set is NOT backed by the underlying map. If new keys are inserted or
   * removed from the cache, they will not be reflected by the set returned.
   * However, no synchronization is needed for working with the set.
   */
  public Set<Pair<Pair<Store, Long>, Long>> timestamps() {
    Set<Pair<Pair<Store, Long>, Long>> result =
        new HashSet<Pair<Pair<Store, Long>, Long>>();

    for (Pair<Store, Long> key : map.keys()) {
      Glob glob = map.get(key);
      if (glob != null)
        result.add(new Pair<Pair<Store, Long>, Long>(key, glob.getTimestamp()));
    }

    return result;
  }

  /**
   * Returns a snapshot set of the timestamp for each OID currently in the
   * cache. The set is sorted in descending order by the popularity of the
   * corresponding objects. Like {@code timestamps()}, the returned set is not
   * backed by the underlying table.
   */
  public List<Pair<Pair<Store, Long>, Long>> sortedTimestamps() {
    List<Pair<Pair<Store, Long>, Long>> k =
        new ArrayList<Pair<Pair<Store, Long>, Long>>(timestamps());

    Collections.sort(k, TIMESTAMP_COMPARATOR);

    return k;
  }

  private final Comparator<Pair<Pair<Store, Long>, Long>> TIMESTAMP_COMPARATOR =
      new Comparator<Pair<Pair<Store, Long>, Long>>() {
        @Override
        public int compare(Pair<Pair<Store, Long>, Long> o1,
            Pair<Pair<Store, Long>, Long> o2) {
          Glob g1 = map.get(o1.first);
          Glob g2 = map.get(o2.first);

          if (g1 == g2) {
            return 0;
          }

          if (g1 == null) {
            return 1;
          }

          if (g2 == null) {
            return -1;
          }

          return g2.frequency() - g1.frequency();
        }
      };

}
