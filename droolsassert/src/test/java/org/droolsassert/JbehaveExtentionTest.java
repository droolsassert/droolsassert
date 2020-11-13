package org.droolsassert;

import static com.google.common.base.Charsets.UTF_8;
import static org.droolsassert.util.JsonUtils.fromJson;
import static org.droolsassert.util.JsonUtils.fromYaml;
import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.Format.TXT;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.droolsassert.jbehave.DroolsAssertSteps;
import org.jbehave.core.annotations.Then;
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

import com.google.common.io.Resources;

public class JbehaveExtentionTest extends JUnitStories {
	
	private ExtendedDroolsAssertSteps steps = new ExtendedDroolsAssertSteps();
	
	@Override
	public Configuration configuration() {
		return new MostUsefulConfiguration()
				.useStoryParser(new RegexStoryParser(new ExamplesTableFactory(new LoadFromClasspath(this.getClass()), new TableTransformers())))
				.useStoryReporterBuilder(new StoryReporterBuilder()
						.withCodeLocation(codeLocationFromClass(this.getClass()))
						.withDefaultFormats().withFormats(TXT)
						.withReporters(steps)
						.withFailureTrace(true));
	}
	
	@Override
	public InjectableStepsFactory stepsFactory() {
		return new InstanceStepsFactory(configuration(), steps);
	}
	
	@Override
	protected List<String> storyPaths() {
		return new StoryFinder().findPaths(codeLocationFromClass(this.getClass()), "**/extendedStories/*.story", "");
	}
	
	public static class ExtendedDroolsAssertSteps extends DroolsAssertSteps<DroolsAssert> {
		
		@Then("no errors reported")
		public void assertNoErrors() throws Exception {
			assertEmpty(new ArrayList<Throwable>(drools.getObjects(Throwable.class)));
		}
		
		@Override
		protected Object resolveVariableFromJson(String type, String expression) {
			return fromJson(mvelProcessor.process(expression), classOf(type));
		}
		
		@Override
		protected Object resolveVariableFromJsonResource(String type, String expression) throws IOException {
			return fromJson(mvelProcessor.process(Resources.toString(resourceResolver.getResource(expression).getURL(), UTF_8)), classOf(type));
		}
		
		@Override
		protected Object resolveValriableFromYaml(String type, String expression) {
			return fromYaml(mvelProcessor.process(expression), classOf(type));
		}
		
		@Override
		protected Object resolveVariableFromYamlResource(String type, String expression) throws IOException {
			return fromYaml(mvelProcessor.process(Resources.toString(resourceResolver.getResource(expression).getURL(), UTF_8)), classOf(type));
		}
	}
}
