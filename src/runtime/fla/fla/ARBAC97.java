package fla;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import fla.principals.PrimitivePrincipal;
import fla.principals.Principal;
import fla.principals.PrincipalUtil;

class ARBAC97 {
  static final Principal bottom = PrincipalUtil.bottom();

  /**
   * Represents a rule specifying which administrators can assign which roles
   * to which users.
   *
   * With this rule, an administrator, in the context of an organization
   * {@code o}, acting for {@code ar} may assign a user having role {@code c}
   * to any role between {@code xiMin} and {@code xiMax} (inclusive). To
   * assign a role {@code r} to a user {@code u}, the acts-for relationship
   * {@code o:u ≽ r} is established at {@code o} with label {@code ar←}.
   *
   * The invariant {@code ar} ≽ {@code xiMax} is assumed to hold.
   */
  static class CanAssignRule {
    /**
     * The owner.
     */
    final PrimitivePrincipal o;

    /**
     * The administrative authority required to assign roles with this rule.
     */
    final Principal ar;

    /**
     * The role-membership criterion: for all roles that can be assigned with
     * this rule, the members of those roles must already have role c.
     */
    final Principal c;

    /**
     * The minimum role that can be assigned with this rule.
     */
    final Principal xiMin;

    /**
     * The maximum role that can be assigned with this rule.
     */
    final Principal xiMax;

    public CanAssignRule(PrimitivePrincipal o, Principal ar, Principal c,
        Principal xiMin, Principal xiMax) {
      this.o = o;
      this.ar = ar;
      this.c = c;
      this.xiMin = xiMin;
      this.xiMax = xiMax;
    }

