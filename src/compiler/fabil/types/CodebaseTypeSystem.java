package fabil.types;

import polyglot.frontend.ExtensionInfo;
import polyglot.frontend.Source;
import polyglot.types.ClassType;
import polyglot.types.LazyClassInitializer;
import polyglot.types.Named;
import polyglot.types.Package;
import polyglot.types.TopLevelResolver;
import polyglot.types.TypeSystem;

public interface CodebaseTypeSystem extends TypeSystem {

  CodebaseImportTable importTable(Source source, Package pkg);

  boolean isPlatformType(Named name);

  boolean isPlatformType(String fullName);

  CodebaseClassContextResolver createClassContextResolver(ClassType type);

  CodebasePackageContextResolver createPackageContextResolver(Package p);

  CodebaseSystemResolver createSystemResolver(TopLevelResolver loadedResolver,
      ExtensionInfo extInfo);

  CodebasePackage createPackage(Package prefix, String name);

  CodebaseClassType createClassType(LazyClassInitializer init, Source fromSource);
}
