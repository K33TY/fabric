package regression;

public class Cast06 implements Cast06_I {
  public Cast06_I m() {
    Cast06 o = (Cast06) m();
    return o;
  }
}

interface Cast06_I {
  Cast06_I m();
}

