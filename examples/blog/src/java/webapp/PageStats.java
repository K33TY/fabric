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
package webapp;

public class PageStats {
  public int numReads;
  public int numUpdates;
  public int numCreates;
  public int numTransactions;
  public int transactionTime;
  public long pageLoadTime;
  public long appTime;
  public long transmissionTime;

  public PageStats() {}

  public int getNumReads() {
    return numReads;
  }

  public int getNumUpdates() {
    return numUpdates;
  }

  public int getNumCreates() {
    return numCreates;
  }

  public int getNumTransactions() {
    return numTransactions;
  }

  public int getTransactionTime() {
    return transactionTime;
  }

  public long getPageLoadTime() {
    return pageLoadTime;
  }

  public long getAppTime() {
    return appTime;
  }

  public long getTrasmissionTime() {
    return transmissionTime;
  }

  public String getSerialized() {
    StringBuilder str = new StringBuilder();
    str.append(numReads).append(',').append(numUpdates).append(',').append(
        numCreates);
    str.append(',').append(numTransactions).append(',').append(transactionTime);
    str.append(',').append(pageLoadTime).append(',').append(appTime);
    return str.toString();
  }

  public static PageStats fromSerialized(String str) {
    String[] splits = str.split(",");
    PageStats stats = new PageStats();
    int i = 0;
    stats.numReads = Integer.parseInt(splits[i++]);
    stats.numUpdates = Integer.parseInt(splits[i++]);
    stats.numCreates = Integer.parseInt(splits[i++]);
    stats.numTransactions = Integer.parseInt(splits[i++]);
    stats.transactionTime = Integer.parseInt(splits[i++]);
    stats.pageLoadTime = Long.parseLong(splits[i++]);
    stats.appTime = Long.parseLong(splits[i++]);
    return stats;
  }

}
