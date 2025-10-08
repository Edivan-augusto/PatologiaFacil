plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // KAPT será removido do módulo app ao migrarmos para KSP
    id("org.jetbrains.kotlin.kapt") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply false
}
