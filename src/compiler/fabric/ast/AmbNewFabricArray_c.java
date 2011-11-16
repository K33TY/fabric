package fabric.ast;

import java.util.List;

import jif.ast.AmbNewArray_c;
import polyglot.ast.Expr;
import polyglot.ast.NewArray;
import polyglot.ast.Node;
import polyglot.ast.TypeNode;
import polyglot.types.SemanticException;
import polyglot.util.Position;
import polyglot.visit.AmbiguityRemover;

public class AmbNewFabricArray_c extends AmbNewArray_c implements
    AmbNewFabricArray {

  protected Expr loc;

  public AmbNewFabricArray_c(Position pos, TypeNode baseType, Expr loc,
      Object expr, List<Expr> dims, int addDims) {
    super(pos, baseType, expr, dims, addDims);
    this.loc = loc;
  }

  @Override
  public Expr location() {
    return this.loc;
  }

  @Override
  public AmbNewFabricArray location(Expr loc) {
    AmbNewFabricArray_c result = (AmbNewFabricArray_c) copy();
    result.loc = loc;
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node disambiguate(AmbiguityRemover ar) throws SemanticException {
    Expr expr = (Expr) super.disambiguate(ar);

    if (expr != this) {
      // Superclass successfully disambiguated. Hijack its result and return a
      // NewFabricArray instance.
      FabricNodeFactory nf = (FabricNodeFactory) ar.nodeFactory();
      NewArray newArray = (NewArray) expr;
      return nf.NewFabricArray(newArray.position(), newArray.baseType(),
          location(), newArray.dims(), newArray.additionalDims(),
          (FabricArrayInit) newArray.init());
    }

    return this;
  }
}
