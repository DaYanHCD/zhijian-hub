package com.fingertipfuture.hub.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParallelTest {

    @Test
    fun `merge keeps a seed result from every provider`() {
        val deepSeek = SearchProvider(
            id = "deepseek",
            kind = SearchProvider.KIND_DEEPSEEK,
            name = "DeepSeek",
        )
        val bing = SearchProvider(
            id = "bing",
            kind = SearchProvider.KIND_BING,
            name = "Bing",
        )
        val results = buildList {
            repeat(10) { index ->
                add(
                    deepSeek to SearchApi.Hit(
                        title = "DeepSeek $index",
                        url = "https://deepseek.example/$index",
                        score = 10.0 - index,
                    ),
                )
            }
            add(
                bing to SearchApi.Hit(
                    title = "Bing result",
                    url = "https://bing.example/result",
                ),
            )
        }

        val merged = SearchContext.mergeHits(results, 5)
        assertEquals(5, merged.size)
        assertTrue("Bing 至少应保留一条种子结果", merged.any { it.first.kind == SearchProvider.KIND_BING })
    }

    @Test
    fun `two search providers are in flight at the same time`() = runBlocking {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val providers = listOf(
            SearchProvider(id = "parallel-a", kind = SearchProvider.KIND_BING, name = "A"),
            SearchProvider(id = "parallel-b", kind = SearchProvider.KIND_DEEPSEEK, name = "B"),
        )

        val job = async(Dispatchers.Default) {
            SearchContext.searchAll(providers, "parallel") { provider, _ ->
                started.countDown()
                // 两个搜索协程都进到这个函数才放行；串行实现会让第二个永远到不了。
                release.await(2, TimeUnit.SECONDS)
                Result.success(
                    listOf(
                        SearchApi.Hit(
                            title = provider.name,
                            url = "https://example.com/${provider.id}",
                        ),
                    ),
                )
            }
        }

        assertTrue("两个搜索应同时开始", started.await(1, TimeUnit.SECONDS))
        release.countDown()

        val hits = job.await()
        assertEquals(2, hits.size)
        assertEquals(setOf("A", "B"), hits.map { it.first.name }.toSet())
    }
}
