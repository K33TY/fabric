package fla.principals;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import fabric.common.exceptions.InternalError;
import fabric.common.util.Pair;
import fla.util.ActsForProof;
import fla.util.ConfProjectionProof;
import fla.util.DelegatesProof;
import fla.util.FromDisjunctProof;
import fla.util.IntegProjectionProof;
import fla.util.MeetToOwnerProof;
import fla.util.OwnedPrincipalsProof;
import fla.util.ReflexiveProof;
import fla.util.ToConjunctProof;
import fla.util.TransitiveProof;

public abstract class Principal {
  /**
   * @return the confidentiality projection of this principal. The returned
   *          principal will be in normal form if this principal is.
   */
  public abstract Principal confidentiality();

  /**
   * @return the integrity projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  public abstract Principal integrity();

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  public abstract Principal project(Principal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(TopPrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(PrimitivePrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(ConfPrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(IntegPrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(OwnedPrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(ConjunctivePrincipal projection);

  /**
   * @return an ownership projection of this principal. The returned principal
   *          will be in normal form if this principal is.
   */
  abstract Principal project(DisjunctivePrincipal projection);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  public abstract Principal owner(Principal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(TopPrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(PrimitivePrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(ConfPrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(IntegPrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(OwnedPrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(ConjunctivePrincipal owner);

  /**
   * @return the ownership projection owner:this. The returned principal will be
   *          in normal form if this principal is.
   */
  abstract Principal owner(DisjunctivePrincipal owner);

  /**
   * @return {@code true} iff the given object is a syntactically equivalent
   *          principal.
   */
  @Override
  public final boolean equals(Object obj) {
    if (obj instanceof Principal) return equals((Principal) obj);
    return false;
  }

  /**
   * @return {@code true} iff the given principal is syntactically equivalent to
   *          this one.
   */
  public abstract boolean equals(Principal p);

  /**
   * @return the set of {@code PrimitivePrincipal}s that appear as components of
   *          this principal.
   */
  abstract Set<PrimitivePrincipal> componentPrimitivePrincipals();

  /**
   * @return the delegation set stored at this principal, represented as a map
   *          from labels to delegations at that label. If this principal
   *          supports the storage of delegations, then modifications to the
   *          returned map should reflect modifications to the stored
   *          delegations. Otherwise, an empty unmodifiable map should be
   *          returned.
   */
  abstract Map<Principal, Set<Delegation<?, ?>>> delegations();

  /**
   * Obtains a set of delegations that are stored at this principal and can be
   * used to answer the given query. Each delegation is mapped to a proof
   * showing that it can be used.
   * <p>
   * A delegation may be used to answer a query if the label on the delegation
   * flows to {@code query.maxLabel}.
   * <p>
   * If {@code query.maxUsableLabel} or {@code query.accessPolicy} is {@code
   * null}, then a static context is assumed (in which no dynamic delegations
   * exists), and this method returns an empty set.
   *
   * @param searchState the state of the proof search being made
   */
  private final Map<Delegation<?, ?>, ActsForProof<?, ?>> usableDelegations(
      ActsForQuery<?, ?> query, ProofSearchState searchState) {
    if (!query.useDynamicContext()) {
      // Static context. No dynamic delegations should be used.
      return Collections.emptyMap();
    }

    Map<Delegation<?, ?>, ActsForProof<?, ?>> result = new HashMap<>();

    for (Map.Entry<Principal, Set<Delegation<?, ?>>> entry : delegations()
        .entrySet()) {
      Principal delegationLabel = entry.getKey();
      Principal queryLabel = query.maxUsableLabel;

      // Can use delegations if we can robustly show
      //   delegationLabel ⊑ queryLabel
      //     with queryLabel
      //     at (query.accessPolicy ∧ delegationLabel→).
      //
      // The confidentiality of the answer to this subquery is the same as that
      // of the answer to the top-level query:
      //   queryLabel→.
      //
      // To ensure the integrity of the answer to the top-level query, the
      // integrity of the answer to this subquery acts for queryLabel←.
      //
      // To ensure the robustness of this subquery, the integrity of its answer
      // may be higher; this is encapsulated in the "makeRobust()" call below.
      //
      // The access policy on this subquery ensures the confidentiality of both
      // the top-level query and the delegation.
      ActsForQuery<?, ?> subquery =
          ActsForQuery.flowsToQuery(
              delegationLabel,
              queryLabel,
              queryLabel,
              PrincipalUtil.conjunction(query.accessPolicy,
                  delegationLabel.confidentiality())).makeRobust();
      ActsForProof<?, ?> usabilityProof =
          actsForProof(this, subquery, searchState);
      if (usabilityProof != null) {
        for (Delegation<?, ?> delegation : entry.getValue()) {
          result.put(delegation, usabilityProof);
        }
      }
    }

    return result;
  }

  /**
   * Obtains a set of principals to whom the given query can be forwarded. A
   * query can be forwarded to any PrimitivePrincipal {@code p} that
   * <ul>
   * <li>appears as a component of this principal (if this is a {@code
   * NonPrimitivePrincipal}),</li>
   * <li>appears as a component of the superior or inferior parts of the
   * query,</li>
   * <li>appears as a component of a <i>usable</i> delegation stored at this
   * principal, or</li>
   * <li>appears in the search state as a participant in the query,</li>
   * </ul>
   * such that {@code p} acts for {@code query.accessPolicy} and the
   * integrity projection of {@code query.maxLabel}.
   * <p>
   * If {@code query.maxUsableLabel} or {@code query.accessPolicy} is {@code
   * null}, then a static context is assumed (in which no dynamic delegations
   * exists, so forwarding of queries is not useful), and this method returns an
   * empty set.
   *
   * @param searchState the state of the proof search being made
   */
  private final Set<PrimitivePrincipal> askablePrincipals(
      ActsForQuery<?, ?> query, ProofSearchState searchState) {
    if (!query.useDynamicContext()) {
      // Static context. No dynamic delegations should be used.
      return Collections.emptySet();
    }

    // Construct an unfiltered set of candidates.
    // Start with all participants in the search state.
    Set<PrimitivePrincipal> unfilteredResult =
        new HashSet<>(searchState.allParticipants);

    // Add primitive principals that appear as a component of this principal.
    unfilteredResult.addAll(componentPrimitivePrincipals());

    // Add primitive principals that appears as a component of the query.
    unfilteredResult.addAll(query.componentPrimitivePrincipals());

    // Add components of usable delegations.
    for (Delegation<?, ?> delegation : usableDelegations(query, searchState)
        .keySet()) {
      // XXX Dropping usability proofs. Should use them to prove correctness of the return value.
      unfilteredResult.addAll(delegation.componentPrimitivePrincipals());
    }

    // Filter.
    return removeUnaskablePrincipals(unfilteredResult, query, searchState);
  }

  /**
   * Removes from the given set all principals that cannot be asked the given
   * query. A principal can be asked if it acts for {@code query.accessPolicy}
   * and the integrity projection of {@code query.maxLabel}.
   *
   * @param searchState the state of the proof search being made
   *
   * @return the object {@code set}, with all unaskable principals having been
   *          removed
   */
  private final Set<PrimitivePrincipal> removeUnaskablePrincipals(
      Set<PrimitivePrincipal> set, ActsForQuery<?, ?> query,
      ProofSearchState searchState) {
    for (Iterator<PrimitivePrincipal> it = set.iterator(); it.hasNext();) {
      PrimitivePrincipal p = it.next();

      // See if p ≽ query.recurseLowerBound(), robustly.
      ActsForQuery<?, ?> subquery =
          query.inferior(query.recurseLowerBound()).superior(p).makeRobust();
      if (actsForProof(this, subquery, searchState) == null) {
        // Unable to prove the askability of p. Remove.
        it.remove();
      }
    }

    return set;
  }

  /**
   * Asks this principal whether it can find the (direct) delegation "{@code
   * query.superior} ≽ {@code query.inferior}" whose label flows to {@code
   * query.maxLabel}.
   * <p>
   * When making recursive calls, any principals receiving those calls must act
   * for {@code query.accessPolicy} and the integrity projection of {@code
   * query.maxLabel}. It is assumed that {@code query.accessPolicy} acts for the
   * confidentiality component of {@code query.maxLabel}. As such, no explicit
   * checks will be made to ensure that principals receiving recursive calls
   * will act for this component (by the above assumption, such a check would be
   * subsumed by the check against {@code query.accessPolicy}).
   * <p>
   * It is also assumed that {@code query.accessPolicy} has no integrity
   * component.
   * <p>
   * A final assumption that is not explicitly checked is that this principal
   * acts for both {@code query.maxLabel} and {@code query.accessPolicy}.
   * (Otherwise, we have no business making this query to this principal!)
   * <p>
   * If {@code query.maxUsableLabel} or {@code query.accessPolicy} is {@code
   * null}, then a static context is assumed (in which no dynamic delegations
   * exists), and this method returns false.
   */
  final boolean delegatesTo(ActsForQuery<?, ?> query) {
    for (Delegation<?, ?> delegation : usableDelegations(query,
        new ProofSearchState()).keySet()) {
      if (PrincipalUtil.equals(query.inferior, delegation.inferior)
          && PrincipalUtil.equals(query.superior, delegation.superior))
        return true;
    }

    return false;
  }

  /**
   * Asks this principal whether it can find a proof for "{@code superior} ≽
   * {@code inferior}". Labels on the delegations used in this proof must flow
   * to {@code maxUsableLabel}.
   * <p>
   * When making recursive calls, any principals receiving those calls must act
   * for {@code accessPolicy} and the integrity projection of {@code
   * maxUsableLabel}. It is assumed that {@code accessPolicy} acts for the
   * confidentiality component of {@code maxUsableLabel}. As such, no explicit
   * checks will be made to ensure that principals receiving recursive calls
   * will act for this component (by the above assumption, such a check would
   * be subsumed by the check against {@code accessPolicy}).
   * <p>
   * It is also assumed that {@code accessPolicy} has no integrity component.
   * <p>
   * A final assumption that is not explicitly checked is that this principal
   * acts for both {@code maxUsableLabel} and {@code accessPolicy}. (Otherwise,
   * we have no business making this query to this principal!)
   * <p>
   * If {@code maxUsableLabel} or {@code accessPolicy} is {@code null}, then a
   * static context is assumed, and dynamic delegations will be ignored.
   *
   * @param superior the potential superior
   * @param inferior the potential inferior
   * @param maxUsableLabel labels on delegations used in the proof must flow to
   *          this label
   * @param accessPolicy the confidentiality level of the query. This should
   *          act for the confidentiality component of {@code maxUsableLabel}
   */
  public final boolean actsFor(Principal superior, Principal inferior,
      Principal maxUsableLabel, Principal accessPolicy) {
    return actsForProof(new ActsForQuery<>(superior, inferior, maxUsableLabel,
        accessPolicy)) != null;
  }

  public final boolean flowsTo(Principal inferior, Principal superior,
      Principal maxUsableLabel, Principal accessPolicy) {
    return actsForProof(ActsForQuery.flowsToQuery(inferior, superior,
        maxUsableLabel, accessPolicy)) != null;
  }

  /**
   * Revokes delegations of the form <code>superior ≽ inferior {L}</code>, where
   * {@code label ⊑ L}, at all principals reachable from this one.
   *
   * @param inferior the principal whose delegations are being revoked
   * @param superior the principal whose privileges are being revoked
   * @param label the label on the revocation. Labels on revoked delegations
   *          must flow to this label.
   */
  public final void removeDelegatesTo(Principal inferior, Principal superior,
      Principal label) {
    removeDelegatesTo(this, new RevocationRequest(inferior, superior, label),
        Collections.singleton(this));
  }

  /**
   * Recursively calls {@link Principal#removeDelegatesTo(Principal,
   * RevocationRequest, Set)} on those elements of {@code candidates} for which
   * it is safe to do so.
   *
   * @param req the revocation request
   * @param candidates the set of candidates on which to recurse
   * @param visited the set of principals already called so far
   * @param delegationLabel the label on the delegations from which the
   *          candidate set was derived. This can be {@code null} if the
   *          candidate set was not derived from any delegations, in which case
   *          this will be treated as ⊤←.
   * @return the set of candidates to which recursive calls were made. In
   *          principle, this will have label {@code req.label ∧
   *          delegationLabel→ ∧ (readersToWriters(req.label) ∨
   *          readersToWriters(delegationLabel))}.
   *
   */
  private final Set<PrimitivePrincipal> recurseRemoveDelegatesTo(
      RevocationRequest req, Set<PrimitivePrincipal> candidates,
      Set<Principal> visited, Principal delegationLabel) {
    if (delegationLabel == null) {
      // Default to bottom confidentiality, top integrity.
      delegationLabel = PrincipalUtil.top().integrity();
    }

    // Remove all candidates that we can't safely visit.
    for (Iterator<PrimitivePrincipal> it = candidates.iterator(); it.hasNext();) {
      PrimitivePrincipal candidate = it.next();
      {
        // We can recurse only if we can robustly show:
        //   candidate ≽ req.label→
        //     with (req.label ∧ delegationLabel→)
        //     at req.label→ ∧ delegationLabel→.
        //
        // Because the confidentiality of the recursive calls will be
        //   req.label→ ∧ delegationLabel→,
        // this the confidentiality of the query result.
        //
        // To ensure the integrity of the revocation request, the integrity of
        // the query result acts for req.label←.
        //
        // To ensure the robustness of this query, the integrity of its result
        // may be higher; this is encapsulated in the "makeRobust()" call below.
        // NB: this is why, for completeness, we should not combine this query
        // with the one below.
        //
        // The access policy ensures the confidentiality of both the revocation
        // request and the delegation.
        Principal superior = candidate;
        Principal inferior = req.label.confidentiality();
        Principal label =
            PrincipalUtil.conjunction(req.label,
                delegationLabel.confidentiality());
        Principal accessPolicy =
            PrincipalUtil.conjunction(req.label.confidentiality(),
                delegationLabel.confidentiality());
        ActsForQuery<Principal, Principal> query =
            new ActsForQuery<>(superior, inferior, label, accessPolicy)
            .makeRobust();
        if (actsForProof(query) == null) {
          // Can't recurse to this candidate.
          it.remove();
          continue;
        }
      }

      {
        // We can recurse only if we can show
        //   candidate ≽ delegationLabel→
        //     with (req.label ∧ delegationLabel→)
        //     at req.label→ ∧ delegationLabel→.
        //
        // Because the confidentiality of the recursive calls will be
        //   req.label→ ∧ delegationLabel→,
        // this the confidentiality of the query result.
        //
        // To ensure the integrity of the revocation request, the integrity of
        // the query result acts for req.label←.
        //
        // To ensure robustness of this query, the integrity its result may be
        // higher; this is encapsulated in the "makeRobust()" call below.
        // NB: this is why, for completeness, we should not combine this query
        // with the one above.
        //
        // The access policy ensures the confidentiality of both the revocation
        // request and the delegation.
        Principal superior = candidate;
        Principal inferior = delegationLabel.confidentiality();
        Principal label =
            PrincipalUtil.conjunction(req.label,
                delegationLabel.confidentiality());
        Principal accessPolicy =
            PrincipalUtil.conjunction(req.label.confidentiality(),
                delegationLabel.confidentiality());
        ActsForQuery<Principal, Principal> query =
            new ActsForQuery<>(superior, inferior, label, accessPolicy)
            .makeRobust();
        if (actsForProof(query) == null) {
          // Can't recurse to this candidate.
          it.remove();
          continue;
        }
      }
    }

    // The contents of candidates has label
    //   req.label ∧ delegationLabel→,
    // which every element of candidates now acts for, so it is safe to include
    // it as part of the visited set for recursive calls made to those
    // candidates.
    visited = new HashSet<>(visited);
    visited.addAll(candidates);
    visited = Collections.unmodifiableSet(visited);

    // Actually recurse. Recursive calls will have label
    //   req.label ∧ delegationLabel→
    for (Principal p : candidates) {
      p.removeDelegatesTo(this, req.joinLabel(delegationLabel), visited);
    }

    return candidates;
  }

  /**
   * Revokes delegations matching the given request, at all principals reachable
   * from this one.
   *
   * @param visited a set of principals that have already received the request,
   *          including {@code this}
   */
  private final void removeDelegatesTo(Principal caller, RevocationRequest req,
      Set<Principal> visited) {
    // If this is a remote call, ensure the caller acts for the request label's
    // integrity.
    if (caller != this) {
      req = req.meetIntegrity(caller);
    }

    // Recurse into other principals that we haven't visited yet.
    // First, consider components of this principal and of the request itself.
    {
      Set<PrimitivePrincipal> candidates = new HashSet<>();
      candidates.addAll(componentPrimitivePrincipals());
      candidates.addAll(req.componentPrimitivePrincipals());
      candidates.removeAll(visited);

      // The set of candidates we actually recurse to will have label
      //   {req.label ∧ readersToWriters(req.label)},
      // so it is safe to include it as part of the visited set for all
      // recursive calls we make.
      visited = new HashSet<>(visited);
      visited.addAll(recurseRemoveDelegatesTo(req, candidates, visited, null));
      visited = Collections.unmodifiableSet(visited);
    }

    // Now, consider recursing into components of any delegations we store.
    for (Map.Entry<Principal, Set<Delegation<?, ?>>> entry : delegations()
        .entrySet()) {
      Principal label = entry.getKey();
      Set<Delegation<?, ?>> delegations = entry.getValue();

      // Get a set of candidates for recursing into.
      Set<PrimitivePrincipal> candidates = new HashSet<>();
      for (Delegation<?, ?> delegation : delegations) {
        candidates.addAll(delegation.componentPrimitivePrincipals());
      }
      candidates.removeAll(visited);

      // Recurse.
      recurseRemoveDelegatesTo(req, candidates, visited, label);
    }

    // Remove any local delegations that match.
    for (Iterator<Map.Entry<Principal, Set<Delegation<?, ?>>>> entryIt =
        delegations().entrySet().iterator(); entryIt.hasNext();) {
      Map.Entry<Principal, Set<Delegation<?, ?>>> entry = entryIt.next();
      Principal label = entry.getKey();

      // Delegations at this level can match only if we can robustly show:
      //   req.label ⊑ label
      //     with req.label
      //     at req.label→ ∧ label→.
      //
      // Because the effect of removal will be visible at req.label→, this is
      // the confidentiality of the query result.
      //
      // To ensure the integrity of the revocation request, the integrity of the
      // query result acts for req.label←.
      //
      // For robustness, the integrity of this query's result may be higher;
      // this is encapsulated in the "makeRobust()" call below.
      //
      // The access policy ensures the confidentiality of both the revocation
      // request and the delegation.
      Principal inferior = req.label;
      Principal superior = label;
      Principal queryLabel = req.label;
      Principal accessPolicy =
          PrincipalUtil.join(label.confidentiality(),
              req.label.confidentiality());
      if (actsForProof(ActsForQuery.flowsToQuery(inferior, superior,
          queryLabel, accessPolicy).makeRobust()) == null) {
        continue;
      }

      Set<Delegation<?, ?>> delegations = entry.getValue();
      for (Iterator<Delegation<?, ?>> it = delegations.iterator(); it.hasNext();) {
        Delegation<?, ?> delegation = it.next();
        if (!PrincipalUtil.equals(delegation.inferior, req.inferior)) continue;
        if (!PrincipalUtil.equals(delegation.superior, req.superior)) continue;
        it.remove();
      }

      if (delegations.isEmpty()) entryIt.remove();
    }
  }

  /**
   * Asks this principal for a proof of "{@code query.superior} ≽ {@code
   * query.inferior}". Labels on the delegations used in this proof must flow to
   * {@code query.maxUsableLabel}.
   * <p>
   * When making recursive calls, any principals receiving those calls must act
   * for {@code query.accessPolicy} and the integrity projection of {@code
   * query.maxUsableLabel}. It is assumed that {@code query.accessPolicy} acts
   * for the confidentiality component of {@code query.maxUsableLabel}. As such,
   * no explicit checks will be made to ensure that principals receiving
   * recursive calls will act for this component (by the above assumption, such
   * a check would be subsumed by the check against {@code query.accessPolicy}).
   * <p>
   * It is also assumed that {@code accessPolicy} has no integrity component.
   * <p>
   * A final assumption that is not explicitly checked is that this principal
   * acts for both {@code query.maxUsableLabel} and {@code query.accessPolicy}.
   * (Otherwise, we have no business making this query to this principal!)
   * <p>
   * If {@code query.maxUsableLabel} or {@code query.accessPolicy} is {@code
   * null}, then a static context is assumed, and dynamic delegations will be
   * ignored.
   */
  private final <Superior extends Principal, Inferior extends Principal> ActsForProof<Superior, Inferior> actsForProof(
      ActsForQuery<Superior, Inferior> query) {
    // TODO Verify proof?
    return actsForProof(this, query, new ProofSearchState());
  }

  /**
   * Answers the query while trying to use the cache in the search state. On a
   * cache miss, a proof search is made using {@code findActsForProof()}, and
   * the result is added to the cache.
   *
   * @param caller the principal making this call
   * @param searchState records the goals that we are in the middle of
   *          attempting, and any cached intermediate results
   */
  final <Superior extends Principal, Inferior extends Principal> ActsForProof<Superior, Inferior> actsForProof(
      Principal caller, ActsForQuery<Superior, Inferior> query,
      ProofSearchState searchState) {
    // If this is a remote call, ensure the caller is trusted with the
    // confidentiality of the query's answer.
    if (caller != this) {
      query = query.meetLabel(caller);
    }

    // Check the cache.
    Pair<Boolean, ActsForProof<Superior, Inferior>> cachedResult =
        searchState.cacheLookup(query);

    if (cachedResult != null) {
      if (cachedResult.first) {
        // Positive cache hit.
        return cachedResult.second;
      } else {
        // Negative cache hit.
        return null;
      }
    }

    // Cache miss. Search for a proof.
    ProofSearchResult<Superior, Inferior> result =
        findActsForProof(caller, query, searchState);

    switch (result.type) {
    case SUCCESS:
      // Cache positive result.
      searchState.cacheActsFor(query, result.proof);
      return result.proof;

    case PRUNED:
      // Don't cache.
      return null;

    case FAILED:
      // Cache negative result.
      searchState.cacheNotActsFor(query);
      return null;
    }

    throw new InternalError("Unexpected acts-for-proof search result type: "
        + result.type);
  }

  /**
   * Searches for an ActsForProof, assuming a cache miss. Callers are
   * responsible for caching the results.
   *
   * @param caller the principal making this call
   * @param searchState records the goals that we are in the middle of
   *          attempting, and any cached intermediate results
   * @param query the query to satisfy. It is assumed that the caller acts for
   *          the label in the query.
   */
  private final <Superior extends Principal, Inferior extends Principal> ProofSearchResult<Superior, Inferior> findActsForProof(
      Principal caller, ActsForQuery<Superior, Inferior> query,
      ProofSearchState searchState) {
    final boolean forwarded = caller != this;

    // If this is a forwarded query, just skip to the part that uses
    // delegations directly. The caller should have done the rest already.
    if (!forwarded) {
      // Try the dumb things first.
      if (query.inferior instanceof BottomPrincipal
          || query.superior instanceof TopPrincipal) {
        return new ProofSearchResult<>(new DelegatesProof<>(query));
      }

      if (PrincipalUtil.equals(query.inferior, query.superior)) {
        return new ProofSearchResult<>(new ReflexiveProof<>(query));
      }

      // Add the query to the search state.
      try {
        searchState = new ProofSearchState(searchState, query);
      } catch (CircularProofException e) {
        // Already a goal. Prevent an infinite recursion, but don't cache this
        // result.
        return ProofSearchResult.pruned();
      }
    }

    // See if we can satisfy the query directly with a delegation.
    for (Map.Entry<Delegation<?, ?>, ActsForProof<?, ?>> entry : usableDelegations(
        query, searchState).entrySet()) {
      final Delegation<?, ?> delegation = entry.getKey();
      final ActsForProof<?, ?> usabilityProof = entry.getValue();
      if (PrincipalUtil.equals(query.superior, delegation.superior)
          && PrincipalUtil.equals(query.inferior, delegation.inferior)) {
        return new ProofSearchResult<>(
            (ActsForProof<Superior, Inferior>) new DelegatesProof<>(delegation,
                query, usabilityProof));
      }
    }

    // If this is a forwarded query, just skip to the next part that uses
    // delegations directly. The caller should have done the rest already.
    if (!forwarded) {
      // Attempt to use the rule:
      //   a ≽ c or b ≽ c => a ∧ b ≽ c
      if (query.superior instanceof ConjunctivePrincipal) {
        final ConjunctivePrincipal superior =
            (ConjunctivePrincipal) query.superior;
        for (Principal witness : superior.conjuncts()) {
          ActsForProof<Principal, Inferior> proof =
              actsForProof(this, query.superior(witness), searchState);
          if (proof != null) {
            // Have a proof of witness ≽ query.inferior.
            // Construct a proof for query.superior ≽ witness.
            DelegatesProof<Superior, Principal> step =
                new DelegatesProof<>(query.inferior(witness));
            return new ProofSearchResult<>(new TransitiveProof<>(step, witness,
                proof));
          }
        }
      }

      // Attempt to use the rule:
      //   a ≽ c and b ≽ c => a ∨ b ≽ c
      if (query.superior instanceof DisjunctivePrincipal) {
        final DisjunctivePrincipal superior =
            (DisjunctivePrincipal) query.superior;
        Map<Principal, ActsForProof<Principal, Inferior>> proofs =
            new HashMap<>(superior.disjuncts().size());
        boolean success = true;
        for (Principal p : superior.disjuncts()) {
          ActsForProof<Principal, Inferior> proof =
              actsForProof(this, query.superior(p), searchState);
          if (proof == null) {
            success = false;
            break;
          }
          proofs.put(p, proof);
        }

        if (success) {
          return new ProofSearchResult<>(
              (ActsForProof<Superior, Inferior>) new FromDisjunctProof<>(
                  superior, query.inferior, proofs));
        }
      }

      // Attempt to use the rule:
      //   a ≽ b and a ≽ c => a ≽ b ∧ c
      if (query.inferior instanceof ConjunctivePrincipal) {
        final ConjunctivePrincipal inferior =
            (ConjunctivePrincipal) query.inferior;
        Map<Principal, ActsForProof<Superior, Principal>> proofs =
            new HashMap<>(inferior.conjuncts.size());
        boolean success = true;
        for (Principal p : inferior.conjuncts()) {
          ActsForProof<Superior, Principal> proof =
              actsForProof(this, query.inferior(p), searchState);
          if (proof == null) {
            success = false;
            break;
          }
          proofs.put(p, proof);
        }

        if (success) {
          return new ProofSearchResult<>(
              (ActsForProof<Superior, Inferior>) new ToConjunctProof<>(
                  query.superior, inferior, proofs));
        }
      }

      // Attempt to use the rule:
      //   a ≽ b or a ≽ c => a ≽ b ∨ c
      if (query.inferior instanceof DisjunctivePrincipal) {
        DisjunctivePrincipal inferior = (DisjunctivePrincipal) query.inferior;
        for (Principal witness : inferior.disjuncts()) {
          ActsForProof<Superior, Principal> proof =
              actsForProof(this, query.inferior(witness), searchState);
          if (proof != null) {
            // Have a proof of query.superior ≽ witness.
            // Construct a proof for witness ≽ query.inferior.
            DelegatesProof<Principal, Inferior> step =
                new DelegatesProof<>(query.superior(witness));
            return new ProofSearchResult<>(new TransitiveProof<>(proof,
                witness, step));
          }
        }
      }

      if (query.inferior instanceof ConfPrincipal) {
        ConfPrincipal inferior = (ConfPrincipal) query.inferior;
        {
          // Attempt to use the rule:
          //   a ≽ b => a ≽ b→
          ActsForProof<Superior, Principal> proof =
              actsForProof(this, query.inferior(inferior.base()), searchState);
          if (proof != null) {
            // Have a proof of query.superior ≽ inferior.base().
            // Construct a proof for inferior.base() ≽ query.inferior.
            DelegatesProof<Principal, Inferior> step =
                new DelegatesProof<>(query.superior(inferior.base()));
            return new ProofSearchResult<>(new TransitiveProof<>(proof,
                inferior.base(), step));
          }
        }

        if (query.superior instanceof ConfPrincipal) {
          ConfPrincipal superior = (ConfPrincipal) query.superior;

          // Attempt to use the rule:
          //   a ≽ b => a→ ≽ b→
          ActsForProof<Principal, Principal> proof =
              actsForProof(this,
                  query.superior(superior.base()).inferior(inferior.base()),
                  searchState);
          if (proof != null) {
            // Have a proof of superior.base() ≽ inferior.base().
            return new ProofSearchResult<>(
                (ActsForProof<Superior, Inferior>) new ConfProjectionProof(
                    proof));
          }
        }
      }

      if (query.inferior instanceof IntegPrincipal) {
        IntegPrincipal inferior = (IntegPrincipal) query.inferior;
        {
          // Attempt to use the rule:
          //   a ≽ b => a ≽ b←
          ActsForProof<Superior, Principal> proof =
              actsForProof(this, query.inferior(inferior.base()), searchState);
          if (proof != null) {
            // Have a proof of query.superior ≽ inferior.base().
            // Construct a proof for inferior.base() ≽query.inferior.
            DelegatesProof<Principal, Inferior> step =
                new DelegatesProof<>(query.superior(inferior.base()));
            return new ProofSearchResult<>(new TransitiveProof<>(proof,
                inferior.base(), step));
          }
        }

        if (query.superior instanceof IntegPrincipal) {
          IntegPrincipal superior = (IntegPrincipal) query.superior;

          // Attempt to use the rule:
          //   a ≽ b => a← ≽ b←
          ActsForProof<Principal, Principal> proof =
              actsForProof(this,
                  query.superior(superior.base()).inferior(inferior.base()),
                  searchState);
          if (proof != null) {
            // Have a proof of superior.base() ≽ inferior.base().
            return new ProofSearchResult<>(
                (ActsForProof<Superior, Inferior>) new IntegProjectionProof(
                    proof));
          }
        }
      }

      // Attempt to use the rule:
      //   a ∨ b ≽ c => a:b ≽ c
      if (query.superior instanceof OwnedPrincipal) {
        OwnedPrincipal superior = (OwnedPrincipal) query.superior;
        Principal a = superior.owner();
        Principal b = superior.projection();
        ActsForProof<Principal, Inferior> proof =
            actsForProof(this, query.superior(PrincipalUtil.disjunction(a, b)),
                searchState);
        if (proof != null) {
          return new ProofSearchResult<>(
              (ActsForProof<Superior, Inferior>) new MeetToOwnerProof<>(
                  superior, proof));
        }

        // Attempt to use the rule:
        //   a ≽ c and a∨b ≽ c∨d => a:b ≽ c:d
        if (query.inferior instanceof OwnedPrincipal) {
          OwnedPrincipal inferior = (OwnedPrincipal) query.inferior;
          Principal c = inferior.owner();
          Principal d = inferior.projection();
          ActsForProof<Principal, Principal> ownersProof =
              actsForProof(this, query.superior(a).inferior(c), searchState);
          if (ownersProof != null) {
            ActsForProof<Principal, Principal> projectionProof =
                actsForProof(
                    this,
                    query.superior(PrincipalUtil.disjunction(a, b)).inferior(
                        PrincipalUtil.disjunction(c, d)), searchState);
            if (projectionProof != null) {
              return new ProofSearchResult<>(
                  (ActsForProof<Superior, Inferior>) new OwnedPrincipalsProof(
                      superior, inferior, ownersProof, projectionProof));
            }
          }
        }
      }

      // Attempt to use the rule:
      //   a ≽ b => a ≽ b:c
      if (query.inferior instanceof OwnedPrincipal) {
        OwnedPrincipal inferior = (OwnedPrincipal) query.inferior;
        Principal b = inferior.owner();
        ActsForProof<Superior, Principal> proof =
            actsForProof(this, query.inferior(b), searchState);
        if (proof != null) {
          // Have a proof of query.superior ≽ inferior.owner().
          // Construct a proof for inferior.owner() ≽ query.inferior.
          DelegatesProof<Principal, Inferior> step =
              new DelegatesProof<>(query.superior(inferior.owner()));
          return new ProofSearchResult<>(new TransitiveProof<>(proof,
              inferior.owner(), step));
        }
      }
    }

    // Attempt to use transitivity.
    // For each usable delegation p ≽ q, try to show either:
    //   query.superior = p and q ≽ query.inferior
    // or:
    //   query.superior ≽ p and q = query.inferior.
    for (Map.Entry<Delegation<?, ?>, ActsForProof<?, ?>> entry : usableDelegations(
        query, searchState).entrySet()) {
      final Delegation<?, ?> delegation = entry.getKey();
      final ActsForProof<?, ?> usabilityProof = entry.getValue();

      // Let p = delegation.superior and q = delegation.inferior.
      final Principal p = delegation.superior;
      final Principal q = delegation.inferior;

      if (PrincipalUtil.equals(query.superior, p)) {
        // Have query.superior = p. Show q ≽ query.inferior.
        ActsForProof<Principal, Inferior> step2 =
            actsForProof(this, query.superior(q), searchState);
        if (step2 != null) {
          // Proof successful.
          ActsForProof<Superior, Principal> step1 =
              new DelegatesProof<>(
                  (Delegation<Principal, Superior>) delegation, query,
                  usabilityProof);
          return new ProofSearchResult<>(new TransitiveProof<>(step1, q, step2));
        }
      }

      if (PrincipalUtil.equals(q, query.inferior)) {
        // Have q = query.inferior. Show query.superior ≽ p.
        ActsForProof<Superior, Principal> step1 =
            actsForProof(this, query.inferior(p), searchState);
        if (step1 != null) {
          // Proof successful.
          ActsForProof<Principal, Inferior> step2 =
              new DelegatesProof<>(
                  (Delegation<Inferior, Principal>) delegation, query,
                  usabilityProof);
          return new ProofSearchResult<>(new TransitiveProof<>(step1, p, step2));
        }
      }
    }

    // Forward query to other nodes. First, figure out who to ask.
    Set<PrimitivePrincipal> askable =
        new HashSet<>(askablePrincipals(query, searchState));
    askable.removeAll(searchState.principalsAsked);

    // Add the set of askable nodes to the existing search state.
    searchState = new ProofSearchState(searchState, askable);

    // Ask the other nodes.
    for (Principal callee : askable) {
      ActsForProof<Superior, Inferior> result =
          callee.actsForProof(this, query, searchState);
      if (result != null) return new ProofSearchResult<>(result);
    }

    // Proof failed.
    if (searchState.searchPruned.get()) {
      return ProofSearchResult.pruned();
    } else {
      return ProofSearchResult.failed();
    }
  }

  /**
   * A request to revoke all reachable delegations of {@code inferior}'s
   * authority to {@code superior}, where {@code label} flows to the
   * delegation's label.
   */
  private final class RevocationRequest {
    /**
     * The principal whose delegations are to be revoked.
     */
    final Principal inferior;

    /**
     * The principal whose privileges are to be revoked.
     */
    final Principal superior;

    /**
     * The label on the request.
     */
    final Principal label;

    RevocationRequest(Principal inferior, Principal superior, Principal label) {
      this.inferior = inferior;
      this.superior = superior;
      this.label = label;
    }

    /**
     * Joins the confidentiality projection of the given principal into the
     * {@code label} of this request and returns the result. This ensures that
     * the given principal's confidentiality is protected by the resulting
     * request.
     */
    RevocationRequest joinLabel(Principal p) {
      Principal newLabel = PrincipalUtil.join(label, p.confidentiality());
      if (label == newLabel) return this;
      return new RevocationRequest(inferior, superior, newLabel);
    }

    /**
     * Meets the given principal's integrity into the {@code label} and returns
     * the resulting {@code RevocationRequest}. This ensures that the given
     * principal is trusted with the integrity of the request.
     */
    RevocationRequest meetIntegrity(Principal p) {
      // newLabel = label ∨ (⊤→ ∧ p←).
      Principal newLabel =
          PrincipalUtil.meet(
              label,
              PrincipalUtil.join(PrincipalUtil.top().confidentiality(),
                  p.integrity()));
      if (PrincipalUtil.equals(label, newLabel)) return this;
      return new RevocationRequest(inferior, superior, newLabel);
    }

    Set<PrimitivePrincipal> componentPrimitivePrincipals() {
      Set<PrimitivePrincipal> result = new HashSet<>();
      result.addAll(inferior.componentPrimitivePrincipals());
      result.addAll(superior.componentPrimitivePrincipals());
      result.addAll(label.componentPrimitivePrincipals());
      return result;
    }
  }

  private static final class ProofSearchResult<Superior extends Principal, Inferior extends Principal> {
    enum Type {
      SUCCESS, PRUNED, FAILED
    }

    private static final ProofSearchResult<Principal, Principal> PRUNED =
        new ProofSearchResult<>(Type.PRUNED);

    private static final ProofSearchResult<Principal, Principal> FAILED =
        new ProofSearchResult<>(Type.FAILED);

    final Type type;
    final ActsForProof<Superior, Inferior> proof;

    private ProofSearchResult(Type type) {
      this.type = type;
      this.proof = null;
    }

    /**
     * Constructs a ProofSearchResult indicating that a proof was found.
     */
    ProofSearchResult(ActsForProof<Superior, Inferior> proof) {
      this.type = Type.SUCCESS;
      this.proof = proof;
    }

    @Override
    public String toString() {
      return type.toString();
    }

    /**
     * @return a ProofSearchResult indicating that no proof was found, but the
     *          search was pruned due to cycles with ancestor goals
     */
    static <Superior extends Principal, Inferior extends Principal> ProofSearchResult<Superior, Inferior> pruned() {
      return (ProofSearchResult<Superior, Inferior>) PRUNED;
    }

    /**
     * @return a ProofSearchResult indicating that no proof was found, despite
     *          an exhaustive search
     */
    static <Superior extends Principal, Inferior extends Principal> ProofSearchResult<Superior, Inferior> failed() {
      return (ProofSearchResult<Superior, Inferior>) FAILED;
    }
  }

  final class ProofSearchState {
    /**
     * The most recent goal on the stack.
     */
    private final ActsForQuery<?, ?> curGoal;

    /**
     * The search state for the parent goal.
     */
    private final ProofSearchState parent;

    /**
     * All goals currently on the stack.
     */
    private final Set<ActsForQuery<?, ?>> allGoals;

    /**
     * The set of principals who have participated in the search so far.
     */
    private final Set<PrimitivePrincipal> allParticipants;

    /**
     * The set of principals who have already been asked about the most recent
     * goal on the stack.
     */
    private final Set<Principal> principalsAsked;

    /**
     * Whether the proof search for the current goal has been pruned. A search
     * is pruned when an ancestor goal becomes a subgoal. For example, suppose
     * the proof of A has the proof of B as a subgoal, which in turn depends on
     * the proof of C. If the proof search for C finds A as a subgoal, then the
     * search for B and C (but not A) are considered pruned.
     */
    private final AtomicBoolean searchPruned;

    /**
     * The acts-for cache. Maps pairs of principals and queries to the
     * principal's proof of the query's truth.
     */
    private final Map<Pair<Principal, ActsForQuery<Principal, Principal>>, ActsForProof<Principal, Principal>> actsForCache;

    /**
     * The not-acts-for cache. A set of pairs of principals and queries, where
     * the principal has previously returned a negative answer to the query.
     */
    private final Set<Pair<Principal, ActsForQuery<Principal, Principal>>> notActsForCache;

    public ProofSearchState() {
      this.curGoal = null;
      this.parent = null;
      this.searchPruned = new AtomicBoolean(false);

      allGoals = Collections.emptySet();
      if (Principal.this instanceof PrimitivePrincipal) {
        allParticipants =
            Collections.singleton((PrimitivePrincipal) Principal.this);
      } else {
        allParticipants = Collections.emptySet();
      }
      principalsAsked = Collections.emptySet();

      this.actsForCache = new HashMap<>();
      this.notActsForCache = new HashSet<>();
    }

    /**
     * Constructs a new search state with the given query pushed onto the query
     * stack, and with {@link ProofSearchState#principalsAsked} containing just
     * {@link Principal}{@code .this}. If the given query already exists as a
     * goal, then every goal on the stack up to (but not including) the query
     * will be marked as being pruned, and a {@link CircularProofException} is
     * thrown.
     */
    private ProofSearchState(ProofSearchState state, ActsForQuery<?, ?> query)
        throws CircularProofException {
      // Check for circularity.
      if (state.allGoals.contains(query)) {
        // Circular proof. Mark pruned searches.
        for (ProofSearchState curState = state; !query.equals(curState.curGoal); curState =
            curState.parent) {
          curState.searchPruned.set(true);
        }

        throw new CircularProofException();
      }

      this.curGoal = query;
      this.parent = state;
      this.searchPruned = new AtomicBoolean(false);

      Set<ActsForQuery<?, ?>> allGoals =
          new HashSet<>(state.allGoals.size() + 1);
      this.allGoals = Collections.unmodifiableSet(allGoals);
      allGoals.addAll(state.allGoals);
      allGoals.add(query);

      this.allParticipants = state.allParticipants;

      this.principalsAsked = Collections.singleton(Principal.this);

      this.actsForCache = state.actsForCache;
      this.notActsForCache = state.notActsForCache;
    }

    /**
     * Constructs a new search state with the given set of principals added to
     * the set of principals asked.
     */
    private ProofSearchState(ProofSearchState state,
        Set<PrimitivePrincipal> newPrincipalsAsked) {
      this.curGoal = state.curGoal;
      this.parent = state.parent;
      this.allGoals = state.allGoals;
      this.searchPruned = state.searchPruned;

      Set<PrimitivePrincipal> allParticipants =
          new HashSet<>(state.allParticipants.size()
              + newPrincipalsAsked.size());
      this.allParticipants = Collections.unmodifiableSet(allParticipants);
      allParticipants.addAll(state.allParticipants);
      allParticipants.addAll(newPrincipalsAsked);

      Set<Principal> principalsAsked =
          new HashSet<>(state.principalsAsked.size()
              + newPrincipalsAsked.size());
      this.principalsAsked = Collections.unmodifiableSet(principalsAsked);

      principalsAsked.addAll(state.principalsAsked);
      principalsAsked.addAll(newPrincipalsAsked);

      this.actsForCache = state.actsForCache;
      this.notActsForCache = state.notActsForCache;
    }

    private <Superior extends Principal, Inferior extends Principal> void cacheActsFor(
        ActsForQuery<Superior, Inferior> query,
        ActsForProof<Superior, Inferior> proof) {
      actsForCache.put(new Pair<>(Principal.this,
          (ActsForQuery<Principal, Principal>) query),
          (ActsForProof<Principal, Principal>) proof);
    }

    private <Superior extends Principal, Inferior extends Principal> void cacheNotActsFor(
        ActsForQuery<Superior, Inferior> query) {
      notActsForCache.add(new Pair<>(Principal.this,
          (ActsForQuery<Principal, Principal>) query));
    }

    /**
     * @return (true, proof) for cached positive answers, (false, null) for
     *        cached negative answers, and null for a cache miss
     */
    private <Superior extends Principal, Inferior extends Principal> Pair<Boolean, ActsForProof<Superior, Inferior>> cacheLookup(
        ActsForQuery<Superior, Inferior> query) {
      Pair<Principal, ActsForQuery<Principal, Principal>> key =
          new Pair<>(Principal.this, (ActsForQuery<Principal, Principal>) query);
      ActsForProof<Principal, Principal> proof = actsForCache.get(key);
      if (proof != null) {
        return new Pair<>(true, (ActsForProof<Superior, Inferior>) proof);
      }

      if (notActsForCache.contains(key)) return new Pair<>(false, null);

      return null;
    }

    public boolean contains(ActsForQuery<?, ?> query) {
      return allGoals.contains(query);
    }

    @Override
    public String toString() {
      StringBuffer result = new StringBuffer();
      result.append("goals = [\n");
      for (ProofSearchState curState = this; curState != null; curState =
          curState.parent) {
        result.insert(10,
            "  " + curState.curGoal
                + (curState.searchPruned.get() ? " [pruned]" : "") + ",\n");
      }
      result.append("]\n");

      result.append("allParticipants = " + allParticipants + "\n");

      result.append("prinicpalsAsked = " + principalsAsked);

      return result.toString();
    }
  }

  /**
   * Indicates a circular proof tree was detected.
   */
  private static class CircularProofException extends Exception {
  }
}
