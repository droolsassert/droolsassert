package org.droolsassert;

import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.Format.TXT;

import java.util.List;

import org.droolsassert.jbehave.DroolsAssertSteps;
import org.jbehave.core.InjectableEmbedder;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.model.ExamplesTableFactory;
import org.jbehave.core.model.TableTransformers;
import org.jbehave.core.parsers.RegexStoryParser;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.InjectableStepsFactory;
import org.jbehave.core.steps.InstanceStepsFactory;
import org.junit.jupiter.api.Test;

public class JbehaveTest extends InjectableEmbedder {
	
	private DroolsAssertSteps<DroolsAssert> droolsAssertSteps = new DroolsAssertSteps<>();
	
	@Test
	@Override
	public void run() {
		Embedder embedder = injectedEmbedder();
		embedder.useConfiguration(configuration());
		embedder.useStepsFactory(stepsFactory());
		embedder.runStoriesAsPaths(storyPaths());
	}
	
	public Configuration configuration() {
		return new MostUsefulConfiguration()
				.useStoryParser(new RegexStoryParser(new ExamplesTableFactory(new LoadFromClasspath(this.getClass()), new TableTransformers())))
				.useStoryReporterBuilder(new StoryReporterBuilder()
						.withCodeLocation(codeLocationFromClass(this.getClass()))
						.withDefaultFormats().withFormats(TXT)
						.withReporters(droolsAssertSteps)
						.withFailureTrace(true));
	}
	
	public InjectableStepsFactory stepsFactory() {
		return new InstanceStepsFactory(configuration(), droolsAssertSteps);
	}
	
	protected List<String> storyPaths() {
		return new StoryFinder().findPaths(codeLocationFromClass(this.getClass()), "**/stories/*.story", "");
	}
}
