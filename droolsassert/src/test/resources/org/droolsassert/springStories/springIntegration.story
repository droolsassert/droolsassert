Logical events story

Scenario: definitions
Given import org.droolsassert.SpringIntegrationTest

Given drools session classpath:/org/droolsassert/weather.drl

Given global weatherUrl is 'https://api.agromonitoring.com/agro/1.0/weather?lat=35&lon=139&appid=f4bacddfb3de281a5b88f8fb4c6c4237'
Given global restTemplate is a spring service restTemplate


Scenario: test weather in London
Given new session for scenario
When advance time for 1 hour
Given variable weather as Weather object from the session
Then assert weather.humidity greater than 0
Then all activations are 
    Check weather
    Humidity is high
