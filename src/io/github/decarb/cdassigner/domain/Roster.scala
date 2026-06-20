package io.github.decarb.cdassigner.domain

import io.circe.{Decoder, Encoder, Json}

opaque type PlayerName = String
object PlayerName:
  def apply(value: String): PlayerName        = value
  extension (n: PlayerName) def value: String = n

/** One canonical roster entry after Raid-Helper normalization. */
final case class RaidMember(
  name: PlayerName,
  playerClass: PlayerClass,
  spec: Spec,
  confirmed: Boolean,
  group: Int
)

/** A serialized roster: our own stable format, decoupled from Raid-Helper's template quirks.
  * `raidPlanId` + `capturedAt` (ISO-8601) record where and when it was pulled.
  */
final case class Roster(raidPlanId: String, capturedAt: String, members: List[RaidMember])

object RaidMember:
  given Encoder[RaidMember] = Encoder.instance { m =>
    Json.obj(
      "name"      -> Json.fromString(m.name.value),
      "class"     -> Json.fromString(m.playerClass.code),
      "spec"      -> Json.fromString(m.spec.code),
      "confirmed" -> Json.fromBoolean(m.confirmed),
      "group"     -> Json.fromInt(m.group)
    )
  }

  given Decoder[RaidMember] = Decoder.instance { c =>
    for
      name     <- c.get[String]("name")
      classStr <- c.get[String]("class")
      cls      <- PlayerClass.fromCode(classStr).toRight(err(c, s"unknown class '$classStr'"))
      specStr  <- c.get[String]("spec")
      spec     <- Spec.fromCode(specStr).toRight(err(c, s"unknown spec '$specStr'"))
      conf     <- c.get[Boolean]("confirmed")
      group    <- c.get[Int]("group")
    yield RaidMember(PlayerName(name), cls, spec, conf, group)
  }

  private def err(c: io.circe.HCursor, msg: String): io.circe.DecodingFailure =
    io.circe.DecodingFailure(msg, c.history)

object Roster:
  given Encoder[Roster] = Encoder.instance { r =>
    Json.obj(
      "raidPlanId" -> Json.fromString(r.raidPlanId),
      "capturedAt" -> Json.fromString(r.capturedAt),
      "members"    -> Json.arr(r.members.map(summon[Encoder[RaidMember]].apply)*)
    )
  }

  given Decoder[Roster] = Decoder.instance { c =>
    for
      raidPlanId <- c.get[String]("raidPlanId")
      capturedAt <- c.get[String]("capturedAt")
      members    <- c.get[List[RaidMember]]("members")
    yield Roster(raidPlanId, capturedAt, members)
  }
