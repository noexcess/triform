package io.triform.sample.allinone;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/** T14: 契约纯净护栏——api 包不得依赖任何实现/启动/web 包(编译期隔离的运行时兜底)。 */
class ArchitectureGuardTest {

    @Test
    void apiPackagesMustNotDependOnImplementations() {
        ArchRuleDefinition.noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..echo..", "..app..", "..allinone..", "..gateway..")
                .check(new ClassFileImporter().importPackages("io.triform"));
    }
}
