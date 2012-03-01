package fabil.visit;

import java.util.*;

import fabil.ast.Atomic;

import polyglot.ast.*;
import polyglot.types.LocalInstance;
import polyglot.visit.NodeVisitor;

public class UpdatedVariableFinder extends NodeVisitor {
  protected Set<LocalInstance> declared = new HashSet<LocalInstance>();
  protected Set<LocalInstance> updated = new HashSet<LocalInstance>();
  
  @SuppressWarnings("unchecked")
  @Override
  public NodeVisitor enter(Node n) {
    if (n instanceof ProcedureDecl) {
      UpdatedVariableFinder v = (UpdatedVariableFinder)this.copy();
      v.declared = new HashSet<LocalInstance>();
      v.updated = new HashSet<LocalInstance>();
      
      ProcedureDecl pd = (ProcedureDecl)n;
      for (Formal f : (List<Formal>)pd.formals()) {
        v.declared.add(f.localInstance());
      }
      
      return v;
    }
    else if (n instanceof Block) {
      UpdatedVariableFinder v = (UpdatedVariableFinder)this.copy();
      v.declared = new HashSet<LocalInstance>();
      v.declared.addAll(declared);
      
      Block b = (Block)n;
      for (Stmt s : (List<Stmt>)b.statements()) {
        if (s instanceof LocalDecl) {
          LocalDecl ld = (LocalDecl)s;
          v.declared.add(ld.localInstance());
        }
      }
      
      return v;
    }
    else if (n instanceof For) {
      UpdatedVariableFinder v = (UpdatedVariableFinder)this.copy();
      v.declared = new HashSet<LocalInstance>();
      v.declared.addAll(declared);

      For f = (For)n;
      for (Stmt s : (List<Stmt>)f.inits()) {
        if (s instanceof LocalDecl) {
          LocalDecl ld = (LocalDecl)s;
          v.declared.add(ld.localInstance());
        }
      }
      
      return v;
    }
    
    return this;
  }
  
  @Override
  public Node leave(Node old, Node n, NodeVisitor v) {
    if (n instanceof LocalAssign) {
      Local l = (Local)((LocalAssign)n).left();
      updated.add(l.localInstance());
    }
    else if (n instanceof Atomic) {
      Atomic a = (Atomic)n;
      List<LocalInstance> updatedLocals = new ArrayList<LocalInstance>();
      for (LocalInstance li : updated) {
        if (declared.contains(li)) {
          updatedLocals.add(li);
        }
      }
      return a.updatedLocals(updatedLocals);
    }
    
    return n;
  }
}
