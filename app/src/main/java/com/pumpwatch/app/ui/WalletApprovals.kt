package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigInteger

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VCard = Color(0xFF1A2230)

data class BlockscoutTokenApproval(
    @SerializedName("contract_address") val contractAddress: String?,
    @SerializedName("token_symbol") val tokenSymbol: String?,
    @SerializedName("spender_address") val spenderAddress: String?,
    @SerializedName("value") val value: String?,
    @SerializedName("expiration") val expiration: String?
)

data class BlockscoutApprovalsResponse(
    val result: List<BlockscoutTokenApproval>?,
    val message: String?,
    val status: String?
)

interface BlockscoutApprovalsApi {
    @GET("api")
    suspend fun getTokenApprovals(
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokenapprovals",
        @Query("address") address: String
    ): BlockscoutApprovalsResponse
}

private object BlockscoutApprovalsClient {
    private val apiCache = mutableMapOf<String, BlockscoutApprovalsApi>()

    fun api(baseUrl: String): BlockscoutApprovalsApi {
        return apiCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BlockscoutApprovalsApi::class.java)
        }
    }
}

internal fun isUnlimitedAllowance(allowance: String?): Boolean {
    if (allowance == null) return false
    return try {
        val value = allowance.toBigIntegerOrNull() ?: return false
        value >= BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935")
    } catch (_: Exception) {
        false
    }
}

internal fun formatAllowance(allowance: String?): String {
    if (allowance == null) return "نامشخص"
    return if (isUnlimitedAllowance(allowance)) {
        "∞ نامحدود"
    } else {
        try {
            val value = allowance.toBigIntegerOrNull() ?: return allowance
            if (value > BigInteger("1000000000000000000")) {
                "${value / BigInteger("1000000000000000000")} ETH"
            } else {
                value.toString()
            }
        } catch (_: Exception) {
            allowance
        }
    }
}

@Composable
internal fun ApprovalsTab(
    address: String,
    chain: ChainCfg,
    onInfo: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var approvals by remember { mutableStateOf<List<BlockscoutTokenApproval>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            approvals = emptyList()
            try {
                val result = withContext(Dispatchers.IO) {
                    val bsUrl = chain.bs ?: throw Exception("Blockscout URL برای این شبکه موجود نیست")
                    BlockscoutApprovalsClient.api(bsUrl).getTokenApprovals(address = address)
                }
                approvals = result.result ?: emptyList()
                if (approvals.isEmpty()) {
                    onInfo("✅ هیچ Approval فعالی یافت نشد")
                } else {
                    val unlimitedCount = approvals.count { isUnlimitedAllowance(it.value) }
                    onInfo("✅ ${approvals.size} Approval یافت شد ($unlimitedCount نامحدود ⚠️)")
                }
            } catch (e: Exception) {
                error = "خطا در خواندن Approvals: ${e.message}"
            }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "🔐 مدیریت Token Approvals",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = VBlue
                    )
                    Text(
                        "Approvals دسترسی‌هایی هستند که به قراردادهای هوشمند داده‌اید. اگر allowance نامحدود باشد، قرارداد می‌تواند هر مقدار از توکن شما را برداشت کند.",
                        fontSize = 10.sp,
                        color = VGray
                    )

                    if (address.isEmpty()) {
                        Text(
                            "⚠️ ابتدا آدرس کیف پول را وارد کنید",
                            fontSize = 11.sp,
                            color = VGold
                        )
                    } else {
                        Button(
                            onClick = { load() },
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = VBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(14.dp).height(14.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            }
                            Text("🔍 بررسی Approvals", fontSize = 12.sp)
                        }
                    }

                    if (error != null) {
                        Text(error ?: "", fontSize = 10.sp, color = VRed)
                    }
                }
            }
        }

        if (approvals.isNotEmpty()) {
            val unlimitedCount = approvals.count { isUnlimitedAllowance(it.value) }

            if (unlimitedCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1F1F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "⚠️ هشدار امنیتی",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = VRed
                            )
                            Text(
                                "$unlimitedCount Approval نامحدود یافت شد! این یعنی قرارداد می‌تواند هر مقدار از توکن شما را برداشت کند.",
                                fontSize = 10.sp,
                                color = VRed
                            )
                        }
                    }
                }
            }

            items(approvals) { approval ->
                val isUnlimited = isUnlimitedAllowance(approval.value)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlimited) Color(0xFF3D1F1F) else VCard
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isUnlimited) "⚠️" else "✅", fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    approval.tokenSymbol ?: "Unknown Token",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    "مجاز به: ${shortAddr(approval.spenderAddress ?: "")}",
                                    fontSize = 9.sp,
                                    color = VGray
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Allowance:", fontSize = 9.sp, color = VGray)
                            Text(
                                formatAllowance(approval.value),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlimited) VRed else VGreen
                            )
                        }

                        Button(
                            onClick = {
                                val revokeUrl = "https://revoke.cash/address/$address"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(revokeUrl))
                                    context.startActivity(intent)
                                    onInfo("🔐 برای لغو دسترسی، به Revoke.cash هدایت شدید")
                                } catch (_: Exception) {
                                    onInfo("⚠️ نمی‌توان مرورگر را باز کرد")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VRed.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "🔐 لغو دسترسی (Revoke)",
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        } else if (!loading && address.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VCard),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 32.sp)
                        Text(
                            "برای مشاهده Approvals، دکمه بالا را بزنید",
                            fontSize = 11.sp,
                            color = VGray
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VCard),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "💡 راهنما",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = VGold
                    )
                    Text(
                        "• Approvals نامحدود خطرناک هستند و می‌توانند منجر به سرقت توکن‌های شما شوند\n" +
                            "• برای لغو دسترسی، روی دکمه Revoke کلیک کنید تا به Revoke.cash هدایت شوید\n" +
                            "• فقط به قراردادهای معتبر دسترسی بدهید",
                        fontSize = 9.sp,
                        color = VGray
                    )
                }
            }
        }
    }
}
