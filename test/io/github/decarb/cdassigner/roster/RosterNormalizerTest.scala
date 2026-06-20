package io.github.decarb.cdassigner.roster

import io.circe.parser
import io.github.decarb.cdassigner.domain.{PlayerClass, Spec}
import io.github.decarb.cdassigner.raidhelper.RawSlot
import java.nio.file.{Files, Paths}

class RosterNormalizerTest extends munit.FunSuite:

  test("normalizes the Raid-Helper class/spec quirks correctly") {
    val cases = List(
      ("Tank", "Protection1")    -> (PlayerClass.Warrior, Spec.Protection),
      ("Tank", "Guardian")       -> (PlayerClass.Druid, Spec.Guardian),
      ("Tank", "Blood")          -> (PlayerClass.DeathKnight, Spec.Blood),
      ("DK", "Unholy")           -> (PlayerClass.DeathKnight, Spec.Unholy),
      ("Paladin", "Holy1")       -> (PlayerClass.Paladin, Spec.Holy),
      ("Shaman", "Restoration1") -> (PlayerClass.Shaman, Spec.Restoration),
      ("Druid", "Restoration")   -> (PlayerClass.Druid, Spec.Restoration),
      ("Priest", "Discipline")   -> (PlayerClass.Priest, Spec.Discipline),
      ("Warrior", "Fury")        -> (PlayerClass.Warrior, Spec.Fury)
    )
    cases.foreach { case ((cn, sn), expected) =>
      assertEquals(RosterNormalizer.normalizeSlot(cn, sn), Right(expected), s"$cn / $sn")
    }
  }

  test("rejects an illegal class/spec pairing with a descriptive error") {
    assert(RosterNormalizer.normalizeSlot("Mage", "Holy").isLeft)
  }

  test("normalizes the whole committed raidplan sample with no errors") {
    val body  = Files.readString(Paths.get("research/raidhelper-raidplan-sample.json"))
    val slots = parser.parse(body).flatMap(_.hcursor.get[List[RawSlot]]("slots")).toOption.get
    assert(slots.nonEmpty)
    RosterNormalizer.normalize(slots) match
      case Right(members) => assertEquals(members.size, slots.size)
      case Left(errors)   => fail(s"unexpected normalization errors:\n${errors.mkString("\n")}")
  }
