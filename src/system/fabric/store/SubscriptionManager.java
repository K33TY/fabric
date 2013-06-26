package fabric.store;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import fabric.common.FabricThread;
import fabric.common.Logging;
import fabric.common.ObjectGroup;
import fabric.common.Threading;
import fabric.common.VersionWarranty.Binding;
import fabric.common.WarrantyGroup;
import fabric.common.exceptions.AccessException;
import fabric.common.exceptions.InternalError;
import fabric.common.util.LongHashSet;
import fabric.common.util.LongIterator;
import fabric.common.util.LongKeyCache;
import fabric.common.util.LongKeyHashMap;
import fabric.common.util.LongKeyMap;
import fabric.common.util.LongSet;
import fabric.common.util.Pair;
import fabric.dissemination.ObjectGlob;
import fabric.dissemination.WarrantyGlob;
import fabric.store.db.GroupContainer;
import fabric.worker.Store;
import fabric.worker.Worker;
import fabric.worker.remote.RemoteWorker;

/**
 * Keeps track of who's subscribed to what object. Handles subscriptions for a
 * single store.
 */
public class SubscriptionManager extends FabricThread.Impl {
  private static abstract class NotificationEvent extends
      Threading.NamedRunnable {
    public NotificationEvent(String name) {
      super(name);
    }

    @Override
    protected final void runImpl() {
      handle();
    }

    protected abstract void handle();
  }

  private final class ObjectUpdateEvent extends NotificationEvent {
    /**
     * The set of onums that were updated.
     */
    final LongSet onums;

    /**
     * The worker that updated the onums.
     */
    final RemoteWorker writer;

    public ObjectUpdateEvent(LongSet onums, RemoteWorker writer) {
      super("Fabric subscription manager object-update notifier");
      this.onums = onums;
      this.writer = writer;
    }

    @Override
    protected void handle() {
      // Go through the onums and figure out which workers are interested in
      // which updates.
      Map<RemoteWorker, List<Long>> onumsToNotify =
          new HashMap<RemoteWorker, List<Long>>();
      Map<RemoteWorker, LongSet> groupedOnumsToSend =
          new HashMap<RemoteWorker, LongSet>();

      Map<RemoteWorker, List<ObjectGroup>> workerNotificationMap =
          new HashMap<RemoteWorker, List<ObjectGroup>>();
      Map<RemoteWorker, LongKeyMap<ObjectGlob>> dissemNotificationMap =
          new HashMap<RemoteWorker, LongKeyMap<ObjectGlob>>();

      for (LongIterator it = onums.iterator(); it.hasNext();) {
        long onum = it.next();
        GroupContainer groupContainer;
        ObjectGlob glob;
        try {
          // Skip if the onum represents a surrogate.
          if (tm.read(onum).isSurrogate()) continue;

          groupContainer = tm.getGroupContainer(onum);
          glob = groupContainer.getGlob();
        } catch (AccessException e) {
          throw new InternalError(e);
        }

        Set<Pair<RemoteWorker, Boolean>> subscribers = subscriptions.get(onum);
        if (subscribers != null) {
          synchronized (subscribers) {
            for (Iterator<Pair<RemoteWorker, Boolean>> subscriberIt =
                subscribers.iterator(); subscriberIt.hasNext();) {
              Pair<RemoteWorker, Boolean> subscriber = subscriberIt.next();

              RemoteWorker subscribingNode = subscriber.first;
              boolean isDissem = subscriber.second;

              if (subscribingNode == writer && !isDissem) continue;
              subscriberIt.remove();

              if (isDissem) {
                // Add group to dissemNotificationMap.
                LongKeyMap<ObjectGlob> globs =
                    dissemNotificationMap.get(subscribingNode);
                if (globs == null) {
                  globs = new LongKeyHashMap<ObjectGlob>();
                  dissemNotificationMap.put(subscribingNode, globs);
                }

                globs.put(onum, glob);
              } else {
                // Add onum to onumsToNotify.
                List<Long> toNotify = onumsToNotify.get(subscribingNode);
                if (toNotify == null) {
                  toNotify = new ArrayList<Long>();
                  onumsToNotify.put(subscribingNode, toNotify);
                }
                toNotify.add(onum);

                // Check whether onum is already being sent.
                LongSet toSend = groupedOnumsToSend.get(subscribingNode);
                if (toSend == null) {
                  toSend = new LongHashSet();
                  groupedOnumsToSend.put(subscribingNode, toSend);
                }

                if (!toSend.contains(onum)) {
                  // Add group to workerNotificationMap.
                  ObjectGroup group =
                      groupContainer.getGroup(subscribingNode.getPrincipal());

                  List<ObjectGroup> groups =
                      workerNotificationMap.get(subscribingNode);
                  if (groups == null) {
                    groups = new ArrayList<ObjectGroup>();
                    workerNotificationMap.put(subscribingNode, groups);
                  }

                  groups.add(group);

                  // Add group's onums to onumsToSend.
                  toSend.addAll(group.objects().keySet());
                }
              }
            }
          }
        }
      }

      // Notify the workers and resubscribe them.
      for (Entry<RemoteWorker, List<ObjectGroup>> entry : workerNotificationMap
          .entrySet()) {
        RemoteWorker worker = entry.getKey();
        List<ObjectGroup> updates = entry.getValue();

        List<Long> updatedOnums = onumsToNotify.get(worker);
        List<Long> resubscriptions =
            worker.notifyObjectUpdates(updatedOnums, updates);
        resubscriptions.retainAll(new HashSet<Long>(updatedOnums));

        // Resubscribe.
        for (long onum : resubscriptions) {
          subscribe(onum, worker, false);
        }
      }

      // Notify the dissemination nodes and resubscribe them.
      for (Entry<RemoteWorker, LongKeyMap<ObjectGlob>> entry : dissemNotificationMap
          .entrySet()) {
        RemoteWorker dissemNode = entry.getKey();
        LongKeyMap<ObjectGlob> updates = entry.getValue();

        List<Long> resubscriptions =
            dissemNode.notifyObjectUpdates(storeName, updates);

        // Resubscribe.
        for (long onum : resubscriptions) {
          if (updates.containsKey(onum)) {
            subscribe(onum, dissemNode, true);
          }
        }
      }
    }
  }

