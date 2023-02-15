package com.bso.webfluxdemo.service

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class ExternalService {
    fun getExternalIdFromExternalService(): Mono<String> =
        Mono.just(UUID.randomUUID().toString())
}