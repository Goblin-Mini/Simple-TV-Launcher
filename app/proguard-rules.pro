# Сжатие имён: все классы в одном пакете, журнал не нужен в релизе.
-repackageclasses ''
-assumenosideeffects class android.util.Log {
    public static *** w(...);
}
