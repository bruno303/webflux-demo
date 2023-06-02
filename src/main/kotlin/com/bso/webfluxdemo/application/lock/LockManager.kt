package com.bso.webfluxdemo.application.lock

import reactor.core.publisher.Mono

interface LockManager {
    fun <T: Any> runWithLock(key: String, action: () -> Mono<T>): Mono<T>
    fun unlock(key: String): Mono<Void>
}

interface Lock {
    fun tryLock(): Mono<Boolean>
    fun unlock(): Mono<Void>
    fun forceUnlock(): Mono<Boolean>
}