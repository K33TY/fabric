package fabric.types;

import java.net.URI;

import jif.translate.LabelToJavaExpr;
import jif.types.JifClassType;
import jif.types.label.ProviderLabel_c;
import polyglot.types.ParsedClassType;
import codebases.frontend.ExtensionInfo;
import codebases.types.CodebaseClassType;

public class FabricProviderLabel_c extends ProviderLabel_c {
  boolean namespaceChecked = false;

  public FabricProviderLabel_c(JifClassType classType, LabelToJavaExpr toJava) {
    super(classType, toJava);
  }

  @Override
  public boolean isTrusted() {
    if (!namespaceChecked) {
      // Classes published to Fabric should never have trusted providers.
      if (classType instanceof ParsedClassType) {
        ExtensionInfo extInfo =
            (ExtensionInfo) classType.typeSystem().extensionInfo();
        CodebaseClassType cbct = (CodebaseClassType) classType;
        URI ns = cbct.canonicalNamespace();
        // If this class isn't from the local or platform namespace,
        // then we should never trust it.
        if (!ns.equals(extInfo.localNamespace())
            && !ns.equals(extInfo.platformNamespace())) {
          this.isTrusted = false;
        }
      }
      namespaceChecked = true;
      return isTrusted;
    } else return isTrusted;
  }
}
