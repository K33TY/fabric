package fabric.extension;

import java.util.ArrayList;
import java.util.List;

import polyglot.ast.ClassMember;
import polyglot.ast.FieldDecl;
import polyglot.ast.TypeNode;
import polyglot.qq.QQ;
import polyglot.types.Flags;
import fabric.visit.ProxyRewriter;

public class FieldDeclExt_c extends FabricExt_c implements ClassMemberExt {

  public List<ClassMember> implMember(ProxyRewriter pr) {
    FieldDecl fieldDecl  = (FieldDecl) node();
    Flags     fieldFlags =
      fieldDecl.flags().clear(Flags.PUBLIC)
                       .clear(Flags.PROTECTED)
                       .set(  Flags.PRIVATE)
                       .clear(Flags.FINAL);
    List<ClassMember> result = new ArrayList<ClassMember>();
    for (ClassMember m : accessors(pr))
      result.addAll(((ClassMemberExt) m.ext()).implMember(pr));
    result.add(fieldDecl.flags(fieldFlags));
    return result;
  }

  public List<ClassMember> interfaceMember(ProxyRewriter pr) {
    List<ClassMember> result = new ArrayList<ClassMember>();
    for (ClassMember m : accessors(pr))
      result.addAll(((ClassMemberExt) m.ext()).interfaceMember(pr));
    return result;
  }

  public List<ClassMember> proxyMember(ProxyRewriter pr) {
    List<ClassMember> result = new ArrayList<ClassMember>();
    for (ClassMember m : accessors(pr))
      result.addAll(((ClassMemberExt) m.ext()).proxyMember(pr));
    return result;
  }

  protected List<ClassMember> accessors(ProxyRewriter pr) {
    FieldDecl fieldDecl = (FieldDecl) node();
    Flags flags = fieldDecl.flags();
    QQ qq = pr.qq();
    
    // TODO need to handle static fields.
    
    TypeNode typeNode = fieldDecl.type();
    String name = "$" + fieldDecl.name();

    // TODO consider fields that point to Java-only objects.

    flags = flags.clear(Flags.TRANSIENT).clear(Flags.FINAL);
    List<ClassMember> members = new ArrayList<ClassMember> ();
    members.add(qq.parseMember(flags + " %T get" + name + "() {"
        + "diaspora.client.TransactionManager.INSTANCE"
        + ".registerRead(this);" + "return this." + name + "; }", typeNode));
    members.add(qq.parseMember(flags + " %T set" + name + "(%T val) {"
        + "diaspora.client.TransactionManager.INSTANCE"
        + ".registerWrite(this);" + "return this." + name + " = val; }",
        typeNode, typeNode));
    
    // Add post-incrementer and post-decrementer if type is numeric.
    if (typeNode.type().isNumeric()) {
      members.add(qq.parseMember(flags + " %T postInc" + name + "() {"
          + "%T tmp = this.get" + name + "();"
          + "this.set" + name + "(tmp + 1);"
          + "return tmp; }", typeNode, typeNode, typeNode));
      members.add(qq.parseMember(flags + " %T postDec" + name + "() {"
          + "%T tmp = this.get" + name + "();"
          + "this.set" + name + "(tmp - 1);"
          + "return tmp; }", typeNode, typeNode, typeNode));
    }
    return members;
  }
}
