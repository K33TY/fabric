/**
 * 
 */
package fabric.extension;

import polyglot.ast.Ext;
import polyglot.ast.Node;
import fabric.visit.ProxyRewriter;
import fabric.visit.AtomicRewriter;

/**
 * The interface for all Fabric extension nodes.
 */
public interface FabricExt extends Ext {

  /**
   * Used by ProxyRewriter to rewrite references to proxy references
   */
  public Node rewriteProxies(ProxyRewriter pr);
  
  /**
   * Used by <code>AtomicRewriter</code> to rewrite the AST to eliminate
   * <code>atomic</code> statements.
   */
  public Node rewriteAtomic(AtomicRewriter ar);
}
