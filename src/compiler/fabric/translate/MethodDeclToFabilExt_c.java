package fabric.translate;

import jif.translate.JifToJavaRewriter;
import jif.translate.MethodDeclToJavaExt_c;
import polyglot.ast.Block;
import polyglot.ast.If;
import polyglot.ast.MethodDecl;
import polyglot.ast.Node;
import polyglot.types.SemanticException;
import polyglot.util.Position;
import fabil.ast.FabILNodeFactory;

public class MethodDeclToFabilExt_c extends MethodDeclToJavaExt_c {
  @Override
  public Node toJava(JifToJavaRewriter rw) throws SemanticException {
    MethodDecl md = (MethodDecl) super.toJava(rw);

    if (md.body() == null) {
      // abstract method
      return md;
    }

    FabILNodeFactory nf = (FabILNodeFactory) rw.nodeFactory();

    if (md.name().endsWith("_remote")) {
      // Fabric wrapper
      // Rewrite the else block to throw an exception
      If ifStmt = (If) md.body().statements().get(0);
      ifStmt =
          ifStmt
              .alternative(rw
                  .qq()
                  .parseStmt(
                      "throw new fabric.worker.remote.RemoteCallLabelCheckFailedException();"));
      return md.body(nf.Block(Position.compilerGenerated(), ifStmt));
    }

    return md;
  }

  @Override
  protected Block guardWithConstraints(JifToJavaRewriter rw, Block b)
      throws SemanticException {
    boolean shouldGuard = true;
    if (shouldGuard) {
      @SuppressWarnings("unchecked")
      Block newBody =
          ((FabILNodeFactory) rw.java_nf()).Atomic(b.position(), super
              .guardWithConstraints(rw, b).statements());
      return newBody;
    }
    return b;
  }
}
