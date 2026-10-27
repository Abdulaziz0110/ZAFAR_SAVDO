# jTDS (SQL Server) Class.forName bilan yuklanadi va reflection ishlatadi
-keep class net.sourceforge.jtds.** { *; }
-dontwarn net.sourceforge.jtds.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
# Tink (androidx.security.crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keep class com.google.crypto.tink.** { *; }
