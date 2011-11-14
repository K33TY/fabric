package fabric.translate;

import jif.translate.JifToJavaRewriter;
import jif.translate.NewArrayToJavaExt_c;
import jif.types.label.Label;
import polyglot.ast.Expr;
import polyglot.ast.Node;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.visit.NodeVisitor;
import fabil.ast.FabILNodeFactory;
import fabil.ast.FabricArrayInit;
import fabric.ast.FabricUtil;
import fabric.ast.NewFabricArray;
import fabric.extension.NewFabricArrayExt_c;
import fabric.types.FabricClassType;
import fabric.types.FabricTypeSystem;
import fabric.visit.FabricToFabilRewriter;

public class NewFabricArrayToFabilExt_c extends NewArrayToJavaExt_c {
  protected Type baseType;

  @Override
  public NodeVisitor toJavaEnter(JifToJavaRewriter rw) {
    NewFabricArray n = (NewFabricArray) node();
    baseType = n.baseType().type();
    return rw;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node toJava(JifToJavaRewriter rw) throws SemanticException {
    NewFabricArray n = (NewFabricArray) node();
    NewFabricArrayExt_c ext = (NewFabricArrayExt_c) FabricUtil.fabricExt(n);

    FabILNodeFactory nf = (FabILNodeFactory) rw.nodeFactory();
    FabricTypeSystem ts = (FabricTypeSystem) rw.jif_ts();

    Type base = baseType;
    while (base.isArray()) {
      base = base.toArray().base();
    }

    Expr labelloc = ext.location();
    if (labelloc == null) labelloc = nf.StoreGetter(n.position());
    //push the new location
    rw = ((FabricToFabilRewriter)rw).pushLocation(labelloc);

    Label baseLabel = null;
    Expr labelExpr = null;
    if (base instanceof FabricClassType && ts.isFabricClass(base)) {
      baseLabel = ((FabricClassType)base).classUpdateLabel();
      if (baseLabel != null) {
        labelExpr = rw.labelToJava(baseLabel);      
      }
    }
    
    Label accessLabel = null;
    Expr accessLabelExpr = null;
    if (base instanceof FabricClassType && ts.isFabricClass(base)) {
      accessLabel = ((FabricClassType)base).classAccessLabel();
      if (accessLabel != null) { 
        accessLabelExpr = rw.labelToJava(accessLabel);
      }
    }

    return nf.NewFabricArray(n.position(), n.baseType(), labelExpr, accessLabelExpr, ext.location(), n
        .dims(), n.additionalDims(), (FabricArrayInit) n.init());
  }
}
