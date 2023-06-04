package com.bso.webfluxdemo.infra.lock.webflux

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RedisRSemaphoreReactiveManagerImplTest {
    @Autowired
    private lateinit var subject: RedisRSemaphoreReactiveManagerImpl

    companion object {
        private const val KEY = "key"
    }

    @BeforeEach
    fun setup() {
        subject.unlock(KEY).block()
    }

    @Test
    @DisplayName("should throw if key is already locked by the same flow")
    fun throwIfLockedEvenInSameBlock() {
        val mono = subject.runWithLock(KEY) {
            subject.runWithLock(KEY) {
                Mono.just("xpto")
            }
        }

        StepVerifier.create(mono.log())
            .expectSubscription()
            .expectErrorMessage("Can't acquire lock for key key")
            .verify()

        verifyLocked(false)
    }

    @Test
    @DisplayName("should execute with success if key is not locked")
    fun lockWithSuccess() {
        val mono = subject.runWithLock(KEY) {
            Mono.just("xpto")
        }

        StepVerifier.create(mono.log())
            .expectSubscription()
            .expectNext("xpto")
            .verifyComplete()

        verifyLocked(false)
    }

    @Test
    @DisplayName("should lock, execute and release a few times with success")
    fun lockAndReleaseFewTimes() {
        val monos: List<Mono<String>> = (0..100).map {
            subject.runWithLock(KEY) {
                Mono.just("Test number $it")
            }
        }

        monos.forEachIndexed { index, mono ->
            StepVerifier.create(mono.log())
                .expectSubscription()
                .expectNext("Test number $index")
                .verifyComplete()
        }

        verifyLocked(false)
    }

    @Test
    @DisplayName("should release lock if get error in action")
    fun releaseAfterError() {
        val mono = subject.runWithLock(KEY) {
            Mono.error(IllegalStateException("Test Error"))
        }

        StepVerifier.create(mono.log())
            .expectSubscription()
            .expectErrorMessage("Test Error")
            .verify()

        verifyLocked(false)
    }

    private fun verifyLocked(locked: Boolean) {
        StepVerifier.create(subject.locked(KEY).log())
            .expectSubscription()
            .expectNext(locked)
            .verifyComplete()
    }
}
