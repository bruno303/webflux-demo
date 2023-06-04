package com.bso.webfluxdemo.crosscutting.log

import org.slf4j.LoggerFactory

inline fun <reified T> T.logger() = lazy { LoggerFactory.getLogger(T::class.java) }