package fla.principals;

/**
 * Represents a labelled delegation from an inferior to a superior, stored at a
 * principal.
 */
public class Delegation<Inferior extends Principal, Superior extends Principal> {
  /**
   * The principal storing the delegation.
   */
  public final PrimitivePrincipal store;

  /**
   * The principal delegating its authority.
   */
  public final Inferior inferior;

  /**
   * The principal receiving authority.
   */
  public final Superior superior;

  /**
   * The label on the delegation.
   */
  public final Principal label;

  /**
   * @param store the principal storing the delegation
   * @param inferior the principal whose authority is being delegated
   * @param superior the principal being delegated to
   * @param label on the delegation
   */
  Delegation(PrimitivePrincipal store, Inferior inferior, Superior superior,
      Principal label) {
    this.store = store;
    this.inferior = inferior;
    this.superior = superior;
    this.label = label;
  }
}
