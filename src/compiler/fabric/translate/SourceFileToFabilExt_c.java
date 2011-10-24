package fabric.translate;

import jif.translate.JifToJavaRewriter;
import jif.translate.SourceFileToJavaExt_c;
import polyglot.ast.Node;
import polyglot.ast.PackageNode;
import polyglot.frontend.Source;
import polyglot.types.SemanticException;
import codebases.ast.CBSourceFile;
import codebases.frontend.CodebaseSource;
import fabil.ast.FabILNodeFactory;
import fabric.visit.FabricToFabilRewriter;

public class SourceFileToFabilExt_c extends SourceFileToJavaExt_c {
  @Override
  public Node toJava(JifToJavaRewriter rw) throws SemanticException {
    CBSourceFile n = (CBSourceFile) node();
    Source source = n.source();
    FabricToFabilRewriter ftfr = (FabricToFabilRewriter) rw;
    PackageNode pkgNode = n.package_();
    FabILNodeFactory nf = (FabILNodeFactory) rw.java_nf();

    n = (CBSourceFile) nf.SourceFile(n.position(), pkgNode, n.codebaseDecls(),
            n.imports(), n.decls());

    // Create a source derived from the fabric one. This is in support
    // of compiling to bytecode after publishing
    source = ftfr.createDerivedSource((CodebaseSource) source, source.name());
    n = (CBSourceFile) n.source(source);
    return rw.leavingSourceFile(n);
  }
}