  private final class NewWarrantyEvent extends NotificationEvent {
    /**
     * The collection of new warranties.
     */
    final Collection<Binding> newWarranties;

    /**
     * The worker, if any, that already knows about the warranties.
     */
    final RemoteWorker worker;

    public NewWarrantyEvent(Collection<Binding> newWarranties,
        RemoteWorker worker) {
      super("Fabric subscription manager warranty-refresh notifier");
      this.newWarranties = newWarranties;
      this.worker = worker;
    }

    @Override
    protected void handle() {
      // Ensure the store field is initialized.
      if (store == null) {
        store = Worker.getWorker().getStore(storeName);
      }

      // Go through the warranties and figure out who's interested in which
      // updates. While we're at it, also build a table of updates, keyed by onum.
      LongKeyMap<Binding> updatesByOnum = new LongKeyHashMap<Binding>();
      Map<RemoteWorker, WarrantyGroup> workerNotificationMap =
          new HashMap<>();
      Map<Binding, List<RemoteWorker>> dissemNotificationMap =
          new HashMap<Binding, List<RemoteWorker>>();

      for (Binding update : newWarranties) {
        long onum = update.onum;
        updatesByOnum.put(onum, update);

        Set<Pair<RemoteWorker, Boolean>> subscribers = subscriptions.get(onum);
        if (subscribers != null) {
          synchronized (subscribers) {
            for (Iterator<Pair<RemoteWorker, Boolean>> subscriberIt =
                subscribers.iterator(); subscriberIt.hasNext();) {
              Pair<RemoteWorker, Boolean> subscriber = subscriberIt.next();

              RemoteWorker subscribingNode = subscriber.first;
              boolean isDissem = subscriber.second;
              if (subscribingNode == worker && !isDissem) continue;

              if (isDissem) {
                List<RemoteWorker> dissems = dissemNotificationMap.get(update);
                if (dissems == null) {
                  dissems = new ArrayList<RemoteWorker>();
                  dissemNotificationMap.put(update, dissems);
                }

                dissems.add(subscribingNode);
              } else {
                WarrantyGroup refreshGroup =
                    workerNotificationMap.get(subscribingNode);
                if (refreshGroup == null) {
                  refreshGroup = new WarrantyGroup();
                  workerNotificationMap.put(subscribingNode, refreshGroup);
                }

                refreshGroup.add(update);
              }

              subscriberIt.remove();
            }
          }
        }

        if (worker != null) {
          subscribe(onum, worker, false);
        }
      }

      // Notify the workers and resubscribe them.
      for (Map.Entry<RemoteWorker, WarrantyGroup> entry : workerNotificationMap
          .entrySet()) {
        RemoteWorker worker = entry.getKey();
        WarrantyGroup warranties = entry.getValue();
        List<Long> resubscriptions = worker.notifyWarrantyRefresh(warranties);

        // Resubscribe.
        for (long onum : resubscriptions) {
          subscribe(onum, worker, false);
        }
      }

      // Map the dissemination nodes to the groups of updates that they're
      // interested in.
      Map<RemoteWorker, LongKeyMap<WarrantyGlob>> dissemUpdateMap =
          new HashMap<RemoteWorker, LongKeyMap<WarrantyGlob>>();
      for (Map.Entry<Binding, List<RemoteWorker>> entry : dissemNotificationMap
          .entrySet()) {
        Binding update = entry.getKey();
        List<RemoteWorker> dissemNodes = entry.getValue();

        // Update the object group with the new warranties and get the resulting
        // set of updated warranties in the group.
        GroupContainer groupContainer;
        try {
          groupContainer = tm.getGroupContainer(update.onum);
        } catch (AccessException e) {
          throw new InternalError(e);
        }
        groupContainer.addRefreshedWarranties(updatesByOnum);
        WarrantyGroup updateGroup =
            groupContainer.getWarranties();

        // Encrypt the group.
        WarrantyGlob updateGlob =
            new WarrantyGlob(store, signingKey, updateGroup);

        // Add to the dissemUpdateMap.
        for (RemoteWorker dissemNode : dissemNodes) {
          LongKeyMap<WarrantyGlob> updates =
              dissemUpdateMap.get(dissemNode);
          if (updates == null) {
            updates = new LongKeyHashMap<WarrantyGlob>();
            dissemUpdateMap.put(dissemNode, updates);
          }

          updates.put(update.onum, updateGlob);
        }
      }

      // Send the updates to the dissemination nodes and resubscribe them.
      for (Map.Entry<RemoteWorker, LongKeyMap<WarrantyGlob>> entry : dissemUpdateMap
          .entrySet()) {
        RemoteWorker dissemNode = entry.getKey();
        LongKeyMap<WarrantyGlob> updates = entry.getValue();

        List<Long> resubscriptions =
            dissemNode.notifyWarrantyRefresh(storeName, updates);

        // Resubscribe.
        for (long onum : resubscriptions) {
          subscribe(onum, dissemNode, true);
        }
      }
    }
  }

