package fabric.ast;

import jif.ast.JifExt_c;
import jif.translate.ToJavaExt;
import jif.visit.LabelChecker;
import polyglot.ast.Ext;
import polyglot.ast.Node;
import polyglot.types.SemanticException;

public class AccessPolicyJifExt_c extends JifExt_c implements Ext {

  public AccessPolicyJifExt_c(ToJavaExt toJava) {
    super(toJava);
  }

  @Override
  public Node labelCheck(LabelChecker lc) throws SemanticException {
    return super.labelCheck(lc);
  }
}
