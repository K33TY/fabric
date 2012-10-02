package fabric.ast;

import java.util.Collections;
import java.util.List;

import polyglot.ast.Expr;
import polyglot.ast.Expr_c;
import polyglot.ast.Node;
import polyglot.ast.Special;
import polyglot.ast.Term;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.types.TypeSystem;
import polyglot.util.Position;
import polyglot.visit.CFGBuilder;
import polyglot.visit.NodeVisitor;
import polyglot.visit.TypeChecker;
import fabric.types.FabricTypeSystem;

/**
 * 
 */
public class Store_c extends Expr_c implements Store {
  protected Expr expr;
  protected boolean isLocal;
  @Override
  public Object copy() {
    Store s = (Store) super.copy();
    return s;
  }

  /**
   * @param pos
   */
  public Store_c(Position pos, Expr expr) {
    super(pos);
    this.expr = expr;
  }

  @Override
  public Expr expr() {
    return expr;
  }

  @Override
  public Expr expr(Expr expr) {
    if (this.expr == expr) {
      return this;
    }
    Store_c n = (Store_c) copy();
    n.expr = expr;
    return n;
  }

  @Override
  public Term firstChild() {
    return expr;
  }

  @Override
  public <T> List<T> acceptCFG(CFGBuilder<?> v, List<T> succs) {
    v.visitCFG(expr, this, EXIT);
    return succs;
  }

  @Override
  public Node visitChildren(NodeVisitor v) {
    Expr e = (Expr) visitChild(this.expr, v);
    return this.expr(e);
  }

  @Override
  public boolean isTypeChecked() {
    return expr.isTypeChecked() && type().isCanonical();
  }

  @Override
  public boolean isDisambiguated() {
    return expr.isDisambiguated();
  }

  /** Type check the expression. */
  @Override
  public Node typeCheck(TypeChecker tc) throws SemanticException {
    if (!expr.type().isCanonical()) return this;
    if (type().isCanonical()) return this;

    Type t = expr.type();
    if (!t.isReference())
      throw new SemanticException("Cannot get store of non reference type.");
    FabricTypeSystem ts = (FabricTypeSystem) tc.typeSystem();

    return isLocalStore(ts.isTransient(t)).type(ts.Store());
  }


  @Override
  public List<Type> throwTypes(TypeSystem ts) {

    if (!(expr instanceof Special)) {
      return Collections.singletonList((Type) ts.NullPointerException());
    }
    return Collections.<Type> emptyList();
  }

  @Override
  public boolean exprIsNeverNull() {
    //TODO: need to extend NotNullChecker to make this work in general
    return expr instanceof Special;
  }

  @Override
  public boolean isLocalStore() {
    return isLocal;
  }

  protected Expr isLocalStore(boolean isLocal) {
    if (this.isLocal == isLocal) {
      return this;
    }
    Store_c n = (Store_c) copy();
    n.isLocal = isLocal;
    return n;
  }

}
