package fabric.visit;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jif.ast.JifMethodDecl;
import jif.ast.JifUtil;
import jif.ast.LabelComponentNode;
import jif.ast.PolicyNode;
import jif.ast.PrincipalNode;
import polyglot.ast.ClassDecl;
import polyglot.ast.ClassMember;
import polyglot.ast.Expr;
import polyglot.ast.Formal;
import polyglot.ast.Local;
import polyglot.ast.Node;
import polyglot.ast.Stmt;
import polyglot.ast.TypeNode;
import polyglot.frontend.Job;
import polyglot.qq.QQ;
import polyglot.types.Flags;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import polyglot.visit.NodeVisitor;
import fabric.ast.FabricNodeFactory;
import fabric.extension.ClassBodyJifExt_c;
import fabric.extension.MethodDeclJifExt;
import fabric.types.FabricTypeSystem;

public class RemoteCallWrapperAdder extends NodeVisitor {
  protected Job job;
  protected FabricTypeSystem ts;
  protected FabricNodeFactory nf;
  protected QQ qq;
  public static String REMOTE_WRAPPER_SUFFIX = "_remote";
  
  public RemoteCallWrapperAdder(Job job, FabricTypeSystem ts, FabricNodeFactory nf) {
    this.job = job;
    this.ts = ts;
    this.nf = nf;
    this.qq = new QQ(job.extensionInfo());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node leave(Node old, Node n, NodeVisitor v) {

    if (n instanceof ClassDecl) {
      // Now add wrappers for remote call authentication check.
      ClassDecl cd = (ClassDecl)n;
      
      // No need to add wrappers for interfaces 
      if (cd.flags().isInterface()) return cd;
      
      ClassBodyJifExt_c ext = (ClassBodyJifExt_c) JifUtil.jifExt(cd.body());
      List<ClassMember> remote_wrappers = ext.remoteWrappers();

      for (ClassMember cm : (List<ClassMember>)cd.body().members()) {
        if (cm instanceof JifMethodDecl) {
          JifMethodDecl md = (JifMethodDecl)cm;
          if (!md.flags().isPublic() || md.flags().isStatic()) continue;
          // Also skip abstract method.
          if (md.body() == null) continue;
          
          String cm_fmt = "%T " + md.name() + REMOTE_WRAPPER_SUFFIX + "(%LF) { %LS }";
          
          List<Formal> formals = new ArrayList<Formal>(md.formals().size() + 1);
          formals.add(createWorkerFormal());
          formals.addAll(md.formals());

          String call_fmt = "%s(%LE);";
          List<Expr> args = new ArrayList<Expr>(md.formals().size());
          for (Formal formal : (List<Formal>)md.formals()) {
            Local l = nf.Local(Position.compilerGenerated(), formal.id());
            args.add(l);
          }
          Stmt call = qq.parseStmt(call_fmt, md.name(), args);
          Stmt thrw = qq.parseStmt("throw new NullPointerException();");
          List<Stmt> stmts = new LinkedList<Stmt>();
          stmts.add(call);
          stmts.add(thrw);
          
          List<TypeNode> throwTypes = new ArrayList<TypeNode>(md.throwTypes().size() + 1);
          throwTypes.addAll(md.throwTypes());

          ClassMember newCM = qq.parseMember(cm_fmt, md.returnType(), formals, stmts);
          if(newCM instanceof JifMethodDecl) {
            JifMethodDecl rmd = (JifMethodDecl) newCM;
            rmd = (JifMethodDecl) rmd.flags(md.flags());
            rmd = (JifMethodDecl) rmd.throwTypes(md.throwTypes());
            rmd = rmd.startLabel(md.startLabel());
            rmd = rmd.returnLabel(md.returnLabel());
            rmd = rmd.constraints(md.constraints());
            
            MethodDeclJifExt rmd_ext = (MethodDeclJifExt) JifUtil.jifExt(rmd);
            rmd_ext.setRemote();
            remote_wrappers.add(rmd);
          }
          else throw new InternalCompilerError("Error creating remote wrapper." + newCM.getClass());
        }
      }
    }

    return n;
  }
  
  protected Formal createWorkerFormal() {
    List<LabelComponentNode> components = new ArrayList<LabelComponentNode>(2);
    List<PrincipalNode> writers = new ArrayList<PrincipalNode>();

    writers.add(nf.CanonicalPrincipalNode(Position.compilerGenerated(), 
                ts.topPrincipal(Position.compilerGenerated())));
    PolicyNode writer = 
      nf.WriterPolicyNode(Position.compilerGenerated(),
                          nf.CanonicalPrincipalNode(Position.compilerGenerated(), 
                                                    ts.topPrincipal(Position.compilerGenerated())),
                          writers);
    components.add(writer);

    TypeNode formalType = 
      nf.LabeledTypeNode(Position.compilerGenerated(), 
                         nf.CanonicalTypeNode(Position.compilerGenerated(), ts.Principal()),
                         nf.JoinLabelNode(Position.compilerGenerated(), components));
    return nf.Formal(Position.compilerGenerated(), 
                         Flags.FINAL,
                         formalType, 
                         nf.Id(Position.compilerGenerated(), "worker$principal"));
  }
}
