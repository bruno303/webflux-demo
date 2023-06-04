package com.bso.webfluxdemo.application.lock

import kotlinx.coroutines.flow.Flow
import reactor.core.publisher.Mono

interface WebfluxLockManager {
    fun <T: Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T>
    fun unlock(key: String): Mono<Void>
}

interface KotlinFlowLockManager {
    fun <T: Any> runWithLock(key: String, action: () -> Flow<T>): Flow<T>
}

interface Lock {
    fun tryLock(): Mono<Boolean>
    fun unlock(): Mono<Void>
    fun forceUnlock(): Mono<Boolean>
}