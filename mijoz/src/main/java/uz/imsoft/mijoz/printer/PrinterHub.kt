package uz.imsoft.mijoz.printer

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrinterState(
    val running: Boolean = false,
    val ready: UsbPrinter.Ready = UsbPrinter.Ready.NO_DEVICE,
    /** Bulutdagi navbatda kutayotgan cheklar (-1 — noma'lum). */
    val queue: Int = -1,
    val online: Boolean = false,
    val lastPrintAt: Long = 0L,
    val printedTotal: Int = 0,
    /** Chop etish paytida jarayon to'xtagan chek — chiqqan-chiqmagani noma'lum. */
    val unknownKey: String = "",
    val lastError: String = "",
    val log: List<String> = emptyList(),
)

/** Servis va ekran o'rtasidagi holat va buyruqlar (bitta jarayon ichida). */
object PrinterHub {
    private val _state = MutableStateFlow(PrinterState())
    val state: StateFlow<PrinterState> = _state.asStateFlow()

    /** Servisni uyg'otish (printer ulandi, ruxsat berildi, "Hozir tekshir"). */
    val wake = Channel<Unit>(Channel.CONFLATED)

    sealed interface Command {
        data object ReprintUnknown : Command
        data object DropUnknown : Command
        data object TestPage : Command
    }
    val commands = Channel<Command>(Channel.UNLIMITED)

    fun update(block: (PrinterState) -> PrinterState) = _state.update(block)

    fun log(line: String) {
        val t = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())
        _state.update { it.copy(log = (listOf("$t  $line") + it.log).take(80)) }
    }

    fun nudge() { wake.trySend(Unit) }
}
