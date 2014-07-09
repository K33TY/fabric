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
package fabric.store.db;

import java.security.PrivateKey;

import fabric.common.AuthorizationUtil;
import fabric.common.ObjectGroup;
import fabric.common.SerializedObject;
import fabric.common.exceptions.InternalError;
import fabric.common.util.LongSet;
import fabric.dissemination.ObjectGlob;
import fabric.lang.security.Principal;
import fabric.worker.Store;

/**
 * A group container contains a group or a glob, and supports converting between
 * them. This class is thread-safe.
 */
public final class GroupContainer extends ObjectGrouper.AbstractGroup {
  private final Store store;
  private final long labelOnum;
  private final PrivateKey signingKey;

  private ObjectGroup group;
  private ObjectGlob glob;

  /**
   * The set of onums for the objects contained in this group.
   */
  public final LongSet onums;

  public GroupContainer(Store store, PrivateKey signingKey, ObjectGroup group) {
    this.store = store;
    this.signingKey = signingKey;
    this.group = group;
    this.glob = null;

    this.onums = group.objects().keySet();

    for (SerializedObject obj : group.objects().values()) {
      this.labelOnum = obj.getUpdateLabelOnum();
      return;
    }

    // Shouldn't happen.
    throw new InternalError("Empty object group");
  }

  /**
   * @param principal
   *          The principal accessing the group.
   * @return null if the given principal is not allowed to read the group.
   */
  public ObjectGroup getGroup(Principal principal) {
    if (!AuthorizationUtil.isReadPermitted(principal, store, labelOnum))
      return null;

    ObjectGroup group;
    ObjectGlob glob;
    synchronized (this) {
      group = this.group;
      glob = this.glob;
    }

    if (group != null) return group;
    return glob.decrypt();
  }

  public ObjectGlob getGlob() {
    if (glob != null) return glob;
    synchronized (this) {
      if (glob == null) {
        glob = new ObjectGlob(store, group, signingKey);
        group = null;
      }
      return glob;
    }
  }

  @Override
  protected LongSet onums() {
    return onums;
  }
}
