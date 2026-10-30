package com.focus.prototype
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],manifest=org.robolectric.annotation.Config.NONE)
class BusinessRulesTest {
 @org.junit.Test fun businessFlows() {val checks=runBusinessChecks(org.robolectric.RuntimeEnvironment.getApplication());println("PASS ${checks.size} business flows\n"+checks.joinToString("\n"))}
}
