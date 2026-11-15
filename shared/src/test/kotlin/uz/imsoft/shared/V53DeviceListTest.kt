package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.3: bitta telefon — bitta qator (qayta o'rnatishdan qolgan eski yozuvlar yashiriladi). */
class V53DeviceListTest {
    private fun dev(id: String, model: String, seen: Long, agent: String = "", blocked: Boolean = false, role: String = DeviceCodes.ROLE_AGENT) =
        DeviceInfo(
            id, model, role, "5.3", 42, seen,
            if (agent.isEmpty() && !blocked) null else DeviceAssignment(if (blocked) DeviceAssignment.BLOCKED else DeviceAssignment.OK, if (agent.isEmpty()) emptyList() else listOf(agent, "Ism"), "", "", 0),
            0, "",
        )

    @Test fun reinstallsCollapseToNewest() {
        val t = DeviceList.tidy(listOf(dev("a", "Redmi Note 12", 10), dev("b", "Redmi Note 12", 30), dev("c", "Redmi Note 12", 20)))
        assertEquals(listOf("b"), t.shown.map { it.id })
        assertEquals(setOf("a", "c"), t.stale.map { it.id }.toSet())
        assertEquals(2, t.extra["b"])
    }

    @Test fun sameModelDifferentPeopleStaySeparate() {
        val t = DeviceList.tidy(listOf(dev("a", "Samsung A14", 10, agent = "1"), dev("b", "Samsung A14", 30, agent = "2")))
        assertEquals(2, t.shown.size)
        assertTrue(t.stale.isEmpty())
    }

    @Test fun oldAssignmentOfSamePersonIsStale() {
        val t = DeviceList.tidy(listOf(dev("old", "Samsung A14", 10, agent = "1"), dev("new", "Samsung A14", 30, agent = "1")))
        assertEquals(listOf("new"), t.shown.map { it.id })
    }

    @Test fun blockedIsNeverHidden() {
        val t = DeviceList.tidy(listOf(dev("x", "Poco", 10, blocked = true), dev("y", "Poco", 30)))
        assertEquals(setOf("x", "y"), t.shown.map { it.id }.toSet())
    }

    @Test fun differentRolesAndNewestFirst() {
        val t = DeviceList.tidy(listOf(dev("a", "Poco", 10), dev("m", "Poco", 50, role = DeviceCodes.ROLE_MIJOZ), dev("z", "Vivo", 99)))
        assertEquals(listOf("z", "m", "a"), t.shown.map { it.id })
    }
}
