package com.bso.webfluxdemo.web

import com.bso.webfluxdemo.application.domain.entity.Person
import com.bso.webfluxdemo.application.repository.PersonRepository
import com.bso.webfluxdemo.application.service.MyBusinessService
import com.bso.webfluxdemo.crosscutting.mono.getValue
import com.bso.webfluxdemo.infra.web.filters.IdempotencyIdWebFilter.Companion.IDEMPOTENCY_HEADER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("person")
class PersonController(
    private val myBusinessService: MyBusinessService,
    private val personRepository: PersonRepository
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

    @Transactional
    @PostMapping("random")
    fun createRandomPerson() : Mono<Person> {
        return myBusinessService.executeWithWebfluxStreams()
    }

    @Transactional
    @PostMapping("random/idempotency")
    fun createRandomPersonWithIdempotencyId() : Mono<Person> {
        return Mono.deferContextual { ctx ->
            val idempotencyId: String? = ctx.getValue(IDEMPOTENCY_HEADER)
            logger.info("Idempotency-Id is {}", idempotencyId)
            // validate idempotency
            myBusinessService.executeWithWebfluxStreams()
                .contextWrite { it.put("key2", "value2") }
        }
    }

    @PostMapping("random2")
    suspend fun createRandomPersonWithKotlin() : Person {
        return myBusinessService.executeWithKotlinFlow()
    }

    @GetMapping
    fun listAll() : Flux<Person> {
        return personRepository.findAll()
    }
}
