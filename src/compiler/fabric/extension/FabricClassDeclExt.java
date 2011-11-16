package fabric.extension;

import jif.ast.JifClassDecl;
import jif.extension.JifClassDeclExt;
import jif.translate.ToJavaExt;
import jif.types.ConstraintMessage;
import jif.types.JifClassType;
import jif.types.JifContext;
import jif.types.JifParsedPolyType;
import jif.types.JifTypeSystem;
import jif.types.LabelConstraint;
import jif.types.NamedLabel;
import jif.types.PrincipalConstraint;
import jif.types.label.ConfPolicy;
import jif.types.label.Label;
import jif.types.label.ProviderLabel;
import jif.types.principal.Principal;
import jif.visit.LabelChecker;
import polyglot.ast.ClassBody;
import polyglot.ast.Node;
import polyglot.types.SemanticException;
import polyglot.util.Position;
import fabric.types.FabricParsedClassType;
import fabric.types.FabricTypeSystem;

public class FabricClassDeclExt extends JifClassDeclExt {

  public FabricClassDeclExt(ToJavaExt toJava) {
    super(toJava);
  }

  @Override
  public Node labelCheck(LabelChecker lc) throws SemanticException {
    
    JifClassDecl n = (JifClassDecl) node();
    FabricParsedClassType pct = (FabricParsedClassType) n.type();

    JifTypeSystem jts = lc.typeSystem();
    JifContext A = lc.jifContext();
    A = (JifContext)A.pushClass(n.type(), n.type());
    A = n.addParamsToContext(A);
    A = n.addAuthorityToContext(A);

    A.setCurrentCodePCBound(jts.notTaken());

    final JifParsedPolyType ct = (JifParsedPolyType) n.type();

    // let the label checker know that we are about to enter a class body
    lc.enteringClassDecl(ct);
    
    // construct a principal that represents the authority of ct
    final Principal authPrincipal = lc.jifTypeSystem().conjunctivePrincipal(ct.position(), ct.authority());

    // Check the authority of the class against the superclass.
    if (ct.superType() instanceof JifClassType) {
        final JifClassType superType = (JifClassType) ct.superType();         
        
        for (final Principal pl : superType.authority()) {
            // authPrincipal must actfor pl i.e., at least one
            // of the principals that have authorized ct must act for pl.
            lc.constrain(authPrincipal, 
                         PrincipalConstraint.ACTSFOR, 
                        pl, 
                       A.labelEnv(),
                       n.position(),
                       new ConstraintMessage() {
                @Override
                public String msg() {
                    return "Superclass " + superType + " requires " + ct + " to " +
                    "have the authority of principal " + pl;
                }
                @Override
                public String detailMsg() {
                    return "The class " + superType + " has the authority of the " +
                    "principal " + pl + ". To extend this class, " + ct + 
                    " must also have the authority of " + pl + ".";
                }
            });             
            
        }
    }
    

    // Enter scope
    A = (JifContext) n.del().enterScope(A);
    lc = lc.context(A);
    
    // Access label checks
    Label classAccessLabel = ((FabricTypeSystem)jts).toLabel(pct.classAccessPolicy());
    
    // ({this} <= access label) holds true at all access sites 
    A.addAssertionLE(
        jts.pairLabel(Position.compilerGenerated(),
            jts.thisLabel(ct).confProjection(),
          jts.topIntegPolicy(Position.compilerGenerated())),
        singleAccessLabel);
    
    if (singleAccessLabel != null) {
    FabricTypeSystem ts = (FabricTypeSystem) lc.typeSystem();
    if (ts.isFabricClass(pct)) {
      // check that the access label has the top integrity label, i.e. it only
      // has a meaningful confidentiality component
      lc.constrain(
          new NamedLabel("top integ", 
              jts.pairLabel(Position.compilerGenerated(),
                  jts.bottomConfPolicy(Position.compilerGenerated()), 
                  jts.topIntegPolicy(Position.compilerGenerated()))),
          LabelConstraint.LEQ, new NamedLabel("access policy", classAccessLabel), A.labelEnv(), n.position(),
          new ConstraintMessage() {
            @Override
            public String msg() {
              return "The access policy should have no integrity component";
            }
          }
      );

      Label updateLabel = pct.classUpdateLabel();

      lc.constrain(new NamedLabel("object label", updateLabel),
          LabelConstraint.LEQ,
          new NamedLabel("access policy", classAccessLabel), A.labelEnv(),
          n.position(), new ConstraintMessage() {
            @Override
            public String msg() {
              return "The access policy of a class should be at least as restrictive"
                  + " as its object label";
            }
          });
    
    }

    // Provider label checks
    final ProviderLabel provider = ct.provider();
    NamedLabel namedProvider =
            new NamedLabel(provider.toString(), "provider of "
                    + provider.classType().fullName(), provider);
    lc.constrain(namedProvider, authPrincipal, A.labelEnv(), n.position(),
            new ConstraintMessage() {
                @Override
                public String msg() {
                    return provider + " must act for " + authPrincipal;
                }

                @Override
                public String detailMsg() {
                    return provider + " is the provider of " + ct
                            + " but does not have authority to act for "
                            + authPrincipal;
                }
            });
                    
    // label check class conformance
    labelCheckClassConformance(ct,lc);

    // label check the body
    ClassBody body = (ClassBody) lc.labelCheck(n.body());

    // let the label checker know that we have left the class body
    n = lc.leavingClassDecl((JifClassDecl)n.body(body));
    return n;
  }
  
}
