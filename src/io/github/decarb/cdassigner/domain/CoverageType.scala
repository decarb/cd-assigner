package io.github.decarb.cdassigner.domain

/** The kinds of coverage a cooldown can provide, driving demand matching in the engine. A
  * `dr_magic` event can only be filled by `dr_magic`/`dr_all` abilities, etc. Enum order is the
  * canonical display order for the supply ledger.
  */
enum CoverageType(val code: String):
  case AoeHeal    extends CoverageType("aoe_heal")    // raid-wide healing throughput
  case DrMagic    extends CoverageType("dr_magic")    // raid-wide magic damage reduction
  case DrPhysical extends CoverageType("dr_physical") // raid-wide physical damage reduction
  case DrAll      extends CoverageType("dr_all")      // raid-wide damage reduction, any school
  case EffectiveHealth extends CoverageType("effective_health") // temporary max-HP (Rallying Cry)
  case External extends CoverageType("external") // single-target defensive on another player

object CoverageType:
  private val byCode = values.map(t => t.code -> t).toMap

  /** Parse a canonical code, e.g. "dr_magic". Case-insensitive. */
  def fromCode(s: String): Option[CoverageType] = byCode.get(s.trim.toLowerCase)
