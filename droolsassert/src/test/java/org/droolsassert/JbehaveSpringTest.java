package org.droolsassert;

import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.Format.TXT;

import java.util.List;

import org.droolsassert.JbehaveSpringTest.AppConfig;
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
import org.jbehave.core.steps.spring.SpringStepsFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { AppConfig.class })
public class JbehaveSpringTest extends InjectableEmbedder {
	
	@Autowired
	private ApplicationContext appContext;
	@Autowired
	private SpringContextAwareDroolsAssertSteps springContextAwareDroolsAssertSteps;
	
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
						.withReporters(springContextAwareDroolsAssertSteps)
						.withFailureTrace(true));
	}
	
	public InjectableStepsFactory stepsFactory() {
		return new SpringStepsFactory(configuration(), appContext);
	}
	
	protected List<String> storyPaths() {
		return new StoryFinder().findPaths(codeLocationFromClass(this.getClass()), "**/springStories/*.story", "");
	}
	
	public static class AppConfig {
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}
		
		@Bean
		public SpringContextAwareDroolsAssertSteps springContextAwareDroolsAssertSteps() {
			return new SpringContextAwareDroolsAssertSteps();
		}
	}
	
	public static class SpringContextAwareDroolsAssertSteps extends DroolsAssertSteps<DroolsAssert> implements ApplicationContextAware {
		
		private ApplicationContext appContext;
		
		@Override
		public void setApplicationContext(ApplicationContext appContext) throws BeansException {
			this.appContext = appContext;
		}
		
		@Override
		protected Object resolveSpringService(String name) {
			return appContext.getBean(name);
		}
	}
}
