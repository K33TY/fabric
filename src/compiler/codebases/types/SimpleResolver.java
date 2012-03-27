package codebases.types;

import java.net.URI;

import polyglot.frontend.Source;
import polyglot.main.Report;
import polyglot.types.BadSerializationException;
import polyglot.types.Importable;
import polyglot.types.NoClassException;
import polyglot.types.SemanticException;
import polyglot.types.reflect.ClassFile;
import polyglot.types.reflect.ClassPathLoader;
import codebases.frontend.CodebaseSource;
import codebases.frontend.ExtensionInfo;
import codebases.frontend.URISourceLoader;

public class SimpleResolver extends NamespaceResolver_c {
  protected final ClassPathLoader classpathLoader;
  protected final URISourceLoader sourceLoader;
  protected boolean load_raw = false;
  protected boolean load_enc = true;
  protected boolean load_src = true;
  protected boolean ignore_mod_times;
  public SimpleResolver(ExtensionInfo extInfo, URI namespace) {
    this(extInfo, namespace, null);
  }
  public SimpleResolver(ExtensionInfo extInfo, URI namespace, NamespaceResolver parent) {
    super(extInfo, namespace, parent);
    this.classpathLoader = extInfo.classpathLoader(namespace);
    this.sourceLoader = extInfo.sourceLoader(namespace);
    this.ignore_mod_times = extInfo.getOptions().ignore_mod_times;
  }

  @Override
  public Importable findImpl(String name) throws SemanticException {
    String version = extInfo.version().name();
    // Look for a class file first
    if (Report.should_report(REPORT_TOPICS, 3))
      Report.report(3, "NamespaceResolver_c.find(" + name + ")");

    // Find class file.
    ClassFile clazz = null;
    ClassFile encodedClazz = null;
    if (load_enc) clazz = loadFile(name);
    if (clazz != null) {
      // Check for encoded type information.
      if (clazz.encodedClassType(version) != null) {
        if (Report.should_report(REPORT_TOPICS, 4))
          Report.report(4, "Class " + name + " has encoded type info");
        encodedClazz = clazz;
      }
    }

    // Find source file
    Source source = null;
    if (load_src) source = sourceLoader.classSource(name);
    
    // Check if a job for the source already exists.
    if (extInfo.scheduler().sourceHasJob(source)) {
      if (Report.should_report(Report.loader, 4))
        new Exception("Source has job " + source).printStackTrace();

        // the source has already been compiled; what are we doing here?
        return getTypeFromSource(source, name);
    }
    else if (load_enc && encodedClazz == null && source != null) {
      // Found source, but no job.  Check class cache of canonical NS
      CodebaseSource cbsource = (CodebaseSource) source;
      if (!cbsource.canonicalNamespace().isOpaque()
          && !cbsource.canonicalNamespace().equals(namespace)) {
        ClassPathLoader classpathLoader = extInfo.classpathLoader(cbsource.canonicalNamespace());
        ClassFile homeClazz = classpathLoader.loadClass(name);
        if (homeClazz != null) {
          // Check for encoded type information.
          if (homeClazz.encodedClassType(version) != null) {
            if (Report.should_report(REPORT_TOPICS, 4))
              Report.report(4, "Class " + name + " has encoded type info");
            encodedClazz = homeClazz;
          }
        }
      }
    }

    if (Report.should_report(REPORT_TOPICS, 4)) {
      if (source == null)
        Report.report(4, "Class " + name + " not found in source file");
      else Report.report(4, "Class " + name + " found in source " + source);
    }

    // Don't use the raw class if the source or encoded class is available.
    if (encodedClazz != null || source != null) {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Not using raw class file for " + name);
      clazz = null;
    }
    
    if (encodedClazz != null && source != null) {
      long classModTime = encodedClazz.sourceLastModified(version);
      long sourceModTime = source.lastModified().getTime();
      
      if (! ignore_mod_times && classModTime < sourceModTime) {
        if (Report.should_report(REPORT_TOPICS, 3))
            Report.report(3, "Source file version is newer than compiled for " +
                      name + ".");
        encodedClazz = null;
      }
      else if (checkCompilerVersion(encodedClazz.compilerVersion(version)) != COMPATIBLE) {
        if (Report.should_report(REPORT_TOPICS, 3))
          Report.report(3, "Incompatible encoding version for " + name + " in class cache.");
        encodedClazz = null;
      }
    }
    Importable result = null;
    if (encodedClazz != null) {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Using encoded class type for " + name);
      try {
        result = getEncodedType(encodedClazz, name);
      }
      catch (BadSerializationException e) {
        throw e;
      }
      catch (SemanticException e) {
        if (Report.should_report(REPORT_TOPICS, 4))
          Report.report(4, "Could not load encoded class " + name);
        encodedClazz = null;
      }
    }
    // At this point, at most one of clazz and source should be set.
    if (result == null && clazz != null && load_raw) {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Using raw class file for " + name);
      result = extInfo.typeSystem().classFileLazyClassInitializer(clazz).type();
    }
    else {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Not using raw class file for " + name + "( load_raw = " + load_raw + ")" );
    }

    if (result == null && source != null) {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Using source file for " + name);
      result = getTypeFromSource(source, name);
    }
    if ( result != null) {
      return result;
    }
    throw new NoClassException(name);
  }

  /**
   * Load a class file (with encoded type information) for class <code>name</code>.
   * @return ClassFile with encoded type info or null.
   */
  protected ClassFile loadFile(String name) {
    try {
      ClassFile clazz = classpathLoader.loadClass(name);
  
      if (clazz == null) {
        if (Report.should_report(REPORT_TOPICS, 4)) {
          Report.report(4, "ClassFile for " + name + " not found in "
              + namespace);
        }
      } else {
        if (Report.should_report(REPORT_TOPICS, 4)) {
          Report.report(4, "Class " + name + " found in "
              + namespace);
        }
        if (clazz.encodedClassType(extInfo.version().name()) != null) {
          if (Report.should_report(REPORT_TOPICS, 4))
            Report.report(4, "ClassFile for " + name + " has encoded type info");
        }
        return clazz;
      }
    } catch (ClassFormatError e) {
      if (Report.should_report(REPORT_TOPICS, 4))
        Report.report(4, "Class " + name + " format error");
    }
    return null;
  }

  @Override
  public boolean packageExistsImpl(String name) {
    return sourceLoader.packageExists(name);
  }
  
  @Override
  public boolean loadRawClasses(boolean use) {
    boolean old = load_raw;
    load_raw = use;
    return old;
  }
  
  @Override
  public boolean loadEncodedClasses(boolean use) {
    boolean old = load_enc;
    load_enc = use;
    return old;
  }
  
  @Override
  public boolean loadSource(boolean use) {
    boolean old = load_src;
    load_src = use;
    return old;
  }
  
  @Override
  public URI resolveCodebaseNameImpl(String name) {
    // if the name isn't in the cache, 
    // then no codebase alias exists.
    return null;
  }

}
