package dev.chungjungsoo.gptmobile.util

/**
 Small implementation of HashMap, but with default values.
 This way the get operator will not throw an error or null.
 Inspired by Python collections DefaultDict.
 */
open class DefaultHashMap<K, V>(protected val defaultValueProvider: () -> V) : HashMap<K, V>() {
    override operator fun get(key: K): V {
        if (key in this) {
            return super.get(key)!!
        }

        val defaultValue = defaultValueProvider()
        this[key] = defaultValue
        return super.get(key)!!
    }
}
