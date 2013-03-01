package fabil.visit;

import polyglot.ast.Expr;
import polyglot.ast.IntLit;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.Receiver;
import polyglot.qq.QQ;
import polyglot.types.Type;
import polyglot.util.Position;
import polyglot.visit.NodeVisitor;
import fabil.ExtensionInfo;
import fabil.extension.FabILExt;
import fabil.types.FabILTypeSystem;

/**
 * Rewrites the <code>atomic</code> construct.
 */
public class MemoizedMethodRewriter extends NodeVisitor {
  protected QQ qq;
  protected NodeFactory nf;
  protected FabILTypeSystem ts;
  protected Receiver tm;

  public MemoizedMethodRewriter(ExtensionInfo extInfo) {
    this.qq = new QQ(extInfo);
    this.nf = extInfo.nodeFactory();

    ts = extInfo.typeSystem();
    Position CG = Position.compilerGenerated();
    this.tm =
        nf.Call(CG, nf.CanonicalTypeNode(CG, ts.TransactionManager()),
            nf.Id(CG, "getInstance"));
  }

  protected FabILExt ext(Node n) {
    return (FabILExt) n.ext();
  }

  @Override
  public Node leave(Node old, Node n, NodeVisitor v) {
    Node result = ext(n).rewriteMemoizedMethods(this);

    /*
    // XXX HACK!
    if (old instanceof MethodDecl) {
      MethodDecl md = (MethodDecl) old;
      MethodInstance mi = md.methodInstance();

      if (mi != null && !mi.returnType().equals(ts.Void()) && md.body() != null) {
        boolean endWithAtomic = false;
        for (Stmt s : md.body().statements()) {
          if (s instanceof Atomic) {
            endWithAtomic = true;
          } else {
            endWithAtomic = false;
          }
        }

        if (endWithAtomic) {
          // Add a dummy return statement to fool the Java flow checker.
          // It never executes.
          MethodDecl newMd = (MethodDecl) result;
          List<Stmt> stmts =
              new ArrayList<Stmt>(newMd.body().statements().size() + 1);
          stmts.addAll(newMd.body().statements());
          stmts.add(nf.Return(Position.compilerGenerated(),
              getDefaultValue(mi.returnType())));
          result = newMd.body(nf.Block(Position.compilerGenerated(), stmts));
        }
      }
    }
    */

    return result;
  }

  /**
   * @return the qq
   */
  public QQ qq() {
    return qq;
  }

  /**
   * @return the NodeFactory to use in code transformations
   */
  public NodeFactory nodeFactory() {
    return nf;
  }

  /**
   * @return the TypeSystem
   */
  public FabILTypeSystem typeSystem() {
    return ts;
  }

  /**
   * @return the receiver expression to get the current transaction manager for
   * the code currently being transformed.
   */
  public Receiver transactionManager() {
    return tm;
  }

  /**
   * @param t the type for the default value
   * @return the default value (false, 0, or null)
   */
  public Expr getDefaultValue(Type t) {
    if (t.equals(ts.Boolean())) {
      return nf.BooleanLit(Position.compilerGenerated(), false);
    } else if (t.isPrimitive()) {
      return nf.IntLit(Position.compilerGenerated(), IntLit.INT, 0);
    } else {
      return nf.NullLit(Position.compilerGenerated());
    }
  }
}
