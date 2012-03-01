package fabric.translate;

import java.util.ArrayList;
import java.util.List;

import jif.translate.CallToJavaExt_c;
import jif.translate.JifToJavaRewriter;
import polyglot.ast.Expr;
import polyglot.types.SemanticException;
import polyglot.util.Position;
import fabil.ast.FabILCall;
import fabil.ast.FabILNodeFactory;
import fabric.ast.FabricCall;

public class CallToFabilExt_c extends CallToJavaExt_c {
  @SuppressWarnings("unchecked")
  @Override
  public Expr exprToJava(JifToJavaRewriter rw) throws SemanticException {
    FabricCall c = (FabricCall)node();
    Expr e = super.exprToJava(rw);
    if (e instanceof FabILCall) {
      FabILNodeFactory nf = (FabILNodeFactory)rw.java_nf();
      
      FabILCall result = (FabILCall)e;
//      // Make sure that the first argument is a label
//      if (result.methodInstance() == null) return result;
//      List formalTypes = result.methodInstance().formalTypes();
//      if (formalTypes.size() <= 0 || !formalTypes.get(0).equals(((FabricTypeSystem)rw.typeSystem()).Label())) 
//        throw new SemanticException("Method " + result.id() + " cannot be called remotely since its first argument is not of label type");
      if (c.remoteWorker() != null) {
        result = (FabILCall)result.name(result.name() + "_remote");
        result = result.remoteWorker(c.remoteWorker());
        List<Expr> args = new ArrayList<Expr>(result.arguments().size());
        // The first argument is actually a principal.
        args.add(nf.Call(Position.compilerGenerated(),
                         rw.qq().parseExpr("worker$"),
//                         nf.Local(Position.compilerGenerated(), nf.Id(Position.compilerGenerated(), "worker$")), 
                         nf.Id(Position.compilerGenerated(), "getPrincipal")));
//        args.addAll(result.arguments().subList(1, result.arguments().size()));
        args.addAll(result.arguments());
        result = (FabILCall)result.arguments(args);
      }

      return result;
    }
    return e;
  }
}
