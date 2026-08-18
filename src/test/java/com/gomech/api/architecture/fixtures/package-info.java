/**
 * Deliberately violating fixtures for {@code ModuleArchitectureRuleFixturesTest}.
 *
 * <p>Every class in this tree breaks exactly one module boundary rule on purpose, so each rule in
 * {@code ModuleArchitectureRules} can be proven to still detect the violation it was written for.
 * A rule that silently stops matching anything, for example after a package rename, would otherwise
 * keep passing against production code forever.
 *
 * <p>These fixtures are test sources and are excluded from the production check by
 * {@code ImportOption.Predefined.DO_NOT_INCLUDE_TESTS}. They carry no Spring stereotype annotations
 * so component scanning never picks them up.
 */
package com.gomech.api.architecture.fixtures;
