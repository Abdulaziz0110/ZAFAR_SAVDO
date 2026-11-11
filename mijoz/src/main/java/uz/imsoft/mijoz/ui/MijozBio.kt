package uz.imsoft.mijoz.ui

import uz.imsoft.shared.bio.Bio
import uz.imsoft.shared.bio.BioBook

/**
 * v5.2 (A7): mahsulot ma'lumoti — do'kondan kelgan snapshot ichida keladi, telefonda saqlanadi,
 * INTERNETSIZ ham ko'rinadi. Matn bir marta ochiladi va keshlanadi.
 */
object MijozBio {
    private var packed: String = ""
    private var index: Map<String, Bio> = emptyMap()

    @Synchronized
    fun of(packedBio: String?, productId: String): Bio? {
        val t = packedBio.orEmpty()
        if (t != packed) {
            packed = t
            index = BioBook.byProduct(BioBook.unpack(t))
        }
        return index[productId]
    }
}
