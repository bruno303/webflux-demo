package com.bso.webfluxdemo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import reactor.core.publisher.Flux

@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
class WebfluxDemoApplication

fun flux() = Flux
	.fromIterable(listOf(1, 2, 3))

fun controller(): Flux<Int> = flux()
	.doOnSubscribe { println("Subscription done") }
	.map { it * 2 }
	.doOnNext { println(it) }
	.doOnEach { println("Called ${it.type} for element ${it.get()}") }
	.doOnComplete { println("OnComplete") }

fun main(args: Array<String>) {
	runApplication<WebfluxDemoApplication>(*args)
//	controller().subscribe { println("subscription consumer executed for $it") }
}
