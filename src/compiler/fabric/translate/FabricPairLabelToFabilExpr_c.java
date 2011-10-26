package fabric.translate;

import java.util.Iterator;
import java.util.LinkedList;

import jif.translate.JifToJavaRewriter;
import jif.translate.PairLabelToJavaExpr_c;
import jif.types.label.ConfPolicy;
import jif.types.label.ConfProjectionPolicy_c;
import jif.types.label.IntegPolicy;
import jif.types.label.IntegProjectionPolicy_c;
import jif.types.label.JoinPolicy_c;
import jif.types.label.Label;
import jif.types.label.MeetPolicy_c;
import jif.types.label.PairLabel;
import jif.types.label.Policy;
import jif.types.label.ReaderPolicy;
import jif.types.label.WriterPolicy;
import polyglot.ast.Expr;
import polyglot.types.SemanticException;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import fabric.types.FabricTypeSystem;
import fabric.visit.FabricToFabilRewriter;

public class FabricPairLabelToFabilExpr_c extends PairLabelToJavaExpr_c {
  @Override
  public Expr toJava(Label label, JifToJavaRewriter rw) throws SemanticException {
    PairLabel pl = (PairLabel)label;
    FabricToFabilRewriter ffrw = (FabricToFabilRewriter) rw;

    if (pl.confPolicy().isBottomConfidentiality() && pl.integPolicy().isTopIntegrity()) {
        return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".noComponents()");
    }
    
    Expr cexp = policyToJava(pl.confPolicy(), rw);
    Expr iexp = policyToJava(pl.integPolicy(), rw);

