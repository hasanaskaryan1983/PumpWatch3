package com.pumpwatch.app.ui

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class WalletViewModel : ViewModel() {
    
    // 🚀 Commit 100: Stateهای اصلی که باید بین tab switchها حفظ شوند
    var address by mutableStateOf("")
    var chain by mutableStateOf(CHAINS[0])
    var holdings by mutableStateOf<List<WalletHolding>>(emptyList())
    var txs by mutableStateOf<List<WalletTx>>(emptyList())
    var total by mutableStateOf(0.0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf("")
    
    // پاک کردن نتایج قبلی قبل از اسکن جدید
    fun clearResults() {
        holdings = emptyList()
        txs = emptyList()
        total = 0.0
        error = null
    }
}
