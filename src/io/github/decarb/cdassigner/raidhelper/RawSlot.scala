package io.github.decarb.cdassigner.raidhelper

import io.circe.Decoder

/** A raw Raid-Helper `raidplan` slot, before normalization. `className`/`specName` are
  * template-specific (e.g. `className="Tank"`, `specName="Protection1"`) and must be normalized.
  */
final case class RawSlot(
  name: String,
  className: String,
  specName: String,
  confirmed: Boolean,
  group: Int
)

object RawSlot:
  given Decoder[RawSlot] = Decoder.instance { c =>
    for
      name      <- c.get[String]("name")
      className <- c.get[String]("className")
      specName  <- c.get[String]("specName")
      // Raid-Helper sends "confirmed" / "unconfirmed" as a string.
      confirmed <-
        c.get[Option[String]]("isConfirmed").map(_.exists(_.equalsIgnoreCase("confirmed")))
      group <- c.get[Option[Int]]("groupNumber").map(_.getOrElse(0))
    yield RawSlot(name, className, specName, confirmed, group)
  }
