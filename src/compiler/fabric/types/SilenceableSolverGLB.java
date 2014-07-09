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
package fabric.types;

import jif.types.Equation;
import jif.types.JifTypeSystem;
import jif.types.SolverGLB;
import jif.types.UnsatisfiableConstraintException;
import jif.types.VarMap;
import polyglot.types.SemanticException;

/**
 * <code>SilenceableSolverGLB</code> can be muted, that is, not reporting any
 * error. TODO: this class seems totally broken, as it causes the solver to
 * swallow some but not all constraint solution failures.
 * 
 * @author qixin
 */
public class SilenceableSolverGLB extends SolverGLB {
  private static boolean muted;
  // XXX Temporarily set to true
  private static boolean JUST_PROCEED = true;

  public SilenceableSolverGLB(JifTypeSystem ts,
      polyglot.frontend.Compiler compiler, String solverName) {
    super(ts, compiler, solverName);
  }

  protected SilenceableSolverGLB(SolverGLB js) {
    super(js);
  }

  public static boolean muted() {
    return muted;
  }

  public static void mute(boolean b) {
    muted = b;
  }

  public boolean isSolved() {
    return status == STATUS_SOLVED;
  }

  @Override
  public VarMap solve() throws SemanticException {
    if (muted()) {
      // check for static failures.
      if (staticFailedConstraints != null && !staticFailedConstraints.isEmpty()) {
        if (shouldReport(1)) {
          report(1, "   " + staticFailedConstraints.size()
              + " statically failed constraint");
        }

        if (JUST_PROCEED) {
          // XXX Temporarily ignore information leaks caused by runtime checks.
          staticFailedConstraints.clear();
        } else {
          setStatus(STATUS_NO_SOLUTION);

          for (Equation eqn : staticFailedConstraints) {
            UnsatisfiableConstraintException e = reportError(eqn);

            System.err
                .println("Runtime check does not type-check, due to the folloiwing:\n"
                    + e.getMessage());
          }

          staticFailedConstraints.clear();
        }
      }
    }

    return super.solve();
  }

}
