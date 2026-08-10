# Keep enough metadata for useful deobfuscated crash reports and libraries that
# inspect generic signatures or annotations. Library-specific consumer rules are
# supplied by Compose, Ktor, Room, Firebase, and the other dependencies.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-renamesourcefileattribute SourceFile