  private final BlockingQueue<NotificationEvent> notificationQueue;

  /**
   * The set of nodes subscribed to each onum. The second component of each pair
   * indicates whether the node is subscribed as a dissemination node. (true =
   * dissemination, false = worker)
   */
  private final LongKeyCache<Set<Pair<RemoteWorker, Boolean>>> subscriptions;

  /**
   * The name of the store for which we are managing subscriptions.
   */
  private final String storeName;

  // Filled in lazily. Initializing this requires the worker, which won't be
  // initialized until after the subscription manager is constructed.
  private Store store = null;

  /**
   * The transaction manager corresponding to the store for which we are
   * managing subscriptions.
   */
  private final TransactionManager tm;

  /**
   * The key with which to sign outgoing update notifications.
   */
  private final PrivateKey signingKey;

  /**
   * @param tm
   *          The transaction manager corresponding to the store for which
   *          subscriptions are to be managed.
   */
  public SubscriptionManager(String store, TransactionManager tm,
      PrivateKey signingKey) {
    super("subscription manager for store " + store);
    this.storeName = store;
    this.notificationQueue = new ArrayBlockingQueue<NotificationEvent>(50);
    this.tm = tm;
    this.subscriptions = new LongKeyCache<Set<Pair<RemoteWorker, Boolean>>>();
    this.signingKey = signingKey;

    start();
  }

  @Override
  public void run() {
    while (true) {
      try {
        Threading.getPool().submit(notificationQueue.take());
      } catch (InterruptedException e1) {
        Logging.logIgnoredInterruptedException(e1);
      }
    }
  }

  /**
   * Subscribes the given worker to the given onum.
   * 
   * @param dissemSubscribe
   *          If true, then the given subscriber will be subscribed as a
   *          dissemination node; otherwise it will be subscribed as a worker.
   */
  public void subscribe(long onum, RemoteWorker worker, boolean dissemSubscribe) {
    Set<Pair<RemoteWorker, Boolean>> subscribers = subscriptions.get(onum);
    if (subscribers == null) {
      subscribers = new HashSet<Pair<RemoteWorker, Boolean>>();
      Set<Pair<RemoteWorker, Boolean>> existing =
          subscriptions.putIfAbsent(onum, subscribers);
      if (existing != null) subscribers = existing;
    }

    synchronized (subscribers) {
      subscribers.add(new Pair<RemoteWorker, Boolean>(worker, dissemSubscribe));
    }
  }

  /**
   * Notifies the subscription manager that a set of objects has been updated by a
   * particular worker.
   */
  public void notifyUpdate(LongSet onums, RemoteWorker worker) {
    notificationQueue.offer(new ObjectUpdateEvent(onums, worker));
  }

  /**
   * Notifies the subscription manager that a new set of warranties has been
   * issued.
   * 
   * @param worker the worker, if any, that already knows about the new warranties.
   */
  public void notifyNewWarranties(Collection<Binding> newWarranties,
      RemoteWorker worker) {
    if (!newWarranties.isEmpty()) {
      notificationQueue.offer(new NewWarrantyEvent(newWarranties, worker));
    }
  }
}
