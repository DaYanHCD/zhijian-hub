package com.fingertipfuture.hub.ui.provider

import androidx.annotation.DrawableRes
import com.fingertipfuture.hub.R

/**
 * 厂商 Logo。
 *
 * `HubProvider.logo` 里存的是名字（不是资源 id，存进 JSON 才稳定），这里翻成打包进来的
 * VectorDrawable —— 全部来自 LobeHub icons（MIT），是各家**真实的厂商标志**。
 *
 * [BrandIcon.colored]：这家的标志自带颜色（官方彩色版）→ 画的时候**不要**再 tint；
 * 官方本来就是黑白单色的（OpenAI、Kimi、Groq、xAI、Ollama 就是这种）→ 用前景色 tint 一下。
 */
internal data class BrandIcon(@DrawableRes val res: Int, val colored: Boolean)

internal fun brandIcon(logo: String): BrandIcon? = when (logo) {
    // 官方就是单色的这五家
    "openai" -> BrandIcon(R.drawable.ic_brand_openai, false)
    "moonshot" -> BrandIcon(R.drawable.ic_brand_moonshot, false)
    "groq" -> BrandIcon(R.drawable.ic_brand_groq, false)
    "xai" -> BrandIcon(R.drawable.ic_brand_xai, false)
    "ollama" -> BrandIcon(R.drawable.ic_brand_ollama, false)
    // 官方彩色版
    "gemini" -> BrandIcon(R.drawable.ic_brand_gemini, true)
    "deepseek" -> BrandIcon(R.drawable.ic_brand_deepseek, true)
    "zhipu" -> BrandIcon(R.drawable.ic_brand_zhipu, true)
    "qwen" -> BrandIcon(R.drawable.ic_brand_qwen, true)
    "siliconcloud" -> BrandIcon(R.drawable.ic_brand_siliconcloud, true)
    "minimax" -> BrandIcon(R.drawable.ic_brand_minimax, true)
    "hunyuan" -> BrandIcon(R.drawable.ic_brand_hunyuan, true)
    "stepfun" -> BrandIcon(R.drawable.ic_brand_stepfun, true)
    "volcengine" -> BrandIcon(R.drawable.ic_brand_volcengine, true)
    "spark" -> BrandIcon(R.drawable.ic_brand_spark, true)
    "baidu" -> BrandIcon(R.drawable.ic_brand_baidu, true)
    "openrouter" -> BrandIcon(R.drawable.ic_brand_openrouter, true)
    "mistral" -> BrandIcon(R.drawable.ic_brand_mistral, true)
    "together" -> BrandIcon(R.drawable.ic_brand_together, true)
    "fireworks" -> BrandIcon(R.drawable.ic_brand_fireworks, true)
    "huggingface" -> BrandIcon(R.drawable.ic_brand_huggingface, true)
    "perplexity" -> BrandIcon(R.drawable.ic_brand_perplexity, true)
    "aihubmix" -> BrandIcon(R.drawable.ic_brand_aihubmix, true)
    "cerebras" -> BrandIcon(R.drawable.ic_brand_cerebras, true)
    "nvidia" -> BrandIcon(R.drawable.ic_brand_nvidia, true)
    else -> null
}