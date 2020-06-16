Complex name story

Given import java.util.concurrent.atomic

Given drools session classpath:/org/droolsassert/complex name * ${with}(*)[or].drl


!-- test int
Given new session for scenario
	ignore * ${with}(and)[??]
Given variable atomicInteger is new AtomicInteger()
Given variable atomicLong is new AtomicLong()
When insert and fire atomicInteger, atomicLong
Then assert atomicInteger.get() is 1
Then assert atomicLong.get() is 1L
Then there was single activation 'atomic int rule'
