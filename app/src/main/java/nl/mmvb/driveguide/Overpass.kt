package nl.mmvb.driveguide

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

data class OverpassResponse(val elements: List<Element>)
data class Element(val tags: Tags)
@JsonClass(generateAdapter = true)
data class Tags(
    @Json(name = "maxspeed") val maxspeed: String?,
    @Json(name = "maxspeed:conditional") val maxspeed_conditional: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "official_name") val official_name: String?,
    @Json(name = "int_name") val int_name: String?,
    @Json(name = "ref") val ref: String?,
)

interface OverpassService {
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun getSpeedLimit(
        @Field("data") query: String
    ): OverpassResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://overpass-api.de/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().
        setLevel(HttpLoggingInterceptor.Level.BODY)

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build();

    val instance: OverpassService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(OverpassService::class.java)
    }
}