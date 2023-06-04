package com.bso.webfluxdemo.infra.lock

import com.bso.webfluxdemo.infra.configuration.AppConfigurationProperties
import com.bso.webfluxdemo.application.lock.LockManager
import com.bso.webfluxdemo.crosscutting.log.logger
import org.redisson.api.RPermitExpirableSemaphoreReactive
import org.redisson.api.RedissonReactiveClient
import org.slf4j.Logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class RedisRSemaphoreReactiveManagerImpl(
    private val prop: AppConfigurationProperties,
    private val redissonReactiveClient: RedissonReactiveClient
) : LockManager {
    private val logger: Logger by logger()

    companion object {
        private const val LOCKS_HASH_NAME = "WEBFLUX_DEMO_LOCKS"
    }

    override fun <T : Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T> {
        val semaphore = getSemaphore(key)
        return lock(semaphore, key)
            .flatMap(executeAndRelease(action, key, semaphore))
    }

    private fun <T : Any> executeAndRelease(
        action: () -> Mono<T>,
        key: String,
        semaphore: RPermitExpirableSemaphoreReactive
    ) = { lockId: String ->
        action()
            .flatMap { result ->
                logger.info("Unlocking key $key...")
                semaphore.release(lockId).then(Mono.just(result))
//            Mono.just(result)
            }
            .onErrorResume {
                logger.info("Got some error. Unlocking key $key...")
                semaphore.release(lockId).then(Mono.error(it))
//            Mono.error(it)
            }
    }

    private fun RPermitExpirableSemaphoreReactive.lock(): Mono<String> = this.tryAcquire(
        prop.lock.waitTimeSeconds.toLong(),
        prop.lock.leaseTimeSeconds.toLong(),
        TimeUnit.SECONDS
    )

    fun lock(semaphore: RPermitExpirableSemaphoreReactive, key: String): Mono<String> {
        logger.info("Locking key $key...")
        return semaphore
            .setPermits(1)
            .then(
                semaphore.lock()
                    .flatMap { lockId -> setTtlAndReturn(semaphore, lockId) }
                    .switchIfEmpty {
                        Mono.error(IllegalStateException("Can't acquire lock for key $key"))
                    }
            )
    }

    private fun <T: Any> setTtlAndReturn(
        semaphore: RPermitExpirableSemaphoreReactive,
        result: T
    ): Mono<T> = semaphore
        .expire(Duration.ofMinutes(1))
        .then(Mono.just(result))

    @Synchronized
    private fun getSemaphore(key: String): RPermitExpirableSemaphoreReactive {
        return redissonReactiveClient.getPermitExpirableSemaphore("${LOCKS_HASH_NAME}_$key")
    }

    fun locked(key: String): Mono<Boolean> {
        val semaphore = getSemaphore(key)
        return semaphore
            .isExists
            .flatMap { exists ->
                if (!exists) {
                    Mono.just(false)
                } else {
                    semaphore.availablePermits().map { it < 1 }
                }
            }
    }

    override fun unlock(key: String): Mono<Void> {
        val semaphore = getSemaphore(key)
        return semaphore
            .isExists
            .flatMap { exists ->
                if (exists) {
                    semaphore.delete()
                        .flatMap { deleted ->
                            if (!deleted) {
                                Mono.error(IllegalStateException("Could not delete semaphore for key: $key"))
                            } else {
                                logger.info("Semaphore for key $key deleted")
                                Mono.empty()
                            }
                        }
                } else {
                    logger.info("Semaphore for key $key does not exist")
                    Mono.empty()
                }
            }
    }
}
