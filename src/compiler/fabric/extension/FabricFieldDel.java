package fabric.extension;

import jif.extension.JifFieldDel;
import jif.types.label.Label;

public class FabricFieldDel extends JifFieldDel implements FabricStagingDel {

  /**
   * Squirreled away the stage labels to check in rewritten code.
   */
  protected Label startStage;
  protected Label endStage;

  @Override
  public Label startStage() {
    return startStage;
  }

  @Override
  public Label endStage() {
    return endStage;
  }

  @Override
  public void setStageCheck(Label startStage, Label endStage) {
    this.startStage = startStage;
    this.endStage = endStage;
  }

}