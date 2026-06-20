package io.github.decarb.cdassigner.domain

/** The 11 playable classes in Mists of Pandaria, with their canonical lowercase codes. */
enum PlayerClass(val code: String):
  case DeathKnight extends PlayerClass("deathknight")
  case Druid       extends PlayerClass("druid")
  case Hunter      extends PlayerClass("hunter")
  case Mage        extends PlayerClass("mage")
  case Monk        extends PlayerClass("monk")
  case Paladin     extends PlayerClass("paladin")
  case Priest      extends PlayerClass("priest")
  case Rogue       extends PlayerClass("rogue")
  case Shaman      extends PlayerClass("shaman")
  case Warlock     extends PlayerClass("warlock")
  case Warrior     extends PlayerClass("warrior")

  /** The specs this class can be, used to validate a normalized (class, spec) pairing. */
  def specs: Set[Spec] = this match
    case DeathKnight => Set(Spec.Blood, Spec.Frost, Spec.Unholy)
    case Druid       => Set(Spec.Balance, Spec.Feral, Spec.Guardian, Spec.Restoration)
    case Hunter      => Set(Spec.BeastMastery, Spec.Marksmanship, Spec.Survival)
    case Mage        => Set(Spec.Arcane, Spec.Fire, Spec.Frost)
    case Monk        => Set(Spec.Brewmaster, Spec.Mistweaver, Spec.Windwalker)
    case Paladin     => Set(Spec.Holy, Spec.Protection, Spec.Retribution)
    case Priest      => Set(Spec.Discipline, Spec.Holy, Spec.Shadow)
    case Rogue       => Set(Spec.Assassination, Spec.Combat, Spec.Subtlety)
    case Shaman      => Set(Spec.Elemental, Spec.Enhancement, Spec.Restoration)
    case Warlock     => Set(Spec.Affliction, Spec.Demonology, Spec.Destruction)
    case Warrior     => Set(Spec.Arms, Spec.Fury, Spec.Protection)

object PlayerClass:
  private val byCode = values.map(c => c.code -> c).toMap

  /** Parse a canonical code, e.g. "deathknight". Case-insensitive. */
  def fromCode(s: String): Option[PlayerClass] = byCode.get(s.trim.toLowerCase)
