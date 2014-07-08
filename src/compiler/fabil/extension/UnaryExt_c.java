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
package fabil.extension;

import java.util.Collections;

import polyglot.ast.Call;
import polyglot.ast.Expr;
import polyglot.ast.Field;
import polyglot.ast.Receiver;
import polyglot.ast.Unary;
import fabil.visit.ProxyRewriter;
import fabil.visit.ReadWriteChecker.State;

public class UnaryExt_c extends ExprExt_c {

  private State accessState;

  @Override
  public Expr rewriteProxiesOverrideImpl(ProxyRewriter rewriter) {
    // Handle (pre/post)-(inc/dec)rement on fields.
    Unary unary = node();
    Expr expr = unary.expr();
    if (!(expr instanceof Field)) return null;

    Unary.Operator op = unary.operator();
    if (op != Unary.POST_DEC && op != Unary.POST_INC && op != Unary.PRE_DEC
        && op != Unary.PRE_INC) return null;

    if (accessState != null) {
      Field f = (Field) expr;
      Receiver target = f.target();
      target = rewriter.replaceTarget(target, accessState);
      f = f.target(target);
      expr = f;

      if (accessState.all()) {
        return unary.expr(expr);
      }
    }

    Expr getter = (Expr) unary.visitChild(expr, rewriter);
    if (getter instanceof Call) {
      Call getterCall = (Call) getter;
      if (op.isPrefix()) {
        // XXX Hacky. Mangle the getter call to obtain a setter call.
        String name = getterCall.name();
        name = "set" + name.substring(3);

        Call setterCall = getterCall.name(name);
        Expr arg =
            rewriter.qq().parseExpr(
                "%E " + (op == Unary.PRE_DEC ? "-" : "+") + " 1", getterCall);
        return (Expr) setterCall.arguments(Collections.singletonList(arg));
      }

      // XXX Hacky. Mangle the getter call to obtain a post-inc/dec call.
      String name = getterCall.name();
      name = (op == Unary.POST_DEC ? "postDec" : "postInc") + name.substring(3);
      return getterCall.name(name);
    } else {
      return unary.expr(getter);
    }
  }

  @Override
  public Unary node() {
    return (Unary) super.node();
  }

  public void accessState(State s) {
    this.accessState = s;
  }

  public State accessState() {
    return accessState;
  }

}
