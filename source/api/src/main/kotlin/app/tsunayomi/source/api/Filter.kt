package app.tsunayomi.source.api

/** Search filters a source exposes. The UI renders them generically by type. */
sealed class Filter<T>(val name: String, var state: T) {
    class Header(name: String) : Filter<Unit>(name, Unit)

    class Text(name: String, state: String = "") : Filter<String>(name, state)

    class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    /** Include / exclude / ignore, as used for genre tags. */
    class TriState(name: String, state: Value = Value.Ignore) : Filter<TriState.Value>(name, state) {
        enum class Value { Ignore, Include, Exclude }
    }

    class Select(name: String, val values: List<String>, state: Int = 0) : Filter<Int>(name, state)

    class Sort(name: String, val values: List<String>, state: Selection? = null) :
        Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }

    class Group<F : Filter<*>>(name: String, filters: List<F>) : Filter<List<F>>(name, filters)
}

class FilterList(filters: List<Filter<*>> = emptyList()) : List<Filter<*>> by filters {
    constructor(vararg filters: Filter<*>) : this(filters.toList())
}
