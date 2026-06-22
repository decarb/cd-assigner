package io.github.decarb.cdassigner.supply

import io.github.decarb.cdassigner.domain.{Cooldown, CoverageType, RaidMember, Roster}

/** One member's instance of a cooldown and how many times it fires over the fight. */
final case class MemberCharge(member: RaidMember, cooldown: Cooldown, charges: Int)

/** Layer 0 of the engine: the supply side. How many charges of each cooldown the comp brings for a
  * given kill time, before any placement. This is the "show your work" half of the supply/demand
  * ledger described in `research/engine-design.md` §2.
  */
final case class SupplyLedger(killTimeSeconds: Int, rosterSize: Int, entries: List[MemberCharge]):

  /** Total charges available per coverage type — the SUPPLY(type) figure the engine compares
    * against demand.
    */
  def chargesByType: Map[CoverageType, Int] =
    entries.groupMapReduce(_.cooldown.coverage)(_.charges)(_ + _)

  /** How many roster members bring at least one raid cooldown (pure DPS contribute none). */
  def contributors: Int = entries.map(_.member).distinct.size

object SupplyLedger:

  /** Deterministic, no logs: expand every member's cooldowns and count charges over `T`. */
  def compute(roster: Roster, killTimeSeconds: Int): SupplyLedger =
    val entries =
      for
        member   <- roster.members
        cooldown <- CooldownKb.forMember(member)
      yield MemberCharge(member, cooldown, cooldown.charges(killTimeSeconds))
    SupplyLedger(killTimeSeconds, roster.members.size, entries)
