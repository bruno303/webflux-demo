package com.bso.webfluxdemo.infra.lock.flow

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@FlowPreview
class RedisRSemaphoreReactiveManagerFlowImplTest {
    @Autowired
    private lateinit var subject: RedisRSemaphoreReactiveManagerFlowImpl

    companion object {
        private const val KEY = "key"
    }

    @BeforeEach
    fun setup(): Unit = runBlocking {
        subject.unlock(KEY).collect()
    }

    @Test
    @DisplayName("should throw if key is already locked by the same flow")
    fun throwIfLockedEvenInSameBlock(): Unit = runBlocking {
        val flow: Flow<String> = subject.runWithLock(KEY) {
            subject.runWithLock(KEY) {
                flowOf("xpto")
            }
        }
        val ex = assertThrows<IllegalStateException> {
            flow.collect()
        }
        assertEquals("Can't acquire lock for key key", ex.message)
        verifyLocked(false)
    }

    @Test
    @DisplayName("should execute with success if key is not locked")
    fun lockWithSuccess(): Unit = runBlocking {
        val flow = subject.runWithLock(KEY) {
            println("Returning xpto")
            flowOf("xpto")
        }
        flow.collect {
            assertEquals("xpto", it)
        }
        verifyLocked(false)
    }

    @Test
    @DisplayName("should lock, execute and release a few times with success")
    fun lockAndReleaseFewTimes(): Unit = runBlocking {
        val flows: List<Flow<String>> = (0..100).map {
            subject.runWithLock(KEY) {
                flowOf("Test number $it")
            }
        }
        flows.forEachIndexed { index, flow ->
            flow.collect {
                assertEquals("Test number $index", it)
            }
        }
        verifyLocked(false)
    }

    @Test
    @DisplayName("should release lock if get error in action")
    fun releaseAfterError(): Unit = runBlocking {
        val flow: Flow<String> = subject.runWithLock(KEY) {
            throw IllegalStateException("Test Error")
        }

        assertThrows<IllegalStateException> { flow.collect() }

//        try {
//            flow.collect { println(it) }
//        } catch (e: Throwable) {
//            println(e)
//            return@runBlocking
//        }
//        fail<Any>("")
        verifyLocked(false)
    }

    private fun verifyLocked(locked: Boolean): Unit = runBlocking {
        assertEquals(locked, subject.locked(KEY).single())
    }
}