package io.github.decarb.cdassigner.domain

/** Who in the raid can cast a given cooldown. The knowledge base is keyed two ways: class-wide (any
  * spec of a class — e.g. Anti-Magic Zone for any Death Knight), or restricted to a specific set of
  * specs. The spec-set form also captures spec-dependent recharges by having two entries — e.g.
  * Resto Druid Tranquility (180s) vs off-spec Tranquility (480s) — whose spec sets are disjoint so
  * a given player matches exactly one.
  */
enum CooldownSource:
  case AnySpec(cls: PlayerClass)
  case Specs(cls: PlayerClass, specs: Set[Spec])

  def playerClass: PlayerClass = this match
    case AnySpec(c)  => c
    case Specs(c, _) => c

  /** Whether a member of this `(class, spec)` can cast the cooldown. */
  def provides(cls: PlayerClass, spec: Spec): Boolean = this match
    case AnySpec(c)      => c == cls
    case Specs(c, specs) => c == cls && specs.contains(spec)

/** A raid cooldown from the knowledge base (see `research/cooldown-kb.md`).
  *
  * @param rechargeSeconds
  *   time between consecutive uses (the defining scheduling constraint)
  * @param durationSeconds
  *   how long the effect lasts once cast
  * @param coverage
  *   which typed demand this can fill
  * @param magnitude
  *   short human description of strength (not yet numeric; logs will calibrate)
  */
final case class Cooldown(
  id: String,
  name: String,
  source: CooldownSource,
  rechargeSeconds: Int,
  durationSeconds: Int,
  coverage: CoverageType,
  magnitude: String
):
  /** Uses available across a fight of `killTimeSeconds`: floor(T / recharge) + 1 (the opener plus
    * one per full recharge window).
    */
  def charges(killTimeSeconds: Int): Int = killTimeSeconds / rechargeSeconds + 1
