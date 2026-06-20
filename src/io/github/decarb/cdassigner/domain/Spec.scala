package io.github.decarb.cdassigner.domain

/** All specs across MoP classes. Names that collide across classes (Frost, Holy, Protection,
  * Restoration) share a single case; the owning [[PlayerClass]] disambiguates via
  * [[PlayerClass.specs]].
  */
enum Spec(val code: String):
  case Blood         extends Spec("blood")
  case Frost         extends Spec("frost")
  case Unholy        extends Spec("unholy")
  case Balance       extends Spec("balance")
  case Feral         extends Spec("feral")
  case Guardian      extends Spec("guardian")
  case Restoration   extends Spec("restoration")
  case BeastMastery  extends Spec("beastmastery")
  case Marksmanship  extends Spec("marksmanship")
  case Survival      extends Spec("survival")
  case Arcane        extends Spec("arcane")
  case Fire          extends Spec("fire")
  case Brewmaster    extends Spec("brewmaster")
  case Mistweaver    extends Spec("mistweaver")
  case Windwalker    extends Spec("windwalker")
  case Holy          extends Spec("holy")
  case Protection    extends Spec("protection")
  case Retribution   extends Spec("retribution")
  case Discipline    extends Spec("discipline")
  case Shadow        extends Spec("shadow")
  case Assassination extends Spec("assassination")
  case Combat        extends Spec("combat")
  case Subtlety      extends Spec("subtlety")
  case Elemental     extends Spec("elemental")
  case Enhancement   extends Spec("enhancement")
  case Affliction    extends Spec("affliction")
  case Demonology    extends Spec("demonology")
  case Destruction   extends Spec("destruction")
  case Arms          extends Spec("arms")
  case Fury          extends Spec("fury")

object Spec:
  private val byCode = values.map(s => s.code -> s).toMap

  /** Parse a canonical code, e.g. "restoration". Case-insensitive. */
  def fromCode(s: String): Option[Spec] = byCode.get(s.trim.toLowerCase)
