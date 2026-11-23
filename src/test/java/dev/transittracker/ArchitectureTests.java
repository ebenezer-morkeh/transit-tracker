package dev.transittracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.net.http.HttpClient;

/** Layering rules from CODING_STANDARDS.md that a test can check. */
@AnalyzeClasses(packages = "dev.transittracker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	@ArchTest
	static final ArchRule controllersGoThroughServices = noClasses().that()
		.haveSimpleNameEndingWith("Controller")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Repository")
		.because("controllers call services; services own repositories");

	@ArchTest
	static final ArchRule repositoriesStayInTheirFeature = classes().that()
		.haveSimpleNameEndingWith("Repository")
		.should()
		.notBePublic()
		.because("package-private, so the compiler stops other features from bypassing that feature's service");

	@ArchTest
	static final ArchRule ingestUsesServicesOnly = noClasses().that()
		.resideInAPackage("..ingest..")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Repository")
		.because("the poller saves through VehicleService and DelayService");

	@ArchTest
	static final ArchRule httpClientsComeFromFeedHttp = noClasses().that()
		.doNotHaveSimpleName("FeedHttp")
		.should()
		.callMethod(HttpClient.class, "newBuilder")
		.because("FeedHttp sets the redirect policy; agency feeds redirect (see docs/feeds.md)");

}
