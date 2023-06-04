package com.bso.webfluxdemo.infra.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "app")
data class AppConfigurationProperties @ConstructorBinding constructor (
    val lock: LockProperties
)

data class LockProperties (
    val waitTimeSeconds: Int,
    val leaseTimeSeconds: Int
)