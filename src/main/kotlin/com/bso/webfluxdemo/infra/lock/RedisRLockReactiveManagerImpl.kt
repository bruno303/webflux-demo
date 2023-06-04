package com.bso.webfluxdemo.infra.lock

import com.bso.webfluxdemo.application.lock.Lock
import com.bso.webfluxdemo.application.lock.LockManager
import com.bso.webfluxdemo.crosscutting.log.logger
import org.redisson.api.RLockReactive
import org.redisson.api.RedissonReactiveClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

@Deprecated(
    message = "Use RedisRSemaphoreReactiveManagerImpl instead",
    replaceWith = ReplaceWith(
        "RedisRSemaphoreReactiveManagerImpl",
        "com.bso.webfluxdemo.infra.lock.RedisRSemaphoreReactiveManagerImpl"
    ),
    level = DeprecationLevel.ERROR
)
class RedisRLockReactiveManagerImpl(
    private val redissonReactiveClient: RedissonReactiveClient
) : LockManager {
    private val logger: Logger by logger()

    override fun <T : Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T> {
        val lock = getLock(key)
        return lock
            .tryLock()
            .filter { locked -> locked }
            .flatMap {
                action()
                    .flatMap { result ->
                        lock.unlock().then(Mono.just(result))
                    }
                    .onErrorResume {
                        lock.unlock().then(Mono.error(it))
                    }
            }
            .doOnError { logger.error("Error while executing action with lock for key $key", it) }
    }

    @Synchronized
    private fun getLock(key: String): Lock {
        return RLockReactiveRedisLock(
            key = key,
            redisLock = redissonReactiveClient.getLock(key)
        )
    }

    override fun unlock(key: String): Mono<Void> {
        TODO("Not yet implemented")
    }
}

private class RLockReactiveRedisLock(
    private val key: String,
    private val redisLock: RLockReactive
) : Lock {
    private val logger: Logger by lazy { LoggerFactory.getLogger(RLockReactiveRedisLock::class.java) }
    private val threadId: Long = Thread.currentThread().id

    override fun tryLock(): Mono<Boolean> {
        logger.info("Locking key $key...")

        return redisLock.tryLock(threadId)
            .flatMap { locked ->
                return@flatMap if (locked) {
                    Mono.just(locked)
                } else {
                    val errorMessage = "Failed to acquire lock for key $key"
                    logger.error(errorMessage, IllegalStateException(errorMessage))
                    Mono.error(IllegalStateException(errorMessage))
                }
            }
    }
    override fun unlock(): Mono<Void> {
        return redisLock.isLocked.flatMap { locked ->
            if (locked) {
                logger.info("Unlocking key $key...")
                redisLock.unlock(threadId)
            } else {
                Mono.empty()
            }
        }
    }

    override fun forceUnlock(): Mono<Boolean> {
        return redisLock.isLocked.flatMap { locked ->
            if (locked) {
                logger.info("Forcing unlock key $key...")
                redisLock.forceUnlock()
            } else {
                Mono.just(locked)
            }
        }
    }
}