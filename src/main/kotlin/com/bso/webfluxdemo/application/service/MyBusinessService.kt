package com.bso.webfluxdemo.application.service

import com.bso.webfluxdemo.application.domain.entity.Person
import com.bso.webfluxdemo.application.lock.LockManager
import com.bso.webfluxdemo.application.repository.PersonRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

@Service
class MyBusinessService(
    private val personRepository: PersonRepository,
    private val externalService: ExternalService,
    private val lockManager: LockManager
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }
//    private val uuidTest = UUID.randomUUID()

    @Transactional
    fun executeWithWebfluxStreams() : Mono<Person> {
        val person = Person(
            id = UUID.randomUUID(),
            name = "Person Xpto",
            birthDate = LocalDate.of(1995, 12, 1)
        )

        return lockManager.runWithLock(person.id.toString()) {
            externalService.getExternalIdFromExternalService()
//                .flatMap { Mono.just(person.copy(externalId = it)) }
                .map { person.copy(externalId = it) }
                .flatMap { personRepository.save(it) }
                .doOnNext { logger.info("Person created: {}", it) }
        }
    }


    // TODO - try implement some code using kotlin's Flow
    @Transactional
    suspend fun executeWithKotlinFlow(): Person {
        val person = Person(
            id = UUID.randomUUID(),
            name = "Person Xpto2",
            birthDate = LocalDate.of(1995, 12, 1)
        ).let { person ->
            externalService.getExternalIdFromExternalService().awaitSingle().let {
                person.copy(externalId = it)
            }
        }

        return personRepository.save(person).awaitSingle().also {
            logger.info("Person created: {}", it)
//            throw IllegalStateException("Teste")
        }
    }
}
