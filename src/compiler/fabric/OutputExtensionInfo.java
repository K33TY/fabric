package fabric;

import polyglot.ast.Node;
import polyglot.frontend.CyclicDependencyException;
import polyglot.frontend.EmptyPass;
import polyglot.frontend.Job;
import polyglot.frontend.Pass;
import polyglot.frontend.Scheduler;
import polyglot.frontend.Source;
import polyglot.frontend.goals.Goal;
import polyglot.frontend.goals.SourceFileGoal;
import polyglot.main.Options;
import polyglot.util.InternalCompilerError;
import fabil.FabILOptions;
import fabil.frontend.FabILScheduler;
import fabric.lang.Codebase;
import fabric.lang.security.LabelUtil;
import fabric.lang.security.NodePrincipal;
import fabric.worker.Store;
import fabric.worker.Worker;

/** A small extension of the fabil ExtensionInfo and Scheduler to perform fabil
 *  compilation of asts that have come from fabric.
 */
public class OutputExtensionInfo extends fabil.ExtensionInfo {

  protected ExtensionInfo fabext;
  private fabric.lang.security.Label destLabel;

  @Override
  public Scheduler createScheduler() {
    return new OutputScheduler(this);
  }
  
  @Override
  protected Options createOptions() {
    // we share the options with fabric, which in turn delegates to a
    // FabILOptions object for the fabil options handling.
    return fabext.getOptions(); 
  }
  
  public OutputExtensionInfo(ExtensionInfo fabext) {
    this.fabext = fabext;
  }
  
  public fabric.worker.Store destinationStore() {
    //Worker must be running!
    if(!Worker.isInitialized())
      throw new InternalCompilerError("Worker is not initialized.");
      
     FabILOptions opt = (FabILOptions) getOptions();
    
    if(opt.destinationStore() == null)
      return Worker.getWorker().getLocalStore();
    return Worker.getWorker().getStore(opt.destinationStore());
  }
  
  protected static class OutputScheduler extends FabILScheduler {
    protected Job objectJob;
    
    OutputScheduler(OutputExtensionInfo extInfo) {
      super(extInfo);
    }
    
    @Override
    public Job addJob(Source source, Node ast) {
      // We use the same hack here as in JifScheduler, but we need to override
      // to compare with Object.fab instead of Object.jif. Moreover, we need to
      // make sure it's referring to java.lang.Object (as opposed to
      // fabric.lang.Object)
      Job j = super.addJob(source, ast);
      if ("Object.fab".equals(source.name()) && source.path().contains("java")) {
        this.objectJob = j;
      }
      return j;
    }
    
    @Override
    public Job addJob(Source source) {
      // We use the same hack here as in JifScheduler, but we need to override
      // to compare with Object.fab instead of Object.jif. Moreover, we need to
      // make sure it's referring to java.lang.Object (as opposed to
      // fabric.lang.Object)
      Job j = super.addJob(source);
      if ("Object.fab".equals(source.name()) && source.path().contains("java")) {
          this.objectJob = j;
      }
      return j;
    }
    
    @Override
    public Goal TypesInitialized(Job job) {
      Goal g = super.TypesInitialized(job);
      try {
          if (objectJob != null && job != objectJob) {
              addPrerequisiteDependency(g, TypesInitialized(objectJob));
          }
      }
      catch (CyclicDependencyException e) {
          // Cannot happen
          throw new InternalCompilerError(e);
      }
      return g;
    }

    @Override
    public Goal Parsed(Job job) {
      if (job.ast() != null) {
        return internGoal(new SourceFileGoal(job) {
          @Override
          public Pass createPass(polyglot.frontend.ExtensionInfo einf) {
            return new EmptyPass(this);
          }
        });
      }
      return super.Parsed(job);
    }
  }
  
}
