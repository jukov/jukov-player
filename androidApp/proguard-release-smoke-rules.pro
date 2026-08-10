# These classes are referenced by the AndroidX instrumentation runner, not by
# production code. Keep them only in the local release smoke-test artifact.
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
