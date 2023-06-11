package com.bso.webfluxdemo.infra.web.filters

import com.bso.webfluxdemo.crosscutting.log.logger
import org.slf4j.Logger
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class IdempotencyIdWebFilter : WebFilter {
    private val logger: Logger by logger()

    companion object {
        const val IDEMPOTENCY_HEADER = "Idempotency-Id"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val idempotencyId = exchange.request.headers.getFirst(IDEMPOTENCY_HEADER)
            ?: UUID.randomUUID().toString()

        logger.info("Running IdempotencyIdWebFilter and putting id {}", idempotencyId)
        exchange.request.mutate()
            .headers {
                it.putIfAbsent(IDEMPOTENCY_HEADER, listOf(idempotencyId))
            }

        return chain.filter(exchange)
                .contextWrite { ctx -> ctx.put(IDEMPOTENCY_HEADER, idempotencyId) }
    }
}