    Expr store = ffrw.currentLocation(); 
      if (containsProjection(pl.confPolicy()) || containsProjection(pl.integPolicy())) {
        if (!containsProjection(pl.confPolicy())) {
          cexp = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".liftToLabel(%E, %E)", store, cexp);
        }
        if (!containsProjection(pl.integPolicy())) {
          iexp = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".liftToLabel(%E, %E)", store, iexp);
        }
        return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".join(%E, %E, %E)", store, cexp, iexp);
      }
        
      return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".toLabel(%E, %E, %E)",store, cexp, iexp); 
  }
  
  protected boolean containsProjection(Policy p) {
    if (p instanceof ConfProjectionPolicy_c) return true;
    else if (p instanceof IntegProjectionPolicy_c) return true;
    else if (p instanceof JoinPolicy_c) {
      @SuppressWarnings("unchecked")
      JoinPolicy_c<Policy> jp = (JoinPolicy_c<Policy>)p;
      for (Policy tp : jp.joinComponents()) {
        if (containsProjection(tp)) return true;
      }
    }
    else if (p instanceof MeetPolicy_c) {
      @SuppressWarnings("unchecked")
      MeetPolicy_c<Policy> mp = (MeetPolicy_c<Policy>)p;
      for (Policy tp : mp.meetComponents()) {
        if (containsProjection(tp)) return true;
      }
    }
    return false;
  }
  
  @Override
  public Expr policyToJava(Policy p, JifToJavaRewriter rw) throws SemanticException {
    FabricTypeSystem ts = (FabricTypeSystem)rw.jif_ts();
    FabricToFabilRewriter ffrw = (FabricToFabilRewriter) rw;
    Expr store = ffrw.currentLocation();
    
    if (p instanceof IntegProjectionPolicy_c) {
      IntegProjectionPolicy_c ipp = (IntegProjectionPolicy_c)p;
      Label l = 
        ts.meet(ipp.label(), 
                ts.pairLabel(Position.compilerGenerated(), 
                             ts.bottomConfPolicy(Position.compilerGenerated()), 
                             ts.topIntegPolicy(Position.compilerGenerated())));
      return l.toJava(rw);
    }
    else if (p instanceof ConfProjectionPolicy_c) {
      ConfProjectionPolicy_c cpp = (ConfProjectionPolicy_c)p;
      Label l = 
        ts.meet(cpp.label(), 
                ts.pairLabel(Position.compilerGenerated(), 
                             ts.topConfPolicy(Position.compilerGenerated()), 
                             ts.bottomIntegPolicy(Position.compilerGenerated())));
      return l.toJava(rw);
    }
    else if (p instanceof JoinPolicy_c) {
      if (containsProjection(p)) {
        @SuppressWarnings("unchecked")
        JoinPolicy_c<Policy> jp = (JoinPolicy_c<Policy>)p;
        Expr result = null;
        for (Policy tp : jp.joinComponents()) {
          Expr ep = policyToJava(tp, rw);
          if (!containsProjection(tp)) {
              ep = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".toLabel(%E, %E)", store, ep);
          }
          if (result == null) {
            result = ep;
          }
          else {
              result = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".join(%E, %E, %E)", store, result, ep);
          }
        }
        return result;
      }
    }
    else if (p instanceof MeetPolicy_c) {
      if (containsProjection(p)) {
        @SuppressWarnings("unchecked")
        MeetPolicy_c<Policy> mp = (MeetPolicy_c<Policy>)p;
        Expr result = null;
        for (Policy tp : mp.meetComponents()) {
          Expr ep = policyToJava(tp, rw);
          if (!containsProjection(tp)) {
              ep = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".toLabel(%E, %E)", store, ep);
          }
          if (result == null) {
            result = ep;
          }
          else {
              result = rw.qq().parseExpr(rw.runtimeLabelUtil() + ".meet(%E, %E, %E)", store, result, ep);
          }
        }
        return result;
      }      
    }
    
    //mostly copied from superclass, but added store arguments
    if (p instanceof ConfPolicy && ((ConfPolicy) p).isBottomConfidentiality()) {
      return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".bottomConf()");
    }
    if (p instanceof IntegPolicy && ((IntegPolicy) p).isTopIntegrity()) {
      return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".topInteg()");
    }
    
    if (p instanceof WriterPolicy) {
      WriterPolicy policy = (WriterPolicy) p;
      Expr owner = rw.principalToJava(policy.owner());
      Expr writer = rw.principalToJava(policy.writer());
      return rw.qq().parseExpr(rw.runtimeLabelUtil() + ".writerPolicy(%E, %E, %E)",
          store, owner, writer);
    }

    if (p instanceof ReaderPolicy) {
      ReaderPolicy policy = (ReaderPolicy) p;
      Expr owner = rw.principalToJava(policy.owner());
      Expr reader = rw.principalToJava(policy.reader());
      return (Expr) rw.qq().parseExpr(
          rw.runtimeLabelUtil() + ".readerPolicy(%E, %E, %E)", store, owner, reader)
          .position(
              Position.compilerGenerated(p.toString() + ":"
                  + p.position().toString()));
    }

    if (p instanceof JoinPolicy_c) {
      @SuppressWarnings("unchecked")
      JoinPolicy_c<Policy> jp = (JoinPolicy_c<Policy>) p;
      LinkedList<Policy> l = new LinkedList<Policy>(jp.joinComponents());
      Iterator<Policy> iter = l.iterator();
      Policy head = iter.next();
      Expr e = policyToJava(head, rw);
      while (iter.hasNext()) {
        head = iter.next();
        Expr f = policyToJava(head, rw);
        e = rw.qq().parseExpr("%E.join(%E, %E)", e, store, f);
      }
      return e;
    }

    if (p instanceof MeetPolicy_c) {
      @SuppressWarnings("unchecked")
      MeetPolicy_c<Policy> mp = (MeetPolicy_c<Policy>) p;
      LinkedList<Policy> l = new LinkedList<Policy>(mp.meetComponents());
      Iterator<Policy> iter = l.iterator();
      Policy head = iter.next();
      Expr e = policyToJava(head, rw);
      while (iter.hasNext()) {
        head = iter.next();
        Expr f = policyToJava(head, rw);
        e = rw.qq().parseExpr("%E.meet(%E, %E)", e, store, f);
      }
      return e;
    }

    throw new InternalCompilerError("Cannot translate policy " + p);
  }
}
