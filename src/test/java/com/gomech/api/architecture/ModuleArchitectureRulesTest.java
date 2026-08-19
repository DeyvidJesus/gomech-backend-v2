package com.gomech.api.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the module boundary rules of ADR-001 and ADR-002 against production code.
 *
 * <p>This test runs as part of {@code mvn test}, so a forbidden dependency fails the build and
 * therefore fails CI. The rules themselves live in {@link ModuleArchitectureRules} and are also
 * exercised against deliberately violating fixtures by {@link ModuleArchitectureRuleFixturesTest}.
 */
class ModuleArchitectureRulesTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.gomech.api");

    /**
     * Guards every other test in this class. The rules below are only meaningful if the imported set
     * actually contains the production code they are meant to police, so this asserts the real IAM
     * layers and the real core slices are present and non-empty. If the import were ever
     * misconfigured, narrowed, or a layer emptied, this fails first and explains why.
     */
    @Test
    void rules_are_evaluated_against_real_production_classes() {
        assertProductionClassesExistIn("..modules.iam.api..");
        assertProductionClassesExistIn("..modules.iam.application..");
        assertProductionClassesExistIn("..modules.iam.domain..");
        assertProductionClassesExistIn("..modules.iam.infrastructure..");
        assertProductionClassesExistIn("..modules.billing.api..");
        assertProductionClassesExistIn("..modules.billing.application..");
        assertProductionClassesExistIn("..modules.billing.domain..");
        assertProductionClassesExistIn("..modules.billing.infrastructure..");
        assertProductionClassesExistIn("..core..");

        assertTrue(
            countAnnotatedWith("jakarta.persistence.Entity") >= 10,
            "expected the IAM and Billing JPA entities to be imported, found " + countAnnotatedWith("jakarta.persistence.Entity"));
        assertTrue(
            countAnnotatedWith("org.springframework.web.bind.annotation.RestController") >= 6,
            "expected the IAM and Billing controllers to be imported");
    }

    @Test
    void iam_must_not_depend_on_billing() {
        ModuleArchitectureRules.iamMustNotDependOnBilling().check(productionClasses);
    }

    @Test
    void modules_must_follow_the_four_layer_layout() {
        ModuleArchitectureRules.modulesMustFollowTheFourLayerLayout().check(productionClasses);
    }

    @Test
    void controllers_must_reside_in_the_api_layer() {
        ModuleArchitectureRules.controllersMustResideInTheApiLayer().check(productionClasses);
    }

    @Test
    void jpa_entities_must_reside_in_the_infrastructure_layer() {
        ModuleArchitectureRules.jpaEntitiesMustResideInTheInfrastructureLayer().check(productionClasses);
    }

    @Test
    void spring_data_repositories_must_reside_in_the_infrastructure_layer() {
        ModuleArchitectureRules.springDataRepositoriesMustResideInTheInfrastructureLayer().check(productionClasses);
    }

    @Test
    void modules_must_not_depend_on_another_modules_controllers() {
        ModuleArchitectureRules.modulesMustNotDependOnAnotherModulesControllers().check(productionClasses);
    }

    @Test
    void modules_must_not_depend_on_core_infrastructure() {
        ModuleArchitectureRules.modulesMustNotDependOnCoreInfrastructure().check(productionClasses);
    }

    @Test
    void domain_must_not_depend_on_outer_layers() {
        ModuleArchitectureRules.domainMustNotDependOnOuterLayers().check(productionClasses);
    }

    @Test
    void domain_must_not_depend_on_frameworks() {
        ModuleArchitectureRules.domainMustNotDependOnFrameworks().check(productionClasses);
    }

    @Test
    void application_must_not_depend_on_api_controllers() {
        ModuleArchitectureRules.applicationMustNotDependOnApiControllers().check(productionClasses);
    }

    @Test
    void api_must_not_access_infrastructure_directly() {
        ModuleArchitectureRules.apiMustNotAccessInfrastructureDirectly().check(productionClasses);
    }

    @Test
    void core_must_not_depend_on_business_modules() {
        ModuleArchitectureRules.coreMustNotDependOnBusinessModules().check(productionClasses);
    }

    @Test
    void cross_module_access_must_not_target_persistence() {
        ModuleArchitectureRules.crossModuleAccessMustNotTargetPersistence().check(productionClasses);
    }

    @Test
    void cross_module_access_must_target_public_contracts_only() {
        ModuleArchitectureRules.crossModuleAccessMustTargetPublicContractsOnly().check(productionClasses);
    }

    @Test
    void repositories_must_not_be_imported_outside_their_module() {
        ModuleArchitectureRules.repositoriesMustNotBeImportedOutsideTheirModule().check(productionClasses);
    }

    @Test
    void modules_must_use_the_event_bus_contract_not_its_implementation() {
        ModuleArchitectureRules.modulesMustUseTheEventBusContractNotItsImplementation().check(productionClasses);
    }

    @Test
    void module_contracts_must_live_in_api_or_events_packages() {
        ModuleArchitectureRules.moduleContractsMustLiveInApiOrEventsPackages().check(productionClasses);
    }

    @Test
    void modules_must_be_free_of_cycles() {
        ModuleArchitectureRules.modulesMustBeFreeOfCycles().check(productionClasses);
    }

    @Test
    void controllers_must_not_be_transactional() {
        ModuleArchitectureRules.controllersMustNotBeTransactional().check(productionClasses);
        ModuleArchitectureRules.controllerMethodsMustNotBeTransactional().check(productionClasses);
    }

    @Test
    void repositories_must_not_declare_transactional() {
        ModuleArchitectureRules.repositoriesMustNotDeclareTransactional().check(productionClasses);
    }

    private void assertProductionClassesExistIn(String packageIdentifier) {
        boolean present = productionClasses.stream()
            .anyMatch(JavaClass.Predicates.resideInAPackage(packageIdentifier));

        assertTrue(present, "no production classes were imported from '" + packageIdentifier
            + "', so every rule targeting it would pass without inspecting anything");
    }

    private long countAnnotatedWith(String annotationTypeName) {
        return productionClasses.stream()
            .filter(CanBeAnnotated.Predicates.annotatedWith(annotationTypeName))
            .count();
    }
}
