/**
 * Copyright (C) 2010-2012 Fabric project group, Cornell University
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
package fabric.extension;

import jif.ast.Jif_c;
import jif.types.ConstraintMessage;
import jif.types.JifContext;
import jif.types.LabelConstraint;
import jif.types.NamedLabel;
import jif.types.label.AccessPath;
import jif.types.label.AccessPathUninterpreted;
import jif.types.label.ConfPolicy;
import jif.types.label.Label;
import jif.visit.LabelChecker;
import polyglot.ast.Expr;
import polyglot.ast.NullLit;
import polyglot.ast.Receiver;
import polyglot.ast.Special;
import polyglot.ast.TypeNode;
import polyglot.types.SemanticException;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import fabric.types.FabricReferenceType;
import fabric.types.FabricTypeSystem;

/**
 * A class containing code for checking access labels on dereferences.
 */
public class DereferenceHelper {
  /**
   * Adds constraints to lc reflecting the fetch side effects of a dereference
   */
  public static void checkDereference(final Receiver ref, LabelChecker lc,
      Position pos) throws SemanticException {
    FabricTypeSystem ts = (FabricTypeSystem) lc.typeSystem();

    // All classes referred to in executing code have already been fetched
    // during typechecking. Thus static dispatches do not cause fetches
    if (ref instanceof TypeNode) return;

    // this and super are known to be resident when a method is executing. Thus
    // they do not cause side effects when dereferenced.
    if (ref == null || ref instanceof Special) return;

    if (!(ref instanceof Expr))
      throw new InternalCompilerError("unexpected receiver type");

    // get the type of the target
    FabricReferenceType refType = (FabricReferenceType) ts.unlabel(ref.type());

    checkAccess((Expr) ref, refType, lc, pos);
  }

  /**
   * Adds constraints to lc to reflect that ref influences a fetch of something
   * of targetType. For example, the code
   * 
   * <pre>
   * C1 x;
   * (C2) x;
   * </pre>
   * 
   * will cause a flow from the label of x to the access label of C2;
   * checkAccess(x, C2, ...) will add constraints reflecting that. Assumes that
   * ref has already been label checked.
   */
  public static void checkAccess(final Expr ref,
      final FabricReferenceType targetType, LabelChecker lc, Position pos)
      throws SemanticException {
    FabricTypeSystem ts = (FabricTypeSystem) lc.typeSystem();

    // if the ref is a null literal, then the access label check is not required
    if (ref instanceof NullLit) return;

    // Dereferencing a reference with a transient type does not result in a fetch unless
    // 1) it is java.lang.Object (could refer to a fabric.lang.Object)
    // 2) it is an Interface (could be implemented by a fabric.lang.Object)
    if (ts.isTransient(ref.type())
        && !ref.type().equals(ts.Object())
        && !(ref.type().isClass() && ref.type().toClass().flags().isInterface()))
      return;

    // check that the pc and ref label can flow to the access label
    JifContext A = lc.context();
    Label objLabel = Jif_c.getPathMap(ref).NV();
    Label pc = ts.join(Jif_c.getPathMap(ref).N(), A.currentCodePCBound());
    AccessPath storeap = ts.storeAccessPathFor(ref, A);
    if (ts.descendsFrom(targetType, ts.DelegatingPrincipal())) {
      if (ts.isFinalAccessExpr(ref)) {
        // this.store >= this holds true for all principals
        A.addActsFor(ts.dynamicPrincipal(pos, storeap),
            ts.dynamicPrincipal(pos, ts.exprToAccessPath(ref, A)));
      } else {
        // ref is not a final access path, so make an uninterpreted path instead
        A.addActsFor(ts.dynamicPrincipal(pos, storeap),
            ts.dynamicPrincipal(pos, new AccessPathUninterpreted(ref, pos)));
      }
    }

    // get the access label of the type
    final ConfPolicy accessPolicy = targetType.accessPolicy();
    final Label accessLabel = ts.toLabel(accessPolicy);
    final Label instantiated =
        StoreInstantiator.instantiate(accessLabel, A, ref, targetType,
            objLabel, storeap);

    lc.constrain(new NamedLabel("reference label", objLabel),
        LabelConstraint.LEQ, new NamedLabel("access label", instantiated),
        A.labelEnv(), pos, new ConstraintMessage() {
          @Override
          public String msg() {
            return "Dereferencing " + ref + " may cause it to be "
                + "fetched, revealing too much information to its " + "store";
          }
        });
    lc.constrain(new NamedLabel("pc", pc), LabelConstraint.LEQ, new NamedLabel(
        "access label", instantiated), A.labelEnv(), pos,
        new ConstraintMessage() {
          @Override
          public String msg() {
            return "Dereferencing " + ref + " may cause it to be "
                + "fetched, revealing too much information to its " + "store";
          }
        });
  }
}
