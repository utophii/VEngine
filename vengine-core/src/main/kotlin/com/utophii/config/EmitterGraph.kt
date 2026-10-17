package com.utophii.config

// effects that spawn themselves through emitters: / then:, directly or through other effects,
// would multiply without end
internal object EmitterGraph {

    // names that can reach themselves through the edges
    fun cyclic(edges: Map<String, List<String>>): Set<String> =
        edges.keys.filterTo(linkedSetOf()) { start -> reaches(edges, start, start) }

    private fun reaches(edges: Map<String, List<String>>, from: String, target: String): Boolean {
        val seen = mutableSetOf<String>()
        val pending = ArrayDeque(edges[from].orEmpty())
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next == target) {
                return true
            }
            if (seen.add(next)) {
                pending.addAll(edges[next].orEmpty())
            }
        }
        return false
    }
}
