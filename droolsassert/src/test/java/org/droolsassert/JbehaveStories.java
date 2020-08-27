package org.droolsassert;

import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.Format.TXT;

import java.util.List;

import org.droolsassert.jbehave.DroolsAssertSteps;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.model.ExamplesTableFactory;
import org.jbehave.core.model.TableTransformers;
import org.jbehave.core.parsers.RegexStoryParser;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.InjectableStepsFactory;
import org.jbehave.core.steps.InstanceStepsFactory;

public class JbehaveStories extends JUnitStories {
	
	private DroolsAssertSteps<DroolsAssert> droolsAssertSteps = new DroolsAssertSteps<>();
	
	@Override
	public Configuration configuration() {
		return new MostUsefulConfiguration()
				.useStoryParser(new RegexStoryParser(new ExamplesTableFactory(new LoadFromClasspath(this.getClass()), new TableTransformers())))
				.useStoryReporterBuilder(new StoryReporterBuilder()
						.withCodeLocation(codeLocationFromClass(this.getClass()))
						.withDefaultFormats().withFormats(TXT)
						.withMultiThreading(false)
						.withReporters(droolsAssertSteps)
						.withFailureTrace(true));
	}
	
	@Override
	public InjectableStepsFactory stepsFactory() {
		return new InstanceStepsFactory(configuration(), droolsAssertSteps);
	}
	
	@Override
	protected List<String> storyPaths() {
		return new StoryFinder().findPaths(codeLocationFromClass(this.getClass()), "**/stories/*.story", "");
	}
}
