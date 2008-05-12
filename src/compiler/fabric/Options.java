package fabric;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import polyglot.frontend.ExtensionInfo;
import polyglot.main.UsageError;
import polyglot.main.Main.TerminationException;

/**
 * This is the same as the JL options, except by default, we always generate
 * fully qualified class names. This is here because the logic for qualifying
 * class names seems a bit wonky.
 */
public class Options extends polyglot.main.Options {
  /**
   * Whether we're running in signature mode.
   */
  public boolean signatureMode;

  /**
   * The classpath for the Fabric signatures of Java objects.
   */
  public String sigcp;

  /**
   * Additional classpath entries for Fabric signatures.
   */
  public List<String> addSigcp;
  
  /** Whether to perform optimizations. */
  public boolean optimize;
  
  public static Options global() {
    return (Options) global;
  }

  public Options(ExtensionInfo extension) {
    super(extension);
    this.sigcp = null;
    this.addSigcp = new ArrayList<String>();
  }

  /*
   * (non-Javadoc)
   * 
   * @see polyglot.main.Options#setDefaultValues()
   */
  @Override
  public void setDefaultValues() {
    super.setDefaultValues();
    this.fully_qualified_names = true;
    this.signatureMode = false;
    this.optimize = false;
  }

  /*
   * (non-Javadoc)
   * 
   * @see polyglot.main.Options#parseCommand(java.lang.String[], int,
   *      java.util.Set)
   */
  @SuppressWarnings("unchecked")
  @Override
  protected int parseCommand(String[] args, int index, Set source)
      throws UsageError, TerminationException {
    if (args[index].equals("-sig")) {
      index++;
      signatureMode = true;
    } else if (args[index].equals("-sigcp")) {
      index++;
      this.sigcp = args[index++];
    } else if (args[index].equals("-addsigcp")) {
      index++;
      this.addSigcp.add(args[index++]);
    } else if (args[index].equals("-O")) {
      index++;
      this.optimize = true;
    } else {
      return super.parseCommand(args, index, source);
    }

    return index;
  }

  /*
   * (non-Javadoc)
   * 
   * @see polyglot.main.Options#usage(java.io.PrintStream)
   */
  @Override
  public void usage(PrintStream out) {
    super.usage(out);
    usageForFlag(out, "-sig", "compile sources to signatures");
    usageForFlag(out, "-sigcp <path>",
        "path for Fabric signatures (e.g. for fabric.lang.Object)");
    usageForFlag(out, "-addsigcp <path>",
        "additional path for Fabric signatures; prefixed to sigcp");
    usageForFlag(out, "-O", "turn optimizations on");
  }

  public String constructSignatureClasspath() {
    // Use the signature classpath if it exists for compiling Fabric classes.
    String scp = "";
    for (String item : addSigcp)
      scp += File.pathSeparator + item;

    if (sigcp != null) scp += File.pathSeparator + sigcp;

    return scp;
  }

  public String constructFabricClasspath() {
    return constructSignatureClasspath() + File.pathSeparator
        + constructFullClasspath();
  }

  @Override
  public String constructPostCompilerClasspath() {
    return super.constructPostCompilerClasspath() + File.pathSeparator
        + constructFullClasspath();
  }
}
