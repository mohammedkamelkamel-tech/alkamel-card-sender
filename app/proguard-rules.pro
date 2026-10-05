-keep public class * extends android.app.Activity { public <init>(); }
-keep public class * extends android.content.BroadcastReceiver { public <init>(); }
-keep public class * extends android.app.Service { public <init>(); }
-keep public class * extends android.content.ContentProvider { public <init>(); }
-keepclassmembers class * implements android.os.Parcelable { public static ** CREATOR; }
-keepclassmembers class * implements java.io.Serializable { static final long serialVersionUID; }
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
