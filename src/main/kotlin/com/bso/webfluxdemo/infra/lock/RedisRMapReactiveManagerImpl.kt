package com.bso.webfluxdemo.infra.lock

import com.bso.webfluxdemo.application.lock.Lock
import com.bso.webfluxdemo.application.lock.LockManager
import org.redisson.api.RMapReactive
import org.redisson.api.RedissonReactiveClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.time.Duration

//@Component
class RedisRMapReactiveManagerImpl(
    private val redissonReactiveClient: RedissonReactiveClient
) : LockManager {
    private val logger: Logger by lazy { LoggerFactory.getLogger(RedisRMapReactiveManagerImpl::class.java) }

    companion object {
        private const val LOCKS_MAP_NAME = "WEBFLUX_DEMO_LOCKS"
        private const val LOCK_TTL: Long = 30
    }

    override fun <T : Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T> {
        logger.info("Locking key $key...")
        val map = getMap(key)
        return map
            .expire(Duration.ofMinutes(1))
            .flatMap { map.fastPutIfAbsent(key, LockState.RELEASED.name) }
            .flatMap { map.get(key) }
            .flatMap { locked ->
                if (LockState.valueOf(locked) == LockState.LOCKED) {
                    val errorMessage = "Failed to acquire lock for key $key"
                    logger.error(errorMessage, IllegalStateException(errorMessage))
                    Mono.error(IllegalStateException(errorMessage))
                } else {
                    map.fastPut(key, LockState.LOCKED.name).also {
                        logger.info("Locking acquired for key $key...")
                    }
                }
            }
            .flatMap {
                action()
                    .flatMap { result ->
                        logger.info("Unlocking key $key...")
                        map.fastPut(key, LockState.RELEASED.name)
                            .then(Mono.just(result))
                    }
                    .onErrorResume {
                        logger.info("Unlocking key $key...")
                        map.fastPut(key, LockState.RELEASED.name)
                            .then(Mono.error(it))
                    }
            }
    }

    @Synchronized
    private fun getMap(key: String): RMapReactive<String, String> {
        return redissonReactiveClient.getMap("${LOCKS_MAP_NAME}_$key")
    }

    override fun unlock(key: String): Mono<Void> {
        TODO("Not yet implemented")
    }
}

private enum class LockState {
    LOCKED,
    RELEASED
}