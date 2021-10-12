package io.kinference.core.graph

class GraphValueOrderInfo {
    private val orders: HashMap<String, Int> = HashMap()

    fun putOrder(name: String, order: Int) {
        if (!orders.containsKey(name) || orders[name]!! < order)
            orders[name] = order
    }

    fun putOrder(names: Collection<String>, order: Int) {
        for (name in names) {
            putOrder(name, order)
        }
    }

    fun getOrder(name: String): Int {
        return orders.getOrElse(name) { Int.MAX_VALUE }
    }
}
