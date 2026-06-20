package io.github.decarb.cdassigner.roster

import io.github.decarb.cdassigner.domain.{PlayerClass, PlayerName, RaidMember, Spec}
import io.github.decarb.cdassigner.raidhelper.RawSlot

/** Maps template-specific Raid-Helper `(className, specName)` pairs onto canonical
  * `(PlayerClass, Spec)`. Unknown pairs are collected as errors so missing rows surface immediately.
  *
  * Two quirks drive the logic:
  *   - `className == "Tank"` collapses the real class into the spec (e.g. `Guardian` ⇒ Druid).
  *   - Raid-Helper appends a disambiguating digit to colliding spec names (`Holy1`, `Restoration1`,
  *     `Protection1`), which we strip before matching.
  */
object RosterNormalizer:

  /** Tank slots carry the real class in the spec; the trailing digit disambiguates Protection. */
  private val tankOverrides: Map[String, (PlayerClass, Spec)] = Map(
    "guardian"    -> (PlayerClass.Druid, Spec.Guardian),
    "blood"       -> (PlayerClass.DeathKnight, Spec.Blood),
    "brewmaster"  -> (PlayerClass.Monk, Spec.Brewmaster),
    "protection1" -> (PlayerClass.Warrior, Spec.Protection),
    "protection2" -> (PlayerClass.Paladin, Spec.Protection)
  )

  /** className aliases that aren't already a canonical class code. */
  private val classAliases: Map[String, PlayerClass] = Map(
    "dk" -> PlayerClass.DeathKnight
  )

  /** Spec-label aliases (post digit-strip) for Raid-Helper names that differ from canonical codes. */
  private val specAliases: Map[String, Spec] = Map(
    "smite"        -> Spec.Discipline, // VERIFY: Raid-Helper's Disc-priest label
    "beast mastery" -> Spec.BeastMastery
  )

  /** Resolve a single raw `(className, specName)` to canonical class/spec, or a descriptive error. */
  def normalizeSlot(className: String, specName: String): Either[String, (PlayerClass, Spec)] =
    val cn = className.trim.toLowerCase
    val sn = specName.trim.toLowerCase
    if cn == "tank" then
      tankOverrides.get(sn).toRight(s"Unknown tank spec: className='$className' specName='$specName'")
    else
      for
        cls  <- resolveClass(cn).toRight(s"Unknown class: className='$className'")
        spec <- resolveSpec(sn).toRight(s"Unknown spec: specName='$specName'")
        _ <- Either.cond(
          cls.specs.contains(spec),
          (),
          s"Spec '${spec.code}' is not valid for ${cls.code} (className='$className' specName='$specName')"
        )
      yield (cls, spec)

  private def resolveClass(cn: String): Option[PlayerClass] =
    classAliases.get(cn).orElse(PlayerClass.fromCode(cn))

  private def resolveSpec(sn: String): Option[Spec] =
    val base = stripTrailingDigits(sn)
    specAliases.get(base).orElse(Spec.fromCode(base))

  private def stripTrailingDigits(s: String): String =
    s.reverse.dropWhile(_.isDigit).reverse

  /** Normalize a whole raidplan, collecting every unresolved slot rather than failing on the first. */
  def normalize(slots: List[RawSlot]): Either[List[String], List[RaidMember]] =
    val results = slots.map { s =>
      normalizeSlot(s.className, s.specName).map { (cls, spec) =>
        RaidMember(PlayerName(s.name), cls, spec, s.confirmed, s.group)
      }
    }
    val errors = results.collect { case Left(e) => e }
    if errors.nonEmpty then Left(errors)
    else Right(results.collect { case Right(m) => m })
