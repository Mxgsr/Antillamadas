# Reglas de optimización R8 para Antillamadas
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Conservar clases @Serializable para respaldo e historial JSON
-keepclassmembers class cl.antillamadas.app.** {
    *** Companion;
}
-keepclasseswithmembers class cl.antillamadas.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
