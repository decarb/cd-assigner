package io.github.decarb.cdassigner.supply

import io.github.decarb.cdassigner.domain.CoverageType

/** Renders a [[SupplyLedger]] as a human-readable text report — the first analytical output of the
  * tool: every raid CD the comp brings and how many times each fires for the given kill time.
  */
object SupplyReport:

  def render(ledger: SupplyLedger): String =
    val t  = ledger.killTimeSeconds
    val sb = new StringBuilder

    sb ++= s"Supply ledger — kill time ${fmtTime(t)} (${t}s), " +
      s"${ledger.rosterSize} members (${ledger.contributors} bring a raid CD)\n"

    sb ++= "\nBy coverage type (SUPPLY available):\n"
    val byType = ledger.chargesByType
    CoverageType.values.foreach { ct =>
      byType.get(ct).foreach { total =>
        sb ++= f"  ${ct.code}%-18s $total%3d charges\n"
      }
    }

    sb ++= "\nBy cooldown (every raid CD your comp brings):\n"
    ledger.entries
      .groupBy(_.cooldown)
      .toList
      .sortBy { case (cd, _) => (cd.coverage.ordinal, cd.name) }
      .foreach { case (cd, es) =>
        val total     = es.map(_.charges).sum
        val providers = es.map(_.member.name.value).sorted.mkString(", ")
        sb ++=
          f"  ${cd.name}%-22s ${cd.coverage.code}%-16s ${cd.rechargeSeconds}%3ds recharge  ×$total  [$providers]\n"
      }

    sb.result()

  private def fmtTime(seconds: Int): String =
    f"${seconds / 60}:${seconds % 60}%02d"
