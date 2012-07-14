package fabric.translate;

import jif.translate.ConjunctivePrincipalToJavaExpr_c;
import jif.translate.JifToJavaRewriter;
import jif.types.JifTypeSystem;
import jif.types.principal.ConjunctivePrincipal;
import jif.types.principal.Principal;
import polyglot.ast.Expr;
import polyglot.types.SemanticException;
import fabric.visit.FabricToFabilRewriter;

public class ConjunctivePrincipalToFabilExpr_c extends
    ConjunctivePrincipalToJavaExpr_c {
  @Override
  public Expr toJava(Principal principal, JifToJavaRewriter rw)
      throws SemanticException {
    FabricToFabilRewriter ffrw = (FabricToFabilRewriter) rw;
    JifTypeSystem ts = rw.jif_ts();
    Expr e = null;
    ConjunctivePrincipal cp = (ConjunctivePrincipal) principal;
    for (Principal p : cp.conjuncts()) {
      Expr pe = rw.principalToJava(p);
      if (e == null) {
        e = pe;
      } else {
        e =
            rw.qq().parseExpr(
                ts.PrincipalUtilClassName() + ".conjunction(%E, %E, %E)",
                ffrw.currentLocation(), pe, e);
      }
    }
    return e;
  }
}
