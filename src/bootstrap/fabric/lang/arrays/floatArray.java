package fabric.lang.arrays;

import fabric.client.Core;
import fabric.lang.Object;
import jif.lang.Label;

public interface floatArray extends Object {
  float get(int i);

  float set(int i, float value);

  public static class $Impl extends Object.$Impl implements floatArray {
    public $Impl(Core core, Label label, int length) {
      super(core, label);
    }

    public native float get(int i);

    public native float set(int i, float value);
  }
}
