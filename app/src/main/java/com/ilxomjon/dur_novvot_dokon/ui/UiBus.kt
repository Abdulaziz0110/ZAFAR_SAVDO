package com.ilxomjon.dur_novvot_dokon.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Ekranlar orasida bir martalik so'rovlar (masalan: do'kon kartasidan "To'lov qabul qil"). */
object UiBus {
    /** To'lov ekrani ochilganda shu do'kon tanlansin (keyin null qilinadi). */
    val paymentStore = MutableStateFlow<String?>(null)
}
