package io.github.decarb.cdassigner.supply

/** Parses a kill-time argument into seconds. Accepts either a plain second count (`390`) or `m:ss`
  * (`6:30`), since raid leaders think in minutes.
  */
object KillTime:

  def parse(raw: String): Either[String, Int] =
    val s   = raw.trim
    val err = s"Invalid kill time '$raw' (expected seconds like 390 or m:ss like 6:30)"
    if s.contains(":") then
      s.split(":", -1) match
        case Array(m, sec) =>
          (m.toIntOption, sec.toIntOption) match
            case (Some(mm), Some(ss)) if mm >= 0 && ss >= 0 && ss < 60 && (mm > 0 || ss > 0) =>
              Right(mm * 60 + ss)
            case _ => Left(err)
        case _ => Left(err)
    else s.toIntOption.filter(_ > 0).toRight(err)
