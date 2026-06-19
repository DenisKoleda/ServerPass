$ErrorActionPreference = "Stop"

$javaHome = "C:\Users\denis\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon"
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Push-Location (Resolve-Path "$PSScriptRoot\..")
try {
    & .\gradlew.bat @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
