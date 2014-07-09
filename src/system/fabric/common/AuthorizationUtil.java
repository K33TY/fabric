/**
 * Copyright (C) 2010-2014 Fabric project group, Cornell University
 *
 * This file is part of Fabric.
 *
 * Fabric is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * Fabric is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 */
package fabric.common;

import fabric.common.util.ConcurrentOidKeyHashMap;
import fabric.lang.security.Label;
import fabric.lang.security.LabelUtil;
import fabric.lang.security.Principal;
import fabric.worker.Store;
import fabric.worker.Worker;

public class AuthorizationUtil {

  private static final class Singleton {
    static final Singleton INSTANCE = new Singleton();
  }

  /**
   * This is the cache for authorized reads. The keys in this map are label
   * onums. The values are sets of oids of principals that are authorized to
   * read according to the label. We're not using the caches in LabelUtil
   * because the transaction management is too slow (!!).
   */
  private static final ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>> cachedReadAuthorizations =
      new ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>>();

  /**
   * This is the cache for authorizing writes. The keys in this map are label
   * onums. The values are principals that are authorized to write according to
   * the label. We're not using the caches in LabelUtil because the transaction
   * management is too slow (!!).
   */
  private static final ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>> cachedWriteAuthorizations =
      new ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>>();

  /**
   * Return true if cache[label][<code>principal</code>] exists, where label is
   * given by the Oid (<code>store</code>,<code>labelOnum</code>).
   */
  private static boolean checkAuthorizationCache(
      ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>> cache,
      Principal principal, Store store, long labelOnum) {
    ConcurrentOidKeyHashMap<Singleton> submap = cache.get(store, labelOnum);
    return submap != null && submap.containsKey(principal);
  }

  private static void cacheAuthorization(
      ConcurrentOidKeyHashMap<ConcurrentOidKeyHashMap<Singleton>> cache,
      Principal principal, Store store, long labelOnum) {
    ConcurrentOidKeyHashMap<Singleton> submap =
        new ConcurrentOidKeyHashMap<Singleton>();
    ConcurrentOidKeyHashMap<Singleton> existing =
        cache.putIfAbsent(store, labelOnum, submap);
    if (existing != null) submap = existing;
    submap.put(principal, Singleton.INSTANCE);
  }

  /**
   * Determines whether the given principal is permitted to read according to
   * the label at the given oid. This is run as a subtransaction of the current
   * transaction.
   */
  public static boolean isReadPermitted(final Principal principal, Store store,
      long labelOnum) {
    // Allow the store's worker principal to do anything. We use pointer
    // equality here to avoid having to call into the worker.
    if (principal == Worker.getWorker().getPrincipal()) return true;

    if (checkAuthorizationCache(cachedReadAuthorizations, principal, store,
        labelOnum)) return true;

    // Call into the Jif label framework to perform the label check.
    final Label label = new Label._Proxy(store, labelOnum);
    boolean result = Worker.runInSubTransaction(new Worker.Code<Boolean>() {
      @Override
      public Boolean run() {
        return LabelUtil._Impl.isReadableBy(label, principal);
      }
    });

    if (result) {
      cacheAuthorization(cachedReadAuthorizations, principal, store, labelOnum);
    }

    return result;
  }

  /**
   * Determines whether the given principal is permitted to write according to
   * the label at the given onum. This is run as a subtransaction of the current
   * transaction.
   */
  public static boolean isWritePermitted(final Principal principal,
      Store store, long labelOnum) {
    // Allow the store's worker principal to do anything. We use pointer
    // equality here to avoid having to call into the worker.
    if (principal == Worker.getWorker().getPrincipal()) return true;

    if (checkAuthorizationCache(cachedWriteAuthorizations, principal, store,
        labelOnum)) return true;

    // Call into the Jif label framework to perform the label check.
    final Label label = new Label._Proxy(store, labelOnum);
    boolean result = Worker.runInSubTransaction(new Worker.Code<Boolean>() {
      @Override
      public Boolean run() {
        return LabelUtil._Impl.isWritableBy(label, principal);
      }
    });

    if (result) {
      cacheAuthorization(cachedWriteAuthorizations, principal, store, labelOnum);
    }
    return result;
  }
}
