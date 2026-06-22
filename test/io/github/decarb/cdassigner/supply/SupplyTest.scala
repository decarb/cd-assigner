package io.github.decarb.cdassigner.supply

import io.github.decarb.cdassigner.domain.*

class SupplyTest extends munit.FunSuite:

  private def member(cls: PlayerClass, spec: Spec): RaidMember =
    RaidMember(PlayerName(s"${cls.code}-${spec.code}"), cls, spec, confirmed = true, group = 1)

  test("Tranquility recharge is spec-dependent: 180s Resto vs 480s off-spec") {
    val resto   = CooldownKb.forMember(member(PlayerClass.Druid, Spec.Restoration))
    val balance = CooldownKb.forMember(member(PlayerClass.Druid, Spec.Balance))

    val restoTranq = resto.filter(_.coverage == CoverageType.AoeHeal)
    val balTranq   = balance.filter(_.coverage == CoverageType.AoeHeal)

    assertEquals(restoTranq.map(_.rechargeSeconds), List(180))
    assertEquals(balTranq.map(_.rechargeSeconds), List(480))
  }

  test("Devotion Aura coverage is spec-dependent: dr_all for Holy, dr_magic for Ret/Prot") {
    def auraCoverage(spec: Spec): List[CoverageType] =
      CooldownKb
        .forMember(member(PlayerClass.Paladin, spec))
        .filter(_.id.startsWith("devotion_aura"))
        .map(_.coverage)

    assertEquals(auraCoverage(Spec.Holy), List(CoverageType.DrAll))
    assertEquals(auraCoverage(Spec.Retribution), List(CoverageType.DrMagic))
    assertEquals(auraCoverage(Spec.Protection), List(CoverageType.DrMagic))
  }

  test("class-wide cooldowns resolve for any spec of the class") {
    Spec.values.filter(PlayerClass.DeathKnight.specs.contains).foreach { spec =>
      val cds = CooldownKb.forMember(member(PlayerClass.DeathKnight, spec))
      assert(cds.exists(_.id == "anti_magic_zone"), s"AMZ missing for DK $spec")
    }
  }

  test("charges = floor(T / recharge) + 1") {
    val amz = CooldownKb.all.find(_.id == "anti_magic_zone").get // 120s recharge
    assertEquals(amz.charges(390), 4) // 390/120 = 3, +1
    assertEquals(amz.charges(120), 2)
    assertEquals(amz.charges(60), 1)
  }

  test("ledger aggregates charges per coverage type across the roster") {
    val roster = Roster(
      "test",
      "2026-06-20T00:00:00Z",
      List(
        member(
          PlayerClass.Priest,
          Spec.Discipline
        ), // Barrier (dr_all 180), Spirit Shell, Pain Supp
        member(PlayerClass.DeathKnight, Spec.Frost), // AMZ (dr_magic 120)
        member(PlayerClass.Paladin, Spec.Holy) // Devotion Aura (dr_all 180), + Hands externals
      )
    )
    val ledger = SupplyLedger.compute(roster, 390)
    val byType = ledger.chargesByType

    // dr_magic: AMZ only (390/120+1=4). Holy's Devotion Aura is dr_all, not dr_magic.
    assertEquals(byType(CoverageType.DrMagic), 4)
    // dr_all: Power Word: Barrier (3) + Holy Devotion Aura (3) = 6
    assertEquals(byType(CoverageType.DrAll), 6)
  }

  test("KillTime parses seconds and m:ss, rejects nonsense") {
    assertEquals(KillTime.parse("390"), Right(390))
    assertEquals(KillTime.parse("6:30"), Right(390))
    assertEquals(KillTime.parse("0:45"), Right(45))
    assert(KillTime.parse("6:75").isLeft)
    assert(KillTime.parse("abc").isLeft)
    assert(KillTime.parse("0").isLeft)
  }
