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

/**
 * This encapsulates the version of Fabric.
 *
 * DO NOT EDIT THIS CLASS BY HAND!  This is automatically generated by Ant.  If
 * you wish to change the version number, run one of the following:
 *
 *   ant bump-version  (Bumps the version number.)
 *   ant bump-major    (Bumps the major version number.)
 *   ant bump-minor    (Bumps the minor version number.)
 *   ant bump-patch    (Bumps the patch level.)
 */
public class Version {
  private int major = 0;
  private int minor = 2;
  private int patch = 2;

  public int major() { return major; }
  public int minor() { return minor; }
  public int patch() { return patch; }

  @Override
  public String toString() {
    return "0.2.2 (2014-07-08 22:46:41 EDT)";
  }
}
