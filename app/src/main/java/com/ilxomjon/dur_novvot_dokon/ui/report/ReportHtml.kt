package com.ilxomjon.dur_novvot_dokon.ui.report

/**
 * Serverdan kelgan hisobot HTML'ini zamonaviy ko'rinishga keltiradi: toza jadval, rangli sarlavha,
 * navbatma-navbat qatorlar, yorug'/qorong'i mavzu. Server HTML'idagi eski ranglar CSS bilan bosib o'tiladi.
 */
object ReportHtml {
    fun wrap(body: String, dark: Boolean): String {
        val bg = if (dark) "#0E1514" else "#F4FBF9"
        val fg = if (dark) "#DDE4E1" else "#161D1C"
        val primary = if (dark) "#53DBC9" else "#006A60"
        val onPrimary = if (dark) "#003731" else "#FFFFFF"
        val zebra = if (dark) "#16201E" else "#E7F1EE"
        val border = if (dark) "#2E3A37" else "#CBD8D4"
        val headBg = if (dark) "#1F3B37" else "#CDEDE7"
        val headFg = if (dark) "#BFF2E9" else "#00201C"
        val css = """
            * { box-sizing: border-box; }
            /* Server HTML'idagi har qanday rang (oq fon, och kulrang matn) — bitta mavzuga keltiriladi */
            body *, font { background-color: transparent !important; background-image: none !important;
                           color: $fg !important; border-color: $border !important; }
            html { -webkit-text-size-adjust: 100%; }
            body { margin: 0; padding: 12px; background: $bg !important; color: $fg !important;
                   font-family: Roboto, Arial, sans-serif; font-size: 14px; line-height: 1.45; }
            .wrap { overflow-x: auto; }
            h1, h2, h3, h4 { color: $primary !important; margin: 14px 0 6px; font-weight: 600; }
            table { width: auto !important; min-width: 100%; border-collapse: separate !important; border-spacing: 0 !important;
                    margin: 8px 0 !important; background: transparent !important; font-size: 13px;
                    border: 1px solid $border; border-radius: 10px; overflow: hidden; }
            th { background: $primary !important; color: $onPrimary !important; font-weight: 600;
                 text-align: left; padding: 9px 10px !important; }
            td { padding: 8px 10px !important; border-bottom: 1px solid $border !important; }
            /* Summalar bo'linib ketmasin ("10 532 / 567") */
            td[align="right"], td[align="RIGHT"], td[style*="right"] { white-space: nowrap !important; }
            tr:nth-child(even):not([bgcolor]):not([style*="background"]) > td:not([bgcolor]):not([style*="background"]) { background-color: $zebra !important; }
            /* Serverda rangli (sarlavha/jami) qatorlar — bir xil ajratilgan uslub */
            td[bgcolor], td[style*="background"], tr[bgcolor] > td, tr[style*="background"] > td {
                 background-color: $headBg !important; color: $headFg !important; font-weight: 600; }
            td[bgcolor] *, td[style*="background"] *, tr[bgcolor] > td *, tr[style*="background"] > td * { color: $headFg !important; }
            tr:last-child td { border-bottom: none !important; }
            b, strong { font-weight: 700; }
            hr { border: 0; border-top: 1px solid $border; margin: 12px 0; }
            a { color: $primary !important; }
        """.trimIndent()
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>" + css + "</style></head><body><div class=\"wrap\">" + body + "</div></body></html>"
    }
}
