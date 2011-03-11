package fabil.types;
import polyglot.types.Package;
import polyglot.types.Package_c;
import fabil.frontend.CodebaseSource;
import fabric.lang.Codebase;

public class CodebasePackage_c extends Package_c implements CodebasePackage {
  protected Codebase codebase = null;
  protected CodebaseSource source;

  public CodebasePackage_c(CodebaseTypeSystem ts, Package prefix, String name) {
    super(ts, prefix, name);
  }

  public Codebase codebase() {
    return codebase;
  }

  public CodebasePackage codebase(Codebase cb) {
    if (cb == null && codebase == null) return this;

    if (codebase != null && cb != null && codebase.equals(cb)) return this;

    CodebasePackage_c cbp = (CodebasePackage_c) copy();
    cbp.codebase = cb;
    return cbp;
  }
  
  public CodebasePackage source(CodebaseSource source) {
    CodebasePackage_c cbp = (CodebasePackage_c) copy();
    cbp.source = source;
    return cbp;
  }
  
  public CodebaseSource source() {
    return source;
  }

}
