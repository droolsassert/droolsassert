JUnit `TestRule` for declarative drools tests.  

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  

Specify rule names which are expected to be triggered for each use case using `@AssertRules` in addition to assertions inside test method.

**Examples**    

[Dummy assertions](https://github.com/droolsassert/droolsassert/wiki/Dummy-assertions)  
[Logical events](https://github.com/droolsassert/droolsassert/wiki/Logical-events)

**Latest maven builds**

For Drools 7.x  

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.7.3</version>
        <scope>test</scope>
    </dependency>

For Drools 6.x  

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.6.3</version>
        <scope>test</scope>
    </dependency>
