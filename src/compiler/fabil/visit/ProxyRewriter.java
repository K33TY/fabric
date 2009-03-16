package fabil.visit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import polyglot.ast.Block;
import polyglot.ast.CodeDecl;
import polyglot.ast.ConstructorCall;
import polyglot.ast.Local;
import polyglot.ast.Node;
import polyglot.ast.Receiver;
import polyglot.ast.Stmt;
import polyglot.qq.QQ;
import polyglot.types.ArrayType;
import polyglot.types.ClassType;
import polyglot.types.Flags;
import polyglot.types.LocalInstance;
import polyglot.types.Type;
import polyglot.visit.NodeVisitor;
import fabil.ExtensionInfo;
import fabil.ast.FabILNodeFactory;
import fabil.extension.FabILExt;
import fabil.types.FabILTypeSystem;
import fabil.visit.ReadWriteChecker.State;

/**
 * Rewrites FabIL classes into classes that implement
 * <code>fabric.lang.Object</code>.
 */
public class ProxyRewriter extends NodeVisitor {
  
  protected QQ qq;
  protected FabILNodeFactory nf;
  protected FabILTypeSystem ts;
  
  private final Stack<Map<LocalInstance, String>> shadowStack = 
    new Stack<Map<LocalInstance, String>>();
  private boolean inConstructorCall;

  public ProxyRewriter(ExtensionInfo extInfo) {
    this.qq = new QQ(extInfo);
    this.nf = extInfo.nodeFactory();
    this.ts = extInfo.typeSystem();
  }

  @Override
  public NodeVisitor enter(Node parent, Node n) {
    if (n instanceof CodeDecl) {
      shadowStack.push(new HashMap<LocalInstance, String>());
    }
    
    if (n instanceof ConstructorCall) {
      inConstructorCall = true;
    }
    
    return super.enter(parent, n);
  }

  /*
   * (non-Javadoc)
   * 
   * @see polyglot.visit.NodeVisitor#override(polyglot.ast.Node)
   */
  @Override
  public Node override(Node n) {
    return ext(n).rewriteProxiesOverride(this);
  }

  /*
   * (non-Javadoc)
   * 
   * @see polyglot.visit.NodeVisitor#leave(polyglot.ast.Node, polyglot.ast.Node,
   *      polyglot.visit.NodeVisitor)
   */
  @Override
  public Node leave(Node old, Node n, NodeVisitor v) {
    n = ext(n).rewriteProxies(this);
    
    if (n instanceof CodeDecl) {
      n = produceShadowDecls((CodeDecl) n);
      shadowStack.pop();
    }
    
    if (n instanceof ConstructorCall) {
      inConstructorCall = false;
    }
    
    return n;
  }

  private FabILExt ext(Node n) {
    return (FabILExt) n.ext();
  }

  public QQ qq() {
    return qq;
  }

  public FabILNodeFactory nodeFactory() {
    return nf;
  }

  public FabILTypeSystem typeSystem() {
    return ts;
  }
  
  public static Flags toPublic(Flags flags) {
    return flags.clearPrivate().clearProtected().Public();
  }
  
  public static Flags toPrivate(Flags flags) {
    return flags.clearPublic().clearProtected().Private();
  }
  
  public static Flags toFinal(Flags flags) {
    return flags.clearAbstract().clearInterface().Final();
  }
  
  public static Flags toInterface(Flags flags) {
    return flags.clearAbstract().clearFinal().clearPrivate().Interface();
  }
  
  /** Returns a shadow name for local variable l. */
  public String getShadow(LocalInstance l) {
    if (l == null) {
      throw new NullPointerException();
    }
    
    Map<LocalInstance, String> shadows = shadowStack.peek();
    String s = shadows.get(l);
    
    if (s == null) {
      s = l.name() + "$impl$" + shadows.size();
      shadows.put(l, s);
    }
    
    return s;
  }
  
  public Receiver replaceTarget(Receiver target, State accessState) {
    if (inConstructorCall) {
      return target;
    }
    
    String t = toImplType(target.type());
    Receiver shadow = target;
    
    if (target instanceof Local) {
      Local l = (Local) target;
      shadow = qq.parseExpr(getShadow(l.localInstance()));
    }
    
    if (!accessState.resident()) {
      target = qq.parseExpr("(%E = (" + t + ") %E.fetch())", 
          shadow, target);
    } else {
      target = shadow;
    }
    
    return target;
  }
  
  @SuppressWarnings("unchecked")
  private CodeDecl produceShadowDecls(CodeDecl n) {
    Map<LocalInstance, String> shadows = shadowStack.peek();
    
    if (shadows == null || shadows.size() == 0) {
      return n;
    }
    
    Block b = n.body();
    List<Stmt> l = new ArrayList<Stmt>(b.statements());
    
    int i = 0;
    for (; i < l.size(); i++) {
      if (!(l.get(i) instanceof ConstructorCall)) {
        break;
      }
    }
    
    l.addAll(i, shadowDecls());
    b = b.statements(l);
    n = (CodeDecl) n.body(b);
    return n;
  }

  private List<Stmt> shadowDecls() {
    Map<LocalInstance, String> shadows = shadowStack.peek();
    List<Stmt> l = new ArrayList<Stmt>(shadows.size());
    
    for (Map.Entry<LocalInstance, String> e : shadows.entrySet()) {
      LocalInstance li = e.getKey();
      String name = e.getValue();
      String t = toImplType(li.type());
      
      l.add(qq.parseStmt(t + " " + name + ";"));
    }
    
    return l;
  }
  
  private String toImplType(Type t) {
    if (t instanceof ArrayType) {
      ArrayType a = (ArrayType) t;
      return ts.fArrayImplOf(a.base()).fullName();
    } else {
      ClassType c = (ClassType) t;
      
      if (c.flags().isInterface() || 
          ts.WrappedJavaInlineable().isImplicitCastValid(c)) {
        return c.fullName();
      } else {
        return c.fullName() + "._Impl";
      }
    }
  }
  
}
