package fabil.types;

import fabric.common.SysUtil;
import fabric.lang.Codebase;
import polyglot.types.ClassType;
import polyglot.types.Named;
import polyglot.types.NoClassException;
import polyglot.types.PackageContextResolver;
import polyglot.types.SemanticException;
import polyglot.types.TypeSystem;
import polyglot.util.InternalCompilerError;
import polyglot.util.StringUtil;

public class CodebasePackageContextResolver extends PackageContextResolver {

  public CodebasePackageContextResolver(TypeSystem ts, CodebasePackage p) {
    super(ts, p);
  }

  /**
   * Find a type object by name.
   */
  @Override
  public Named find(String name, ClassType accessor) throws SemanticException {
      if (! StringUtil.isNameShort(name)) {
          throw new InternalCompilerError(
              "Cannot lookup qualified name " + name);
      }
      
      Named n = null;
      CodebaseTypeSystem cbts = (CodebaseTypeSystem) ts;
      Codebase cb = ((CodebasePackage) p).codebase();   
      String fqName;
      if(!cbts.isPlatformType(p))
        fqName = SysUtil.codebasePrefix(cb) + p.fullName() + "." + name;
      else
        fqName = p.fullName() + "." + name;

      try {
            n = ts.systemResolver().find(fqName);
      }
      catch (NoClassException e) {
          // Rethrow if some _other_ class or package was not found.
          if (!e.getClassName().equals(fqName)) {
              throw e;
          }
      }

      if (n == null) {
          n = ts.createPackage(p, name);
      }
      
      if (! canAccess(n, accessor)) {
          throw new SemanticException("Cannot access " + n + " from " + accessor + ".");
      }
      
      return n;
  }

}
