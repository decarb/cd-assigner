package io.github.decarb.cdassigner.raidhelper

class RaidPlanRefTest extends munit.FunSuite:

  test("extracts the id from a bare id, a URL, and a URL with trailing slash / query") {
    assertEquals(RaidPlanRef.fromInput("1512930111320494124"), Right("1512930111320494124"))
    assertEquals(
      RaidPlanRef.fromInput("https://raid-helper.xyz/raidplan/1512930111320494124"),
      Right("1512930111320494124")
    )
    assertEquals(
      RaidPlanRef.fromInput("https://raid-helper.xyz/raidplan/1512930111320494124/?x=1"),
      Right("1512930111320494124")
    )
  }

  test("rejects non-numeric input") {
    assert(RaidPlanRef.fromInput("https://raid-helper.xyz/event/not-a-plan").isLeft)
  }
