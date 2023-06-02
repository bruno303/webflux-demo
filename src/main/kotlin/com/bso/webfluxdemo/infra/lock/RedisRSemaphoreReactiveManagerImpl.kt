package com.bso.webfluxdemo.infra.lock

import com.bso.webfluxdemo.application.configuration.AppConfigurationProperties
import com.bso.webfluxdemo.application.lock.Lock
import com.bso.webfluxdemo.application.lock.LockManager
import org.redisson.api.RPermitExpirableSemaphoreReactive
import org.redisson.api.RedissonReactiveClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val logger: Logger by lazy { LoggerFactory.getLogger(RedisRSemaphoreReactiveManagerImpl::class.java) }

    companion object {
        private const val LOCKS_HASH_NAME = "WEBFLUX_DEMO_LOCKS"
    }

    override fun <T : Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T> {
        logger.info("Locking key $key...")
        val semaphore = getSemaphore(key)
        return semaphore
            .expire(Duration.ofMinutes(1))
            .then(semaphore.setPermits(1))
            .then(
                semaphore.lock()
                    .switchIfEmpty {
                        Mono.error(IllegalStateException("Can't acquire lock for key $key"))
                    }
            )
            .flatMap { lockId: String -> execute(action, key, semaphore, lockId) }
    }

    private fun RPermitExpirableSemaphoreReactive.lock() = this.tryAcquire(
        prop.lock.waitTimeSeconds.toLong(),
        prop.lock.leaseTimeSeconds.toLong(),
        TimeUnit.SECONDS
    )

    fun lock(key: String): Mono<String> {
        logger.info("Locking key $key...")
        val semaphore = getSemaphore(key)
        return semaphore
            .expire(Duration.ofMinutes(1))
            .then(semaphore.setPermits(1))
            .then(
                semaphore.lock()
                    .switchIfEmpty {
                        Mono.error(IllegalStateException("Can't acquire lock for key $key"))
                    }
            )
    }

    private fun <T : Any> execute(
        action: () -> Mono<T>,
        key: String,
        semaphore: RPermitExpirableSemaphoreReactive,
        lockId: String
    ) = action()
        .flatMap { result ->
            logger.info("Unlocking key $key...")
            semaphore.release(lockId).then(Mono.just(result))
        }
        .onErrorResume {
            logger.info("Got some error. Unlocking key $key...")
            semaphore.release(lockId).then(Mono.error(it))
        }

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
                                throw IllegalStateException("Could not delete semaphore for key: $key")
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