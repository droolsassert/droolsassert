Logical events story

Scenario: definitions
Given import org.droolsassert.SpringIntegrationTest

Given drools session classpath:/org/droolsassert/weather.drl

Given global weatherUrl is 'https://samples.openweathermap.org/data/2.5/weather?q=London,uk&appid=b6907d289e10d714a6e88b30761fae22'
Given global restTemplate is a spring service restTemplate


Scenario: test weather in London
Given new session for scenario
When advance time for 1 hours
Given variable weather as Weather object from the session
Then assert weather.humidity is 81
Then all activations are 
    Check weather
    Humidity is high
