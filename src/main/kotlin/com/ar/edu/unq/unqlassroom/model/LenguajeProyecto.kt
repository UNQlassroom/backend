package com.ar.edu.unq.unqlassroom.model

enum class LenguajeProyecto(val identificador: String, val nombreLegible: String) {
    JAVA_MAVEN("java_maven", "Java (Maven)"),
    JAVA_GRADLE("java_gradle", "Java (Gradle)"),
    PYTHON("python", "Python"),
    NODE_JS("node_js", "JavaScript / TypeScript (Node.js)"),
    DOTNET("dotnet", "C# / .NET"),
    GENERICO("generico", "Genérico / Otro");

    val esJava: Boolean
        get() = this == JAVA_MAVEN || this == JAVA_GRADLE
}

