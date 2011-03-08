package fabric.types;

import fabric.lang.Codebase;
import fabric.lang.FClass;
import fabric.worker.Store;
import polyglot.frontend.Source;
import polyglot.types.ClassType;
import polyglot.types.LocalInstance;
import polyglot.types.Named;
import polyglot.types.Package;
import polyglot.types.Type;
import polyglot.util.Position;
import jif.types.JifContext;
import jif.types.JifTypeSystem;
import jif.types.label.AccessPath;
import jif.types.label.ConfPolicy;
import jif.types.label.IntegPolicy;
import jif.types.label.Label;
import jif.types.principal.*;

public interface FabricTypeSystem extends JifTypeSystem {
  ClassType FObject();
  
  ClassType RemoteWorker();
  
  ClassType Worker();
  
  ClassType Store();

  ClassType DelegatingPrincipal();
  /**
   * Constructs a principal for the local worker. 
   * 
   * Remote workers directly use <code>DynamicPrincipal</code>.
   * 
   * @param pos
   * @return
   */
  Principal workerPrincipal(Position pos);
  
  LocalInstance workerLocalInstance();
  
  Type strip(Type type);
  
  boolean isLocalWorkerAccessPath(AccessPath ap);
  
  ConfPolicy representableConfProjection(Label L);
  IntegPolicy representableIntegProjection(Label L);
  
  /**
   * Checks whether <code>type</code> is a Fabric class, 
   * that is, inherits <code>fabric.lang.Object</code>.
   */
  boolean isFabricClass(Type type);
  
  boolean isFabricArray(Type type);
  FabricArrayType toFabricArray(Type type);
  
  boolean containsThisLabel(Label label);

  FabricArrayType fabricArrayOf(Position pos, Type t);
  FabricArrayType fabricArrayOf(Position pos, Type t, int dims);

  boolean isPlatformType(Named dep);

  void flushSystemResolver();
}
