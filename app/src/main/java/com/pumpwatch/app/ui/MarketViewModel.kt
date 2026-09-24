package com.pumpwatch.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.MarketMeta
import com.pumpwatch.app.data.NetErr
import com.pumpwatch.app.data.ServedFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    private val _coins = MutableStateFlow<List<CoinMarket>>(emptyList())
    val coins: StateFlow<List<CoinMarket>> = _coins.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    private val _meta = MutableStateFlow(MarketMeta(0L, ServedFrom.UNKNOWN, 0))
    val meta: StateFlow<MarketMeta> = _meta.asStateFlow()

    private val _platformMap = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val platformMap: StateFlow<Map<String, Map<String, String>>> = _platformMap.asStateFlow()

    init {
        fetchData()
    }

    fun fetchData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                val quickCoins = ApiClient.getQuickCoins()
                _coins.value = quickCoins
                
                _meta.value = ApiClient.marketMeta()
                _platformMap.value = try { ApiClient.getPlatformMap() } catch (_: Exception) { emptyMap() }

                launch {
                    try {
                        val fullCoins = ApiClient.getTop1000Coins()
                        if (fullCoins.isNotEmpty()) {
                            _coins.value = fullCoins
                        }
                    } catch (e: Exception) {
                        NetErr.log("MarketViewModel", "coingecko/coins/markets full", null, e)
                    }
                }
            } catch (e: Exception) {
                NetErr.log("MarketViewModel", "coingecko/coins/markets quick", null, e)
                _errorMsg.value = NetErr.msg(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refresh() {
        fetchData()
    }
}
