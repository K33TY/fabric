package fabric.visit;

import java.util.*;

import jif.ast.*;
import jif.extension.JifBinaryDel;
import jif.types.*;
import jif.types.label.*;
import jif.types.principal.Principal;
import fabric.ast.FabricNodeFactory;
import fabric.types.FabricParsedClassType;
import fabric.types.FabricTypeSystem;
import polyglot.ast.*;
import polyglot.frontend.Job;
import polyglot.types.LocalInstance;
import polyglot.types.MethodInstance;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import polyglot.visit.NodeVisitor;

public class RemoteCallWrapperUpdater extends NodeVisitor {
  protected Job job;
  protected FabricTypeSystem ts;
  protected FabricNodeFactory nf;

  public RemoteCallWrapperUpdater(Job job, FabricTypeSystem ts, FabricNodeFactory nf) {
    this.job = job;
    this.ts = ts;
    this.nf = nf;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node leave(Node old, Node n, NodeVisitor v) {
    if (n instanceof ClassDecl) {
      ClassDecl cd = (ClassDecl)n;
      FabricParsedClassType pct = (FabricParsedClassType)cd.type();
      
      if (!ts.isFabricClass(pct)) {
        // Remove all the remote wrappers.
        List<ClassMember> members = new ArrayList<ClassMember>();
        for (ClassMember cm : (List<ClassMember>)cd.body().members()) {
          if (cm instanceof MethodDecl) {
            MethodDecl md = (MethodDecl)cm;
            if (md.name().endsWith("_remote")) {
              // Skip remote wrappers.
              MethodInstance mi = md.methodInstance();
              pct.removeMethod(mi);
              continue;
            }
          }
          members.add(cm);
        }
        
        return cd.body(cd.body().members(members));
      }
    }
    else if (n instanceof JifMethodDecl) {
      // Now add wrappers for remote call authentication check.
      JifMethodDecl md = (JifMethodDecl)n;

      if (md.name().endsWith("_remote")) {
        JifMethodInstance mi = (JifMethodInstance)md.methodInstance();

        FabricParsedClassType pct = (FabricParsedClassType)mi.container();
        
        Formal f = (Formal)md.formals().get(0);
//        Formal lbf = (Formal)md.formals().get(1);
        LocalInstance li = f.localInstance();

        Principal workerPrincipal;

        try {
          AccessPath ap = JifUtil.varInstanceToAccessPath(li, li.position());
          workerPrincipal = ts.dynamicPrincipal(ap.position(), ap);
        }
        catch (SemanticException e) {
          throw new InternalCompilerError(e);
        }

        Label startLabel = mi.pcBound();        
        Label returnLabel = ts.join(mi.returnValueLabel(), mi.returnLabel());

        if (ts.containsThisLabel(startLabel) || ts.containsThisLabel(returnLabel)) {
          // If the "this" label is used in the signature, then we does not allow remote calls.
          pct.removeMethod(mi);
          return null;
        }

        // Fold in all integrity policies of method parameters.
        IntegPolicy startIntegPolicy = ts.representableIntegProjection(startLabel);
        Iterator<Type> it = ((List<Type>)mi.formalTypes()).iterator();
        it.next();
        while (it.hasNext()) {
          Type ft = it.next();
          Label l = ts.labelOfType(ft);
          if (ts.containsThisLabel(l)) {
            pct.removeMethod(mi);
            return null;
          }
          IntegPolicy ip = ts.representableIntegProjection(l);
          startIntegPolicy = ts.meet(startIntegPolicy, ip);
        }
        
        // {C(rv), *<-worker$} <= {*->worker$, I(m)}
        // XXX Note, it is better NOT to blindly insert the projection translation, if not needed.
        Label left = ts.pairLabel(Position.compilerGenerated(md.position().toString()), 
                                  ts.representableConfProjection(returnLabel), 
                                  ts.writerPolicy(Position.compilerGenerated(), 
                                                  ts.topPrincipal(Position.compilerGenerated()), 
                                                  workerPrincipal));
        Label right = ts.pairLabel(Position.compilerGenerated(md.name()), 
                                   ts.readerPolicy(Position.compilerGenerated(), 
                                                   ts.topPrincipal(Position.compilerGenerated()), 
                                                   workerPrincipal), 
                                   startIntegPolicy);

        LabelExpr leftExpr = nf.LabelExpr(left.position(), left);
        LabelExpr rightExpr = nf.LabelExpr(right.position(), right);

        Expr labelComp = nf.Binary(Position.compilerGenerated(), leftExpr, Binary.LE, rightExpr);
        
        for (Assertion as : mi.constraints()) {
          if (ts.containsThisLabel(as)
              || ts.containsArgLabel(as)) {
            pct.removeMethod(mi);
            return null;
          }
          if (as instanceof CallerConstraint) {
            CallerConstraint cc = (CallerConstraint)as;
            for (Principal p : cc.principals()) {
              Expr check = 
                nf.Binary(as.position(), 
                          nf.CanonicalPrincipalNode(workerPrincipal.position(), workerPrincipal), 
                          JifBinaryDel.ACTSFOR, 
                          nf.CanonicalPrincipalNode(p.position(), p));
              labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
            }
          }
          else if (as instanceof ActsForConstraint) {
            ActsForConstraint<ActsForParam, ActsForParam> afc =
                (ActsForConstraint<ActsForParam, ActsForParam>) as;
            ActsForParam actor = afc.actor();
            ActsForParam granter = afc.granter();
            if (actor instanceof Principal && granter instanceof Principal) {
              Expr check = nf.Binary(afc.position(), 
                  nf.CanonicalPrincipalNode(actor.position(), (Principal) actor), 
                  afc.isEquiv() ? JifBinaryDel.EQUIV : JifBinaryDel.ACTSFOR, 
                  nf.CanonicalPrincipalNode(granter.position(), (Principal) granter));
              labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
            } else if (actor instanceof Label && granter instanceof Principal) {
              Expr check = nf.Binary(afc.position(), 
                  nf.LabelExpr(actor.position(), (Label) actor), 
                  afc.isEquiv() ? JifBinaryDel.EQUIV : JifBinaryDel.ACTSFOR, 
                  nf.CanonicalPrincipalNode(granter.position(), (Principal) granter));
              labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
            } else {
              throw new InternalCompilerError(afc.position(),
                  "Unexpected ActsForConstraint (" + actor.getClass()
                      + " actsfor " + granter.getClass() + ").");
            }
          }
          else if (as instanceof LabelLeAssertion) {
            LabelLeAssertion lla = (LabelLeAssertion)as;
            Expr check = nf.Binary(lla.position(), 
                                   nf.LabelExpr(lla.lhs().position(), lla.lhs()), 
                                   Binary.LE, 
                                   nf.LabelExpr(lla.rhs().position(), lla.rhs()));
            labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
          }
        }
        
        Eval eval = (Eval)md.body().statements().get(0);

        Stmt conseq;

        if (mi.returnType().isVoid()) {
          conseq = eval;
        }
        else {
          conseq = nf.Return(eval.position(), eval.expr());
        }
        
        Stmt alter = (Stmt)md.body().statements().get(1);

        Stmt s = nf.If(Position.compilerGenerated(), labelComp, conseq, alter);
        
        md = (JifMethodDecl)md.body(md.body().statements(Collections.singletonList(s)));
      }

      return md;
    }

    return n;
  }
}
