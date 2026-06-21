package com.chrisjenx.yakcov

import io.github.classgraph.ClassGraph
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * JVM-only completeness guard for [RuleConventions]: classpath-scans the whole `com.chrisjenx.yakcov`
 * package tree (strings, generic, and combinators in the root package) and fails if any concrete
 * [ValueValidatorRule] is missing from the catalog. This is the half of the convention tooling that
 * reflection makes possible (the cross-platform behavioural contract lives in [RuleConventionsTest]).
 *
 * Because rule logic is common code, scanning the JVM compilation covers every platform. A new rule
 * forces a conscious choice: classify it under the convention, or add it to `exemptRuleNames` with
 * a reason. The build stays red until then.
 */
class RuleRegistryGuardTest {

    // Scan the whole library package tree, not just strings/generic, so combinators in the root
    // package (e.g. Optional) can't escape classification. The `getClassesImplementing` filter
    // selects only rules, so test classes sharing these packages are ignored.
    private val rulePackage = "com.chrisjenx.yakcov"

    // The only simple-name collision the simple-name-keyed catalog tolerates: strings.Required and
    // generic.Required (both reject emptiness). Any other collision is a blind spot — see below.
    private val allowedSimpleNameCollisions = setOf("Required")

    @Test
    fun everyConcreteRuleIsClassifiedInRuleConventions() {
        val discoveredClasses = ClassGraph()
            .enableClassInfo()
            .acceptPackages(rulePackage)
            .scan()
            .use { result ->
                result.getClassesImplementing(ValueValidatorRule::class.java.name)
                    .filter { info ->
                        !info.isInterface &&
                            !info.isAbstract &&
                            !info.isAnonymousInnerClass &&
                            !info.isSynthetic
                    }
                    .mapNotNull { it.name }
                    .toSet()
            }

        assertTrue(
            discoveredClasses.isNotEmpty(),
            "ClassGraph found no rules under $rulePackage — the scan or package name is wrong"
        )

        // [RuleConventions] keys rules by simple name (so the catalog stays cross-platform). That
        // collapses two distinct rules sharing a simple name into one entry, which would let the
        // second ship unclassified. Fail loudly on any unexpected collision so the catalog can be
        // switched to qualified names before that happens.
        val collisions = discoveredClasses
            .groupBy { it.substringAfterLast('.') }
            .filter { (simpleName, fqns) -> fqns.size > 1 && simpleName !in allowedSimpleNameCollisions }
        if (collisions.isNotEmpty()) {
            fail(
                "Rule simple-name collision(s) that RuleConventions cannot distinguish: " +
                    "${collisions.mapValues { it.value.sorted() }}. Key the catalog by qualified name " +
                    "or rename the rule."
            )
        }

        val discovered = discoveredClasses.map { it.substringAfterLast('.') }.toSet()
        val covered = RuleConventions.coveredRuleSimpleNames

        val unclassified = discovered - covered
        if (unclassified.isNotEmpty()) {
            fail(
                "ValueValidatorRule(s) not classified in RuleConventions: ${unclassified.sorted()}. " +
                    "Add each to emptyPasses*/emptyRejected* if it follows the blank/null-passes " +
                    "convention, or to exemptRuleNames with a documented reason."
            )
        }

        val stale = covered - discovered
        if (stale.isNotEmpty()) {
            fail(
                "RuleConventions lists rule name(s) that no longer exist on the classpath: " +
                    "${stale.sorted()}. Remove the stale entries (rule renamed or deleted?)."
            )
        }
    }
}
