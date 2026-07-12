package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val LRCLIB_BASE_URL = "https://lrclib.net/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                // LRCLIB requests a descriptive User-Agent
                .header("User-Agent", "VerseForPoweramp/1.0 (albertpessima@gmail.com; Standalone Lyrics Companion)")
                .build()
            chain.proceed(request)
        }
        .build()

    val lrclibService: LrclibService by lazy {
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrclibService::class.java)
    }
}
