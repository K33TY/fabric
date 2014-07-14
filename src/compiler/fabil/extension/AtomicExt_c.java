package fabil.extension;

import java.util.ArrayList;
import java.util.List;

import polyglot.ast.Assign;
import polyglot.ast.Id;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.Stmt;
import polyglot.types.ArrayType;
import polyglot.types.Flags;
import polyglot.types.LocalInstance;
import polyglot.types.Type;
import polyglot.util.Position;
import fabil.ast.Atomic;
import fabil.types.FabILTypeSystem;
import fabil.visit.AtomicRewriter;

public class AtomicExt_c extends FabILExt_c {
  @Override
  public Node rewriteAtomic(AtomicRewriter ar) {
    Atomic atomic = (Atomic) node();
    NodeFactory nf = ar.nodeFactory();

    // Note: this needs to be built by the NF rather than the QQ because
    // otherwise it will be ambiguous and will fail further translation
    Position CG = Position.compilerGenerated();
    Stmt begin =
        nf.Eval(CG,
            nf.Call(CG, ar.transactionManager(), nf.Id(CG, "startTransaction")));
    Stmt commit =
        nf.Eval(CG, nf.Call(CG, ar.transactionManager(),
            nf.Id(CG, "commitTransaction")));
    Stmt abort =
        nf.Eval(CG,
            nf.Call(CG, ar.transactionManager(), nf.Id(CG, "abortTransaction")));

    FabILTypeSystem ts = ar.typeSystem();

    List<Stmt> lds = new ArrayList<Stmt>();
    List<Stmt> restores = new ArrayList<Stmt>();

    for (LocalInstance li : atomic.updatedLocals()) {
      Id lName = nf.Id(Position.compilerGenerated(), li.name());
      Id vName =
          nf.Id(Position.compilerGenerated(), li.name() + "$var" + (freshTid++));

      Type lt = li.type();
      if (lt.isArray()) {
        // Arrays have been translated in ProxyRewriter,
        // so we have to do it manually here.
        ArrayType at = lt.toArray();
        if (ts.isPureFabricType(at)) {
          lt = ts.toFabricRuntimeArray(at);
        }
      }

      lds.add(nf.LocalDecl(Position.compilerGenerated(), Flags.NONE,
          nf.CanonicalTypeNode(Position.compilerGenerated(), lt), vName,
          nf.Local(Position.compilerGenerated(), lName)));
      restores.add(nf.Eval(
          Position.compilerGenerated(),
          nf.LocalAssign(Position.compilerGenerated(),
              nf.Local(Position.compilerGenerated(), lName), Assign.ASSIGN,
              nf.Local(Position.compilerGenerated(), vName))));
    }

    String label = "$label" + (freshTid++);
    String flag = "$commit" + (freshTid++);
    String e = "$e" + (freshTid++);
    String currentTid = "$currentTid" + (freshTid++);
    String tm = "$tm" + (freshTid++);
    String backoff = "$backoff" + (freshTid++);

    String block =
        "{\n" + "  %LS\n" + "  fabric.worker.transaction.TransactionManager "
            + tm
            + " = fabric.worker.transaction.TransactionManager.getInstance();\n"
            + "  int "
            + backoff
            + " = 1;\n"
            + "  "
            + label
            + ": for (boolean "
            + flag
            + " = false; !"
            + flag
            + "; ) {\n"
            + "    if ("
            + backoff
            + " > 32) {\n"
            + "      while (true) {\n"
            + "        try {\n"
            + "          java.lang.Thread.sleep("
            + backoff
            + ");\n"
            + "          break;\n"
            + "        } catch (java.lang.InterruptedException "
            + e
            + ") {\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "    if ("
            + backoff
            + " < 5000) "
            + backoff
            + " *= 2;\n"
            + "    "
            + flag
            + " = true;\n"
            + "    %S\n"
            + "    try {\n"
            + "      %LS\n"
            + "    }\n"
            + "    catch (final fabric.worker.RetryException "
            + e
            + ") {\n"
            + "      "
            + flag
            + " = false;"
            + "      continue "
            + label
            + ";\n"
            + "    }\n"
            + (atomic.mayAbort() ? "    catch (final fabric.worker.UserAbortException "
                + e
                + ") {\n"
                + "      "
                + flag
                + " = false;"
                + "      break "
                + label + ";\n" + "    }\n"
                : "")
                + "    catch (final fabric.worker.TransactionRestartingException "
                + e
                + ") {\n"
                + "      "
                + flag
                + " = false;"
                + "      fabric.common.TransactionID "
                + currentTid
                + " = \n"
                + "        "
                + tm
                + ".getCurrentTid();\n"
                + "      if ("
                + e
                + ".tid.isDescendantOf("
                + currentTid
                + "))\n"
                + "        continue "
                + label
                + ";\n"
                + "      if ("
                + currentTid
                + ".parent != null) throw "
                + e
                + ";\n"
                + "      throw new InternalError(\"Something is broken with transaction management. Got a signal to restart a different transaction than the one being managed.\");\n"
                + "    }\n"
                + "    catch (final Throwable "
                + e
                + ") {\n"
                + "      "
                + flag
                + " = false;\n"
                + "      if ("
                + tm
                + ".checkForStaleObjects()) continue "
                + label
                + ";\n"
                + "      throw new fabric.worker.AbortException("
                + e
                + ");\n"
                + "    }\n"
                + "    finally {\n"
                + "      if ("
                + flag
                + ") {\n"
                + "        try {\n"
                + "          %S\n"
                + "        }\n"
                + "        catch (final fabric.worker.AbortException "
                + e
                + ") {\n"
                + "          "
                + flag
                + " = false;\n"
                + "        }\n"
                + "        catch (final fabric.worker.TransactionRestartingException "
                + e + ") {\n" + "          " + flag + " = false;\n"
                + "          fabric.common.TransactionID " + currentTid + " = \n"
                + "            " + tm + ".getCurrentTid();\n" + "          if ("
                + currentTid + " == null || " + e + ".tid.isDescendantOf("
                + currentTid + ")\n" + "              && !" + currentTid
                + ".equals(" + e + ".tid))\n" + "            continue " + label
                + ";\n" + "          throw " + e + ";\n" + "        }\n"
                + "      }\n" + "      else {\n" + "        %S\n" + "      }"
                + "      if (!" + flag + ") {\n" + "        %LS\n" + "      }\n"
                + "    }\n" + "  }\n" + "}\n";

    return ar.qq().parseStmt(block, lds, begin, atomic.statements(), commit,
        abort, restores);
  }

  private static int freshTid = 0;
}
