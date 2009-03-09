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
//  protected QQ qq;

  public RemoteCallWrapperUpdater(Job job, FabricTypeSystem ts, FabricNodeFactory nf) {
    this.job = job;
    this.ts = ts;
    this.nf = nf;
//    this.qq = new QQ(job.extensionInfo());
  }

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

        Formal f = (Formal)md.formals().get(0);
//        Formal lbf = (Formal)md.formals().get(1);
        LocalInstance li = f.localInstance();

        Principal clientPrincipal;

        try {
          AccessPath ap = JifUtil.varInstanceToAccessPath(li, li.position());
          clientPrincipal = ts.dynamicPrincipal(ap.position(), ap);
        }
        catch (SemanticException e) {
          throw new InternalCompilerError(e);
        }

        //        Principal clientPrincipal = ts.clientPrincipal(Position.compilerGenerated());

        //          Label defaultBound = ts.defaultSignature().defaultArgBound(f);
        //          JifLocalInstance li = (JifLocalInstance)ts.localInstance(f.position(), Flags.FINAL, ts.Principal(), "client$principal");
        //          ArgLabel al = ts.argLabel(f.position(), li, null);
        //          al.setUpperBound(defaultBound);
        //          li.setLabel(al);
        //          f = f.localInstance(li);
        //          Type labeled = ts.labeledType(Position.compilerGenerated(), ts.Principal(), al);
        //          f = f.type(f.type().type(labeled));

        Label startLabel = mi.pcBound();        
        Label returnLabel = mi.returnLabel();

//        // (rv meet {conf.top;integ.bot}) join ({client$<-}) <= ({client$->}) join (m meet {conf.bot;integ.top})
//        Label left = ts.join(
//            ts.meet(
//                returnLabel,
//                ts.pairLabel(Position.compilerGenerated(),
//                    ts.topConfPolicy(Position.compilerGenerated()), 
//                    ts.bottomIntegPolicy(Position.compilerGenerated()))),
//                    ts.pairLabel(Position.compilerGenerated(),
//                        ts.bottomConfPolicy(Position.compilerGenerated()),
//                        ts.writerPolicy(Position.compilerGenerated(), clientPrincipal, clientPrincipal)));
//
//        Label right = ts.join(
//            ts.meet(
//                startLabel,
//                ts.pairLabel(Position.compilerGenerated(),
//                    ts.bottomConfPolicy(Position.compilerGenerated()), 
//                    ts.topIntegPolicy(Position.compilerGenerated()))),
//                    ts.pairLabel(Position.compilerGenerated(),
//                        ts.readerPolicy(Position.compilerGenerated(), clientPrincipal, clientPrincipal),
//                        ts.bottomIntegPolicy(Position.compilerGenerated())));

        // Fold in all integrity policies of method parameters.
        IntegPolicy startIntegPolicy = ts.representableIntegProjection(startLabel);
        Iterator<Type> it = ((List<Type>)mi.formalTypes()).iterator();
        it.next();
        while (it.hasNext()) {
          Type ft = it.next();
          Label l = ts.labelOfType(ft);
          IntegPolicy ip = ts.representableIntegProjection(l);
          startIntegPolicy = ts.meet(startIntegPolicy, ip);
        }
        
        // {C(rv), *<-client$} <= {*->client$, I(m)}
        // XXX Note, it is better NOT to blindly insert the projection translation, if not needed.
        Label left = ts.pairLabel(Position.compilerGenerated(md.name()), 
                                  ts.representableConfProjection(returnLabel), 
                                  ts.writerPolicy(Position.compilerGenerated(), 
                                                  ts.topPrincipal(Position.compilerGenerated()), 
                                                  clientPrincipal));
        Label right = ts.pairLabel(Position.compilerGenerated(md.name()), 
                                   ts.readerPolicy(Position.compilerGenerated(), 
                                                   ts.topPrincipal(Position.compilerGenerated()), 
                                                   clientPrincipal), 
                                   startIntegPolicy);

        LabelExpr leftExpr = nf.LabelExpr(left.position(), left);
        LabelExpr rightExpr = nf.LabelExpr(right.position(), right);

        Expr labelComp = nf.Binary(Position.compilerGenerated(), leftExpr, Binary.LE, rightExpr);

        //          List<Expr> args = new ArrayList<Expr>(md.formals().size());
        //          for (Formal formal : (List<Formal>)md.formals()) {
        //            Local l = nf.Local(Position.compilerGenerated(), formal.id());
        //            l = l.localInstance(formal.localInstance());
        //            args.add(l);
        //          }
        
        for (Assertion as : (List<Assertion>)mi.constraints()) {
          if (as instanceof CallerConstraint) {
            CallerConstraint cc = (CallerConstraint)as;
            for (Principal p : (List<Principal>)cc.principals()) {
              Expr check = 
                nf.Binary(as.position(), 
                          nf.CanonicalPrincipalNode(clientPrincipal.position(), clientPrincipal), 
                          JifBinaryDel.ACTSFOR, 
                          nf.CanonicalPrincipalNode(p.position(), p));
              labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
            }
          }
          else if (as instanceof ActsForConstraint) {
            ActsForConstraint afc = (ActsForConstraint)as;
            Expr check = nf.Binary(afc.position(), 
                                   nf.CanonicalPrincipalNode(afc.actor().position(), afc.actor()), 
                                   afc.isEquiv() ? JifBinaryDel.EQUIV : JifBinaryDel.ACTSFOR, 
                                   nf.CanonicalPrincipalNode(afc.granter().position(), afc.granter()));
            labelComp = nf.Binary(Position.compilerGenerated(), labelComp, Binary.COND_AND, check);
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
        
//        Stmt t = qq.parseStmt("try { _npe(lbl); %S; } catch (NullPointerException e) {}", s);

        Stmt s = nf.If(Position.compilerGenerated(), labelComp, conseq, alter);
        
        md = (JifMethodDecl)md.body(md.body().statements(Collections.singletonList(s)));
      }

      return md;
    }

    return n;
  }
}
