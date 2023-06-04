package com.bso.webfluxdemo.application.service

import com.bso.webfluxdemo.application.domain.entity.Person
import com.bso.webfluxdemo.application.lock.WebfluxLockManager
import com.bso.webfluxdemo.application.repository.PersonRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
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
    private val webfluxLockManager: WebfluxLockManager
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

        return webfluxLockManager.runWithLock(person.id.toString()) {
            externalService.getExternalIdFromExternalService()
//                .flatMap { Mono.just(person.copy(externalId = it)) }
                .map { person.copy(externalId = it) }
                .flatMap { personRepository.save(it) }
                .doOnNext { logger.info("Person created: {}", it) }
        }
    }


    @Transactional
    suspend fun executeWithKotlinSuspendFunctions(): Person {
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

    @FlowPreview
    @Transactional
    suspend fun executeWithKotlinFlow(): Flow<Person> {
        return Person(
            id = UUID.randomUUID(),
            name = "Person Xpto3",
            birthDate = LocalDate.of(1995, 12, 1)
        ).let { person ->
            externalService.getExternalIdFromExternalService()
                .asFlow()
//                .flatMapConcat { person.withExternalId(it) }
                .map { person.copy(externalId = it) }
//                .flatMapConcat { savePerson(it) }
                .flatMapMerge {
                    savePerson(it)
//                        .also { throw IllegalStateException("Teste") }
                }
                .onEach { logger.info("Person created: {}", it) }
        }
    }

    private fun savePerson(person: Person): Flow<Person> = personRepository.save(person).asFlow()

    private fun Person.withExternalId(externalId: String): Flow<Person> =
        flowOf(this.copy(externalId = externalId))
}
