package com.bso.webfluxdemo.crosscutting.mono

import reactor.util.context.ContextView

fun ContextView.getValue(key: String): String? {
    val opt = this.getOrEmpty<String>(key)
    return if (opt.isPresent) opt.get() else null
}