package io.github.decarb.cdassigner.supply

import io.github.decarb.cdassigner.domain.CooldownSource.{AnySpec, Specs}
import io.github.decarb.cdassigner.domain.CoverageType.*
import io.github.decarb.cdassigner.domain.PlayerClass.*
import io.github.decarb.cdassigner.domain.Spec.*
import io.github.decarb.cdassigner.domain.{Cooldown, RaidMember}

/** The static MoP (5.4.x) raid-cooldown knowledge base, transcribed from `research/cooldown-kb.md`.
  * This is the SUPPLY side of the engine: every raid cooldown a comp can bring, keyed by who
  * provides it.
  *
  * Recharge/duration values are being validated against 5.4.x game data — see the verification
  * table in `research/cooldown-kb.md` for per-ability status and sources. Magnitudes are
  * descriptive for now; logs will later calibrate them to numbers.
  */
object CooldownKb:

  val all: List[Cooldown] = List(
    // --- aoe_heal ---
    Cooldown(
      "tranquility",
      "Tranquility",
      Specs(Druid, Set(Restoration)),
      180,
      8,
      AoeHeal,
      "strong raid heal + HoT"
    ),
    Cooldown(
      "tranquility_offspec",
      "Tranquility (off-spec)",
      Specs(Druid, Set(Balance, Feral, Guardian)),
      480,
      8,
      AoeHeal,
      "weak"
    ),
    Cooldown("revival", "Revival", Specs(Monk, Set(Mistweaver)), 180, 0, AoeHeal, "burst + dispel"),
    Cooldown(
      "healing_tide_totem",
      "Healing Tide Totem",
      Specs(Shaman, Set(Restoration)),
      180,
      11,
      AoeHeal,
      "pulsed heal"
    ),
    Cooldown(
      "ascendance",
      "Ascendance",
      Specs(Shaman, Set(Restoration)),
      180,
      15,
      AoeHeal,
      "copies healing"
    ),
    Cooldown(
      "ancestral_guidance",
      "Ancestral Guidance",
      Specs(Shaman, Set(Elemental, Enhancement)),
      120,
      10,
      AoeHeal,
      "dmg->heal"
    ),
    Cooldown(
      "divine_hymn",
      "Divine Hymn",
      Specs(Priest, Set(Holy)),
      180,
      8,
      AoeHeal,
      "heal + 10% healing taken"
    ),
    Cooldown(
      "spirit_shell",
      "Spirit Shell",
      Specs(Priest, Set(Discipline)),
      60,
      10,
      AoeHeal,
      "absorb conversion"
    ),
    Cooldown(
      "lights_hammer",
      "Light's Hammer",
      Specs(Paladin, Set(Holy)),
      60,
      14,
      AoeHeal,
      "ground AoE"
    ),
    Cooldown(
      "vampiric_embrace",
      "Vampiric Embrace",
      Specs(Priest, Set(Shadow)),
      180,
      15,
      AoeHeal,
      "dmg->heal"
    ),

    // --- raid-wide damage reduction ---
    Cooldown(
      "power_word_barrier",
      "Power Word: Barrier",
      Specs(Priest, Set(Discipline)),
      180,
      10,
      DrAll,
      "-25% in area"
    ),
    // Holy's Devotion Aura reduces ALL damage; Ret/Prot get the same aura but magic-only.
    Cooldown(
      "devotion_aura",
      "Devotion Aura",
      Specs(Paladin, Set(Holy)),
      180,
      6,
      DrAll,
      "-20% all dmg, 40yd"
    ),
    Cooldown(
      "devotion_aura_magic",
      "Devotion Aura (Ret/Prot)",
      Specs(Paladin, Set(Protection, Retribution)),
      180,
      6,
      DrMagic,
      "-20% magic only, 40yd"
    ),
    Cooldown(
      "spirit_link_totem",
      "Spirit Link Totem",
      Specs(Shaman, Set(Restoration)),
      180,
      6,
      DrAll,
      "-10% + HP redistribute"
    ),
    Cooldown(
      "anti_magic_zone",
      "Anti-Magic Zone",
      AnySpec(DeathKnight),
      120,
      3,
      DrMagic,
      "-40% magic"
    ),
    Cooldown(
      "smoke_bomb",
      "Smoke Bomb",
      AnySpec(Rogue),
      180,
      5,
      DrAll,
      "targeting block, tiny radius"
    ),
    Cooldown(
      "demoralizing_banner",
      "Demoralizing Banner",
      AnySpec(Warrior),
      180,
      15,
      DrAll,
      "-10% enemy dmg dealt"
    ),

    // --- effective_health ---
    Cooldown(
      "rallying_cry",
      "Rallying Cry",
      AnySpec(Warrior),
      180,
      10,
      EffectiveHealth,
      "+20% max HP"
    ),

    // --- external (single-target on another player) ---
    Cooldown(
      "pain_suppression",
      "Pain Suppression",
      Specs(Priest, Set(Discipline)),
      180,
      8,
      External,
      "-40% dmg taken"
    ),
    Cooldown(
      "guardian_spirit",
      "Guardian Spirit",
      Specs(Priest, Set(Holy)),
      180,
      10,
      External,
      "+60% healing / death save"
    ),
    Cooldown(
      "ironbark",
      "Ironbark",
      Specs(Druid, Set(Restoration)),
      60,
      12,
      External,
      "-20% dmg taken"
    ),
    Cooldown(
      "hand_of_sacrifice",
      "Hand of Sacrifice",
      AnySpec(Paladin),
      120,
      12,
      External,
      "redirect 30% dmg"
    ),
    Cooldown(
      "hand_of_protection",
      "Hand of Protection",
      AnySpec(Paladin),
      300,
      10,
      External,
      "physical immunity"
    ),
    Cooldown(
      "life_cocoon",
      "Life Cocoon",
      Specs(Monk, Set(Mistweaver)),
      120,
      12,
      External,
      "absorb + 50% healing"
    ),
    Cooldown(
      "zen_meditation",
      "Zen Meditation",
      Specs(Monk, Set(Mistweaver)),
      180,
      8,
      External,
      "-90% all dmg to self + redirect"
    )
  )

  /** Every cooldown the given member's `(class, spec)` can cast. */
  def forMember(member: RaidMember): List[Cooldown] =
    all.filter(_.source.provides(member.playerClass, member.spec))