    /**
     * Uses this rule to assign user {@code u} to role {@code r} using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code u} already has role
     *          {@code r})
     */
    boolean assignUser(Principal a, Principal u, Principal r) {
      final Principal ou = o.project(u);

      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure o:u ≽ c with c←.
      if (!PrincipalUtil.actsFor(ar, ou, c, c.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Establish the delegation o:u ≽ r with ar←.
      o.addDelegatesTo(r, ou, ar.integrity());
      return true;
    }
  }

  /**
   * Represents a rule specifying which administrators can revoke which roles.
   *
   * With this rule, an administrator, in the context of an organization
   * {@code o}, acting for {@code ar} may revoke from any user any role
   * between {@code xiMin} and {@code xiMax} (inclusive). To revoke a role r
   * from a user u, the acts-for relationship o:u ≽ r is revoked at {@code o}.
   */
  static class CanRevokeRule {
    /**
     * The owner.
     */
    final PrimitivePrincipal o;

    /**
     * The administrative authority required to revoke roles with this rule.
     */
    final Principal ar;

    /**
     * The minimum role that can be revoked with this rule.
     */
    final Principal xiMin;

    /**
     * The maximum role that can be revoked with this rule.
     */
    final Principal xiMax;

    public CanRevokeRule(PrimitivePrincipal o, Principal ar, Principal xiMin,
        Principal xiMax) {
      this.o = o;
      this.ar = ar;
      this.xiMin = xiMin;
      this.xiMax = xiMax;
    }

    /**
     * Uses this rule to revoke role {@code r} from user {@code u} using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code u} did not already have
     *          role {@code r})
     */
    boolean revokeUser(Principal a, Principal u, Principal r) {
      final Principal ou = o.project(u);

      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Revoke the delegation o:u ≽ r with ar←.
      o.removeDelegatesTo(r, ou, ar.integrity());
      return true;
    }
  }

  /**
   * Represents a rule specifying which administrators can assign which
   * permissions to which roles.
   *
   * With this rule, an administrator, in the context of an organization
   * {@code o}, acting for {@code ar} may assign a permission {@code p}
   * to any role between {@code xiMin} and {@code xiMax} (inclusive), if
   * {@code p} was previously assigned to {@code c}. To assign a permission
   * {@code p} to a role {@code r}, the acts-for relationship
   * {@code r ≽ p} is established at {@code o} with label {@code ar←}.
   *
   * The invariant {@code ar} ≽ {@code p} is assumed to hold.
   */
  static class CanAssignPRule {
    /**
     * The owner.
     */
    final PrimitivePrincipal o;

    /**
     * The administrative authority required to assign permissions with this
     * rule.
     */
    final Principal ar;

    /**
     * The permission-assignment criterion: permissions that can be assigned
     * with this rule must already be assigned to c.
     */
    final Principal c;

    /**
     * The minimum role to which permissions can be assigned with this rule.
     */
    final Principal xiMin;

    /**
     * The maximum role to which permissions can be assigned with this rule.
     */
    final Principal xiMax;

    public CanAssignPRule(PrimitivePrincipal o, Principal ar, Principal c,
        Principal xiMin, Principal xiMax) {
      this.o = o;
      this.ar = ar;
      this.c = c;
      this.xiMin = xiMin;
      this.xiMax = xiMax;
    }

    /**
     * Uses this rule to assign permission {@code p} to role {@code r} using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code r} already has permission
     *          {@code p})
     */
    boolean assignPermission(Principal a, Principal p, Principal r) {
      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure c ≽ p with p←.
      if (!PrincipalUtil.actsFor(ar, c, p, p.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Establish the delegation r ≽ p with ar←.
      o.addDelegatesTo(p, r, ar.integrity());
      return true;
    }
  }

  /**
   * Represents a rule specifying which administrators can revoke which
   * permissions.
   *
   * With this rule, an administrator, in the context of an organization
   * {@code o}, acting for {@code ar} may revoke any permission from any role
   * between {@code xiMin} and {@code xiMax} (inclusive). To revoke a
   * permission {@code p} from a role {@code r}, the acts-for relationship r ≽
   * p is revoked at {@code o}.
   */
  static class CanRevokePRule {
    /**
     * The owner.
     */
    final PrimitivePrincipal o;

    /**
     * The administrative authority required to revoke permissions with this
     * rule.
     */
    final Principal ar;

    /**
     * The minimum role from which permissions can be revoked with this rule.
     */
    final Principal xiMin;

    /**
     * The maximum role from which permissions can be revoked with this rule.
     */
    final Principal xiMax;

    public CanRevokePRule(PrimitivePrincipal o, Principal ar, Principal xiMin,
        Principal xiMax) {
      this.o = o;
      this.ar = ar;
      this.xiMin = xiMin;
      this.xiMax = xiMax;
    }

    /**
     * Uses this rule to revoke permission {@code p} from role {@code r} using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code r} did not already have
     *          permission {@code p})
     */
    boolean revokePermission(Principal a, Principal p, Principal r) {
      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Revoke the delegation r ≽ p with ar←.
      o.removeDelegatesTo(p, r, ar.integrity());
      return true;
    }
  }

  /**
   * Represents a rule specifying which administrators can modify which role
   * ranges.
   *
   * With this rule, and administrator, in the context of an organization
   * {@code o}, acting for {@code ar} may insert or remove a role between
   * {@code xiMin} and {@code xiMax} (exclusive).
   */
  static class CanModifyRule {
    /**
     * The owner.
     */
    final PrimitivePrincipal o;

    /**
     * The administrative authority required to assign permissions with this
     * rule.
     */
    final Principal ar;

    /**
     * The lower bound of the role range that can be modified with this rule.
     */
    final Principal xiMin;

    /**
     * The upper bound of the role range that can be modified with this rule.
     */
    final Principal xiMax;

    public CanModifyRule(PrimitivePrincipal o, Principal ar, Principal xiMin,
        Principal xiMax) {
      this.o = o;
      this.ar = ar;
      this.xiMin = xiMin;
      this.xiMax = xiMax;
    }

    /**
     * Uses this rule to add a role {@code r} to the role range using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code r} was already in the
     *          range)
     */
    boolean addToRange(Principal a, Principal xiMin, Principal xiMax,
        Principal r) {
      // Ensure the given range matches the one in this rule.
      if (!PrincipalUtil.equals(xiMin, this.xiMin)) return false;
      if (!PrincipalUtil.equals(xiMax, this.xiMax)) return false;

      // Ensure r ≠ xiMin and r≠ xiMax.
      if (PrincipalUtil.equals(r, xiMin)) return false;
      if (PrincipalUtil.equals(r, xiMax)) return false;

      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Establish the delegations xiMax ≽ r and r ≽ xiMin with ar←.
      o.addDelegatesTo(xiMin, r, ar.integrity());
      o.addDelegatesTo(r, xiMax, ar.integrity());
      return true;
    }

    /**
     * Uses this rule to remove a role {@code r} from the range using
     * administrator {@code a}'s authority.
     *
     * @return true iff successful (even if {@code r} was already not in the
     *          range)
     */
    boolean removeFromRange(Principal a, Principal xiMin, Principal xiMax,
        Principal r) {
      // Ensure the given range matches the one in this rule.
      if (!PrincipalUtil.equals(xiMin, this.xiMin)) return false;
      if (!PrincipalUtil.equals(xiMax, this.xiMax)) return false;

      // Ensure r ≠ xiMin and r≠ xiMax.
      if (PrincipalUtil.equals(r, xiMin)) return false;
      if (PrincipalUtil.equals(r, xiMax)) return false;

      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Revoke the delegations xiMax ≽ r and r ≽ xiMin with ar←.
      o.removeDelegatesTo(xiMin, r, ar.integrity());
      o.removeDelegatesTo(r, xiMax, ar.integrity());
      return true;
    }

    /**
     * Uses this rule to add a delegation from role {@code s} to role {@code r},
     * using administrator {@code a}'s authority, where {@code s} and {@code r}
     * are in the range.
     *
     * @return true iff successful (even if {@code s} already delegates to
     *          {@code r})
     */
    boolean addAsSenior(Principal a, Principal r, Principal s) {
      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Ensure s ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, s, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ s with s←.
      if (!PrincipalUtil.actsFor(ar, xiMax, s, s.integrity(), bottom))
        return false;

      // Establish the delegation r ≽ s with ar←.
      o.addDelegatesTo(s, r, ar.integrity());
      return true;
    }

    /**
     * Uses this rule to remove a delegation from role {@code s} to role {@code
     * r}, using administrator {@code a}'s authority, where {@code s} and {@code
     * r} are in the range.
     *
     * @return true iff successful (even if {@code s} already does not delegate
     *          to {@code r})
     */
    boolean removeAsSenior(Principal a, Principal r, Principal s) {
      // Ensure a ≽ ar with ar←.
      if (!PrincipalUtil.actsFor(ar, a, ar, ar.integrity(), bottom))
        return false;

      // Ensure r ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, r, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ r with r←.
      if (!PrincipalUtil.actsFor(ar, xiMax, r, r.integrity(), bottom))
        return false;

      // Ensure s ≽ xiMin with xiMin←.
      if (!PrincipalUtil.actsFor(ar, s, xiMin, xiMin.integrity(), bottom))
        return false;

      // Ensure xiMax ≽ s with s←.
      if (!PrincipalUtil.actsFor(ar, xiMax, s, s.integrity(), bottom))
        return false;

      // Revoke the delegation r ≽ s with ar←.
      o.removeDelegatesTo(s, r, ar.integrity());
      return true;
    }
  }

  final Set<ARBAC97.CanAssignRule> canAssign;
  final Set<ARBAC97.CanRevokeRule> canRevoke;
  final Set<ARBAC97.CanAssignPRule> canAssignP;
  final Set<ARBAC97.CanRevokePRule> canRevokeP;
  final Set<ARBAC97.CanModifyRule> canModify;

  ARBAC97(Set<ARBAC97.CanAssignRule> canAssign,
      Set<ARBAC97.CanRevokeRule> canRevoke,
      Set<ARBAC97.CanAssignPRule> canAssignP,
      Set<ARBAC97.CanRevokePRule> canRevokeP,
      Set<ARBAC97.CanModifyRule> canModify) {
    this.canAssign = Collections.unmodifiableSet(new HashSet<>(canAssign));
    this.canRevoke = Collections.unmodifiableSet(new HashSet<>(canRevoke));
    this.canAssignP = Collections.unmodifiableSet(new HashSet<>(canAssignP));
    this.canRevokeP = Collections.unmodifiableSet(new HashSet<>(canRevokeP));
    this.canModify = Collections.unmodifiableSet(new HashSet<>(canModify));
  }

  /**
   * Assigns user {@code u} to role {@code r} using administrator {@code a}'s
   * authority.
   *
   * @return true iff successful (even if {@code u} already has role {@code r})
   */
  boolean assignUser(Principal a, Principal u, Principal r) {
    for (ARBAC97.CanAssignRule rule : canAssign) {
      if (rule.assignUser(a, u, r)) return true;
    }

    return false;
  }

  /**
   * Revokes role {@code r} from user {@code u} using administrator {@code
   * a}'s authority.
   *
   * @return true iff successful (even if {@code u} did not already have role
   *          {@code r})
   */
  boolean revokeUser(Principal a, Principal u, Principal r) {
    for (ARBAC97.CanRevokeRule rule : canRevoke) {
      if (rule.revokeUser(a, u, r)) return true;
    }

    return false;
  }

  /**
   * Assigns permission {@code p} to role {@code r} using administrator {@code
   * a}'s authority.
   *
   * @return true iff successful (even if {@code r} already has permission
   *          {@code p})
   */
  boolean assignPermission(Principal a, Principal p, Principal r) {
    for (ARBAC97.CanAssignPRule rule : canAssignP) {
      if (rule.assignPermission(a, p, r)) return true;
    }

    return false;
  }

  /**
   * Revokes permission {@code p} from role {@code r} using administrator
   * {@code a}'s authority.
   *
   * @return true iff successful (even if {@code r} did not already have
   *          permission {@code p})
   */
  boolean revokePermission(Principal a, Principal p, Principal r) {
    for (ARBAC97.CanRevokePRule rule : canRevokeP) {
      if (rule.revokePermission(a, p, r)) return true;
    }

    return false;
  }

  /**
   * Adds a role {@code r} to the role range using administrator {@code a}'s
   * authority.
   *
   * @return true iff successful (even if {@code r} was already in the
   *          range)
   */
  boolean addToRange(Principal a, Principal xiMin, Principal xiMax, Principal r) {
    for (ARBAC97.CanModifyRule rule : canModify) {
      if (rule.addToRange(a, xiMin, xiMax, r)) return true;
    }

    return false;
  }

  /**
   * Removes a role {@code r} from the role range using administrator {@code
   * a}'s authority.
   *
   * @return true iff successful (even if {@code r} was already not in the
   *          range)
   */
  boolean removeFromRange(Principal a, Principal xiMin, Principal xiMax,
      Principal r) {
    for (ARBAC97.CanModifyRule rule : canModify) {
      if (rule.removeFromRange(a, xiMin, xiMax, r)) return true;
    }

    return false;
  }

  /**
   * Adds a delegation from role {@code s} to role {@code r}, using
   * administrator {@code a}'s authority.
   *
   * @return true iff successful (even if {@code s} already delegates to
   *          {@code r})
   */
  boolean addAsSenior(Principal a, Principal r, Principal s) {
    for (ARBAC97.CanModifyRule rule : canModify) {
      if (rule.addAsSenior(a, r, s)) return true;
    }

    return false;
  }

  /**
   * Removes a delegation from role {@code s} to role {@code r}, using
   * administrator {@code a}'s authority.
   *
   * @return true iff successful (even if {@code s} already does not delegate
   *          to {@code r})
   */
  boolean removeAsSenior(Principal a, Principal r, Principal s) {
    for (ARBAC97.CanModifyRule rule : canModify) {
      if (rule.removeAsSenior(a, r, s)) return true;
    }

    return false;
  }
}