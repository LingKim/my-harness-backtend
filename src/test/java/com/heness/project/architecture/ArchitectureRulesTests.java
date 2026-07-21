package com.heness.project.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
		packages = ArchitectureRulesTests.ROOT_PACKAGE,
		importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTests {

	static final String ROOT_PACKAGE = "com.heness.project";

	private static final Set<String> BUSINESS_MODULES = Set.of(
			"account",
			"assistant",
			"community",
			"guide",
			"media",
			"moderation",
			"notification",
			"support"
	);
	private static final Set<String> SHARED_FORBIDDEN_TYPE_SUFFIXES = Set.of(
			"Controller",
			"Dto",
			"DTO",
			"Entity",
			"Mapper",
			"Repository",
			"Row",
			"Service"
	);

	@ArchTest
	static final ArchRule AI_DEPENDENCIES_BELONG_TO_ASSISTANT_INFRASTRUCTURE = classes()
			.that(dependOnPackage("org.springframework.ai."))
			.should().resideInAnyPackage(ROOT_PACKAGE + ".assistant.infrastructure..")
			.because("Spring AI 是 assistant 模块的外部适配器")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_FRAMEWORKS = noClasses()
			.that().resideInAnyPackage(ROOT_PACKAGE + ".*.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..",
					"org.apache.ibatis..",
					"com.baomidou.mybatisplus.."
			)
			.because("领域规则必须能够脱离 Spring MVC、Spring AI 和 MyBatis 运行")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_API_OR_INFRASTRUCTURE = noClasses()
			.that().resideInAnyPackage(ROOT_PACKAGE + ".*.application..")
			.should().dependOnClassesThat().resideInAnyPackage(
					ROOT_PACKAGE + ".*.api..",
					ROOT_PACKAGE + ".*.infrastructure.."
			)
			.because("应用层只能编排用例并依赖领域及端口")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_API = noClasses()
			.that().resideInAnyPackage(ROOT_PACKAGE + ".*.infrastructure..")
			.should().dependOnClassesThat().resideInAnyPackage(ROOT_PACKAGE + ".*.api..")
			.because("基础设施适配器不能依赖 HTTP 协议入口")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE_OR_CHAT_MODEL = noClasses()
			.that().areAnnotatedWith(RestController.class)
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.apache.ibatis..",
					"com.baomidou.mybatisplus..",
					"org.springframework.ai.chat.model.."
			)
			.because("Controller 必须通过应用用例访问持久化和模型能力");

	@ArchTest
	static final ArchRule BUSINESS_MODULES_ONLY_USE_OTHER_MODULE_APPLICATION_CONTRACTS = classes()
			.that().resideInAnyPackage(businessModulePackages())
			.should(onlyAccessOtherModulesThroughApplication())
			.because("跨模块协作只能使用目标模块的 application 契约")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule TOP_LEVEL_PACKAGES_ARE_FREE_OF_CYCLES = slices()
			.matching(ROOT_PACKAGE + ".(*)..")
			.should().beFreeOfCycles()
			.because("模块化单体不能形成顶层 package 循环依赖");

	@ArchTest
	static final ArchRule PROJECT_DOES_NOT_USE_JPA = noClasses()
			.should().dependOnClassesThat().resideInAnyPackage(
					"jakarta.persistence..",
					"org.springframework.data.jpa.."
			)
			.because("本项目使用 MyBatis-Plus，数据库结构只由 Flyway 管理");

	@ArchTest
	static final ArchRule SHARED_DOES_NOT_DEPEND_ON_BUSINESS_MODULES = noClasses()
			.that().resideInAnyPackage(ROOT_PACKAGE + ".shared..")
			.should().dependOnClassesThat().resideInAnyPackage(businessModulePackages())
			.because("shared 只能包含无单一业务归属的技术能力")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule SHARED_DOES_NOT_DECLARE_BUSINESS_SHAPED_TYPES = classes()
			.that().resideInAnyPackage(ROOT_PACKAGE + ".shared..")
			.should(notHaveBusinessTypeName())
			.because("业务 DTO、持久化对象、仓储和 Service 不能进入 shared")
			.allowEmptyShould(true);

	private static String[] businessModulePackages() {
		return BUSINESS_MODULES.stream()
				.map(module -> ROOT_PACKAGE + "." + module + "..")
				.toArray(String[]::new);
	}

	private static DescribedPredicate<JavaClass> dependOnPackage(String packagePrefix) {
		return DescribedPredicate.describe(
				"依赖 " + packagePrefix,
				javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
						.map(Dependency::getTargetClass)
						.map(JavaClass::getPackageName)
						.anyMatch(packageName -> packageName.startsWith(packagePrefix))
		);
	}

	private static ArchCondition<JavaClass> onlyAccessOtherModulesThroughApplication() {
		return new ArchCondition<>("只通过其他业务模块的 application 契约访问") {
			@Override
			public void check(JavaClass sourceClass, ConditionEvents events) {
				String sourceModule = businessModuleOf(sourceClass.getPackageName());
				if (sourceModule == null) {
					return;
				}

				for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
					JavaClass targetClass = dependency.getTargetClass();
					String targetModule = businessModuleOf(targetClass.getPackageName());
					if (targetModule == null || sourceModule.equals(targetModule)) {
						continue;
					}

					String allowedPrefix = ROOT_PACKAGE + "." + targetModule + ".application";
					if (!targetClass.getPackageName().startsWith(allowedPrefix)) {
						events.add(SimpleConditionEvent.violated(
								sourceClass,
								dependency.getDescription()
						));
					}
				}
			}
		};
	}

	private static ArchCondition<JavaClass> notHaveBusinessTypeName() {
		return new ArchCondition<>("不使用业务类型后缀") {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				boolean usesForbiddenSuffix = SHARED_FORBIDDEN_TYPE_SUFFIXES.stream()
						.anyMatch(javaClass.getSimpleName()::endsWith);
				if (usesForbiddenSuffix) {
					events.add(SimpleConditionEvent.violated(
							javaClass,
							javaClass.getName() + " 使用了 shared 禁止的业务类型后缀"
					));
				}
			}
		};
	}

	private static String businessModuleOf(String packageName) {
		String prefix = ROOT_PACKAGE + ".";
		if (!packageName.startsWith(prefix)) {
			return null;
		}

		String remainder = packageName.substring(prefix.length());
		int separatorIndex = remainder.indexOf('.');
		String topLevelPackage = separatorIndex < 0
				? remainder
				: remainder.substring(0, separatorIndex);
		return BUSINESS_MODULES.contains(topLevelPackage) ? topLevelPackage : null;
	}
}
