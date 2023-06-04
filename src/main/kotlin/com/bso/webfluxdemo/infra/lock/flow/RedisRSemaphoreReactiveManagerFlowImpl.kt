package com.bso.webfluxdemo.infra.lock.flow

import com.bso.webfluxdemo.application.lock.KotlinFlowLockManager
import com.bso.webfluxdemo.crosscutting.log.logger
import com.bso.webfluxdemo.infra.configuration.AppConfigurationProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import org.redisson.api.RPermitExpirableSemaphoreReactive
import org.redisson.api.RedissonReactiveClient
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@FlowPreview
@Component
class RedisRSemaphoreReactiveManagerFlowImpl(
    private val prop: AppConfigurationProperties,
    private val redissonReactiveClient: RedissonReactiveClient
) : KotlinFlowLockManager {
    private val logger: Logger by logger()

    companion object {
        private const val LOCKS_HASH_NAME = "WEBFLUX_DEMO_LOCKS"
    }

    override fun <T : Any> runWithLock(key: String, action: () -> Flow<T>): Flow<T> {
        logger.info("Locking key $key...")
        val semaphore = getSemaphore(key)
        return semaphore
            .expire(Duration.ofMinutes(1)).asFlow()
            .then { semaphore.setPermits(1).asFlow() }
            .then { semaphore.lock().onEmpty { error("Can't acquire lock for key $key") } }
            .flatMapConcat { lockId: String ->
                action()
                    .flatMapConcat { result: T ->
                        logger.info("Unlocking key $key...")
                        semaphore.releaseFlow(lockId)
                            .flatMapConcat { flowOf(result) }
                    }
                    .catch { ex: Throwable ->
                        logger.info("Got some error. Unlocking key $key...")
                        semaphore.releaseFlow(lockId)
                            .flatMapConcat<Void, T> { error(ex.message ?: "") }

                    }
            }
    }

    private fun RPermitExpirableSemaphoreReactive.lock(): Flow<String> = this.tryAcquire(
        prop.lock.waitTimeSeconds.toLong(),
        prop.lock.leaseTimeSeconds.toLong(),
        TimeUnit.SECONDS
    ).asFlow()

    private fun RPermitExpirableSemaphoreReactive.releaseFlow(lockId: String): Flow<Void> =
        this.release(lockId).asFlow()

    @Synchronized
    private fun getSemaphore(key: String): RPermitExpirableSemaphoreReactive {
        return redissonReactiveClient.getPermitExpirableSemaphore("${LOCKS_HASH_NAME}_$key")
    }

    fun locked(key: String): Flow<Boolean> {
        val semaphore = getSemaphore(key)
        return semaphore
            .isExists.asFlow()
            .flatMapConcat { exists ->
                if (!exists) {
                    flowOf(false)
                } else {
                    semaphore.availablePermits().asFlow().map { it < 1 }
                }
            }
    }

    fun unlock(key: String): Flow<Void> {
        val semaphore = getSemaphore(key)
        return semaphore
            .isExists.asFlow()
            .flatMapConcat { exists ->
                if (exists) {
                    semaphore.delete().asFlow()
                        .flatMapConcat { deleted ->
                            if (!deleted) {
                                throw IllegalStateException("Could not delete semaphore for key: $key")
                            } else {
                                logger.info("Semaphore for key $key deleted")
                                flowOf()
                            }
                        }
                } else {
                    logger.info("Semaphore for key $key does not exist")
                    flowOf()
                }
            }
    }

    private fun <T, R> Flow<T>.then(flow: suspend () -> Flow<R>): Flow<R> {
        return this.flatMapConcat { flow() }
    }
}