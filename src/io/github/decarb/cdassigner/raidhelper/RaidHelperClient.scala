package io.github.decarb.cdassigner.raidhelper

import cats.effect.IO
import io.circe.parser
import sttp.client4.*

/** Fetches finalized rosters from the Raid-Helper API (no auth required). */
final case class RaidHelperClient(backend: Backend[IO]):

  /** GET the `raidplan` for an event id and decode its `slots`. */
  def fetchRaidPlan(id: String): IO[List[RawSlot]] =
    basicRequest
      .get(uri"https://raid-helper.dev/api/raidplan/$id")
      .response(asStringAlways)
      .send(backend)
      .flatMap { resp =>
        if resp.code.isSuccess then
          IO.fromEither(parser.parse(resp.body).flatMap(_.hcursor.get[List[RawSlot]]("slots")))
        else
          val reason = parser.parse(resp.body).flatMap(_.hcursor.get[String]("reason")).getOrElse(resp.body)
          IO.raiseError(
            new RuntimeException(
              s"Raid-Helper has no raidplan for id $id (HTTP ${resp.code}: $reason). " +
                "Check the id is a current event with a finalized comp."
            )
          )
      }
