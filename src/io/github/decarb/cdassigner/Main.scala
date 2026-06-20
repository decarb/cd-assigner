package io.github.decarb.cdassigner

import cats.effect.{ExitCode, IO}
import cats.effect.std.Console
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import io.github.decarb.cdassigner.domain.Roster
import io.github.decarb.cdassigner.raidhelper.{RaidHelperClient, RaidPlanRef}
import io.github.decarb.cdassigner.roster.{RosterNormalizer, RosterStore}
import java.nio.file.{Path, Paths}
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object Main
    extends CommandIOApp(
      name = "cd-assigner",
      header = "Pull a Raid-Helper roster into the canonical domain model"
    ):

  private val pull: Opts[IO[ExitCode]] =
    Opts.subcommand("pull", "Fetch a Raid-Helper roster (by URL or id) and serialize it to JSON") {
      (
        Opts.argument[String]("raidplan-url-or-id"),
        Opts
          .option[Path]("out", "Output roster JSON file (default: roster-<id>.json)", short = "o")
          .orNone
      ).mapN(runPull)
    }

  private def runPull(input: String, out: Option[Path]): IO[ExitCode] =
    RaidPlanRef.fromInput(input) match
      case Left(msg) => Console[IO].errorln(s"✗ $msg").as(ExitCode.Error)
      case Right(id) =>
        val program = HttpClientCatsBackend.resource[IO]().use { backend =>
          RaidHelperClient(backend).fetchRaidPlan(id).flatMap { slots =>
            RosterNormalizer.normalize(slots) match
              case Left(errors) =>
                Console[IO].errorln(s"✗ Could not normalize ${errors.size} of ${slots.size} slot(s):") *>
                  errors.traverse_(e => Console[IO].errorln(s"  - $e")) *>
                  Console[IO]
                    .errorln("Add the missing pairs to RosterNormalizer and retry.")
                    .as(ExitCode.Error)
              case Right(members) =>
                for
                  now <- IO.realTimeInstant
                  roster = Roster(id, now.toString, members)
                  path   = out.getOrElse(Paths.get(s"roster-$id.json"))
                  _ <- RosterStore.write(path, roster)
                  _ <- Console[IO].println(s"✓ Saved ${members.size} members to $path")
                  _ <- printRosterSummary(roster)
                yield ExitCode.Success
          }
        }
        program.handleErrorWith(e => Console[IO].errorln(s"✗ ${e.getMessage}").as(ExitCode.Error))

  private def printRosterSummary(roster: Roster): IO[Unit] =
    val byClass = roster.members
      .groupBy(_.playerClass)
      .toList
      .sortBy(-_._2.size)
      .map((cls, ms) => s"${cls.code} ×${ms.size}")
      .mkString(", ")
    Console[IO].println(s"  by class: $byClass")

  def main: Opts[IO[ExitCode]] = pull
