package com.pumpwatch.app.ui

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class WalletViewModel : ViewModel() {
    
    // 🚀 Commit 100: Stateهای اصلی که باید بین tab switchها حفظ شوند
    var address by mutableStateOf("")
        private set
    
    var chain by mutableStateOf(CHAINS[0])
        private set
    
    var holdings by mutableStateOf<List<WalletHolding>>(emptyList())
        private set
    
    var txs by mutableStateOf<List<WalletTx>>(emptyList())
        private set
    
    var total by mutableStateOf(0.0)
        private set
    
    var loading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var info by mutableStateOf("")
        private set
    
    // توابع setter برای به‌روزرسانی state از UI
    fun setAddress(value: String) { address = value }
    fun setChain(value: ChainCfg) { chain = value }
    fun setHoldings(value: List<WalletHolding>) { holdings = value }
    fun setTxs(value: List<WalletTx>) { txs = value }
    fun setTotal(value: Double) { total = value }
    fun setLoading(value: Boolean) { loading = value }
    fun setError(value: String?) { error = value }
    fun setInfo(value: String) { info = value }
    
    // پاک کردن نتایج قبلی قبل از اسکن جدید
    fun clearResults() {
        holdings = emptyList()
        txs = emptyList()
        total = 0.0
        error = null
    }
}
