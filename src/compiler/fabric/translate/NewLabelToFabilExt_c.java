package fabric.translate;

import java.util.ArrayList;
import java.util.List;

import fabric.ast.FabricUtil;
import fabric.extension.NewLabelExt_c;
import polyglot.ast.*;
import polyglot.types.SemanticException;
import jif.translate.JifToJavaRewriter;
import jif.translate.NewLabelToJavaExt_c;

public class NewLabelToFabilExt_c extends NewLabelToJavaExt_c {
  @SuppressWarnings("unchecked")
  @Override
  public Node toJava(JifToJavaRewriter rw) throws SemanticException {
    NewLabelExt_c ext = (NewLabelExt_c)FabricUtil.fabricExt(node());
    Expr loc = ext.location();
    
    Node n = super.toJava(rw);
    
    if (loc != null && n instanceof Call) {
      // Add loc as the first parameter.
      // Add this recursively for all parameters which are themselves instance of Call
      this.loc = loc;
      Call c = (Call)n;
      return addLoc(c);
    }

    return n;
  }
  
  private Expr loc;
  private Call addLoc(Call c) {
    List<Expr> args = new ArrayList<Expr>(c.arguments().size() + 1);
    args.add(loc);
    for(Expr expr : (List<Expr>)c.arguments()) {
      Expr addExpr = expr;
      if(expr instanceof Call) {
        Call subcall = (Call) expr;
        addExpr = addLoc(subcall);
      }
      args.add(addExpr);
    }
    c = (Call)c.arguments(args);
    return c;
    
  }
}
