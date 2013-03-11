package fabric.dissemination;

import java.util.Properties;

import fabric.common.ObjectGroup;
import fabric.common.SemanticWarranty;
import fabric.common.util.Pair;
import fabric.worker.RemoteStore;
import fabric.worker.Worker;

/**
 * This simple FetchManger always goes directly to the store.
 */
public class DummyFetchManager implements FetchManager {

  private Cache cache;

  public DummyFetchManager(Worker worker, Properties dissemConfig) {
    this.cache = new Cache();
  }

  @Override
  public ObjectGroup fetch(RemoteStore store, long onum) {
    return cache.get(store, onum, true).decrypt();
  }

  @Override
  public Pair<Object, SemanticWarranty> fetchCall(RemoteStore store, long callId) {
    /* TODO: Implement */
    return null;
  }

  @Override
  public void destroy() {
  }

}
