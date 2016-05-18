package fabric.translate;

import java.util.ArrayList;
import java.util.List;

import fabil.ast.FabILNodeFactory;

import fabric.ast.FabricUtil;
import fabric.extension.FabricStagingExt;
import fabric.visit.FabricToFabilRewriter;

import jif.translate.ConstructorCallToJavaExt_c;
import jif.translate.JifToJavaRewriter;

import polyglot.ast.ConstructorCall;
import polyglot.ast.Expr;
import polyglot.ast.Node;
import polyglot.types.SemanticException;

public class ConstructorCallToFabilExt_c extends ConstructorCallToJavaExt_c {

  @Override
  public Node toJava(JifToJavaRewriter rw) throws SemanticException {
    FabricToFabilRewriter frw = (FabricToFabilRewriter) rw;
    ConstructorCall orig = (ConstructorCall) node();
    ConstructorCall call = (ConstructorCall) super.toJava(frw);
    FabricStagingExt fse = FabricUtil.fabricStagingExt(orig);
    FabILNodeFactory nf = (FabILNodeFactory) frw.java_nf();

    // Add in staging.
    if (call.arguments().size() > 0) {
      // Wrap the last argument
      int lastIdx = call.arguments().size() - 1;
      List<Expr> args = new ArrayList<>(call.arguments());
      args.set(lastIdx, fse.stageCheck(frw, orig, args.get(lastIdx)));
      call = (ConstructorCall) call.arguments(args);
    } else if (fse.nextStage() != null) {
      // Use a ternary operator.
      return rw.qq().parseExpr("%E ? %E : %E",
            fse.stageCheck(frw, orig, nf.BooleanLit(call.position(), true)),
            call,
            call);
    }

    return call;
  }
}
