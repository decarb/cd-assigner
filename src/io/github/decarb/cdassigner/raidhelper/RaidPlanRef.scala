package io.github.decarb.cdassigner.raidhelper

/** Accepts either a bare raidplan id or a full Raid-Helper URL and extracts the numeric id. */
object RaidPlanRef:

  def fromInput(input: String): Either[String, String] =
    val noQuery = input.trim.takeWhile(c => c != '?' && c != '#')
    val lastSeg = noQuery.split("/").filter(_.nonEmpty).lastOption.getOrElse("")
    if lastSeg.nonEmpty && lastSeg.forall(_.isDigit) then Right(lastSeg)
    else
      Left(
        s"Could not extract a raidplan id from '$input' (expected a numeric id or a raid-helper URL)"
      )
