# ML Kit (via Firebase components) creates its registrars by reflection; R8 full mode drops their constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.**.*Registrar { <init>(); }
