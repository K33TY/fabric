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
package fabric.dissemination.pastry.messages;

import fabric.dissemination.pastry.Disseminator;

/**
 * A self-scheduled message triggered once every aggregation interval.
 */
public class AggregateInterval implements Message {

  @Override
  public int getPriority() {
    return LOW_PRIORITY;
  }

  @Override
  public void dispatch(Disseminator disseminator) {
    disseminator.aggregateInterval();
  }

}
