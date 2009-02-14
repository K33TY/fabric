package fabil.extension;

import java.util.ArrayList;
import java.util.List;

import polyglot.ast.Assign;
import polyglot.ast.Id;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.Stmt;
import polyglot.types.Flags;
import polyglot.types.LocalInstance;
import polyglot.util.Position;
import fabil.ast.Atomic;
import fabil.visit.AtomicRewriter;

public class AtomicExt_c extends FabILExt_c {
  @Override
  public Node rewriteAtomic(AtomicRewriter ar) {
    Atomic atomic  = (Atomic) node();
    NodeFactory nf = ar.nodeFactory();
    
    // Note: this needs to be built by the NF rather than the QQ because
    // otherwise it will be ambiguous and will fail further translation
    Position CG = Position.compilerGenerated();
    Stmt begin  = nf.Eval(CG, nf.Call(CG, ar.transactionManager(),
                                          nf.Id(CG, "startTransaction")));
    Stmt commit = nf.Eval(CG, nf.Call(CG, ar.transactionManager(),
                                          nf.Id(CG, "commitTransaction")));
    Stmt abort  = nf.Eval(CG, nf.Call(CG, ar.transactionManager(),
                                          nf.Id(CG, "abortTransaction")));
   
//    Id flag = nf.Id(CG, "$commit" + (freshTid++));
    
    List<Stmt> lds = new ArrayList<Stmt>();
    List<Stmt> restores = new ArrayList<Stmt>();
    
    for (LocalInstance li : atomic.updatedLocals()) {
      Id lName = nf.Id(Position.compilerGenerated(), 
                       li.name());
      Id vName = nf.Id(Position.compilerGenerated(), 
                       li.name() + "$var" + (freshTid++));
      lds.add(nf.LocalDecl(Position.compilerGenerated(), 
                           Flags.NONE, 
                           nf.CanonicalTypeNode(Position.compilerGenerated(), 
                                                li.type()), 
                           vName,
                           nf.Local(Position.compilerGenerated(), 
                                    lName)));
      restores.add(nf.Eval(Position.compilerGenerated(), 
                           nf.LocalAssign(Position.compilerGenerated(), 
                                          nf.Local(Position.compilerGenerated(), 
                                                   lName), 
                                          Assign.ASSIGN, 
                                          nf.Local(Position.compilerGenerated(), 
                                                   vName))));
    }
    
    String label = "$label" + (freshTid++);
    String flag = "$commit" + (freshTid++);
    String e = "$e" + (freshTid++);
    
    String block = "{\n" +
    		   "  %LS\n" +
    		   "  " + label + ": for (; ; ) {\n" +
    		   "    boolean " + flag + " = true;\n" +
    		   "    %S\n" +
    		   "    try {\n" +
    		   "      %LS\n" +
    		   "    }\n" +
    		   "    catch (final fabric.client.RetryException " + e + ") {\n" +
                   "      " + flag + " = false;" +
    		   "      continue " + label + ";\n" +
    		   "    }\n" +
                   "    catch (final fabric.client.UserAbortException " + e + ") {\n" +
                   "      " + flag + " = false;" +
                   "      break " + label + ";\n" +
                   "    }\n" +
    		   "    catch (final Throwable " + e + ") {\n" +
    		   "      " + flag + " = false;\n" +
    		   "      throw new fabric.client.AbortException(" + e + ");\n" +
    		   "    }\n" +
    		   "    finally {\n" +
    		   "      if (" + flag + ") {\n" +
    		   "        try {\n" +
    		   "          %S\n" +
    		   "          break " + label + ";\n" +
    		   "        }\n" +
    		   "        catch (final fabric.client.AbortException " + e + ") { }\n" +
    		   "      }\n" +
   		   "      { %LS }\n" +
    		   "      %S\n" +
    		   "    }\n" +
    		   "  }\n" +
    		   "}\n";
    
//    String block = "{ boolean "+flag+" = true;\n" +
//                   "  %S\n" +
//                   "  try { %LS }\n" +
//                   "  catch (final Throwable $_) { "+flag+" = false; throw new fabric.client.AbortException($_); }\n" +
//                   "  finally { if ("+flag+") {%S} else {%S} } }\n";
    return ar.qq().parseStmt(block, lds, begin, atomic.statements(), commit, restores, abort);
  }

  private static int freshTid = 0;
}
