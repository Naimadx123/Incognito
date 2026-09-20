package zone.vao.incognito.packet

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal object NativeReflection {

    private val fields = ConcurrentHashMap<Class<*>, List<Field>>()

    fun fields(type: Class<*>): List<Field> = fields.computeIfAbsent(type) {
        generateSequence(type) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .onEach { it.isAccessible = true }.toList()
    }

    fun record(value: Any, transform: (String, Any?) -> Any?): Any {
        val parts = value.javaClass.recordComponents
        val values = parts.map { part ->
            val accessor = part.accessor.apply { isAccessible = true }
            transform(part.name, accessor.invoke(value))
        }.toTypedArray()
        val constructor = value.javaClass.getDeclaredConstructor(*parts.map { it.type }.toTypedArray())
        constructor.isAccessible = true
        return constructor.newInstance(*values)
    }
}
