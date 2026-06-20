package io.github.decarb.cdassigner.roster

import cats.effect.IO
import io.circe.parser
import io.circe.syntax.*
import io.github.decarb.cdassigner.domain.Roster
import java.nio.file.{Files, Path}

/** Reads and writes the canonical roster JSON to disk. */
object RosterStore:

  def write(path: Path, roster: Roster): IO[Unit] =
    IO.blocking(Files.writeString(path, roster.asJson.spaces2)).void

  def read(path: Path): IO[Roster] =
    IO.blocking(Files.readString(path))
      .flatMap(s => IO.fromEither(parser.decode[Roster](s)))
