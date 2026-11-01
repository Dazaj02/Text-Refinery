# Genera el paquete de Windows con jpackage.
#   -Type exe|msi   instalador (requiere WiX Toolset 3.x)
#   -Type app-image carpeta ejecutable sin instalar (no requiere WiX)
#   -SkipBuild      reutiliza target\ en lugar de llamar a Maven
# Requiere JDK 17+ (jpackage); Maven solo si no se usa -SkipBuild.
param(
    [string]$Type = "exe",
    [switch]$SkipBuild
)

# "Continue": PowerShell 5.1 trata como error cualquier aviso que un programa nativo escribe en stderr;
# el resultado real se comprueba con $LASTEXITCODE.
$ErrorActionPreference = "Continue"
Set-Location (Join-Path $PSScriptRoot "..")

if (-not $SkipBuild) {
    mvn -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Falló el build de Maven" }
}

# jpackage solo admite versiones numéricas (0.1.0, no 0.1.0-SNAPSHOT)
$version = ([xml](Get-Content pom.xml)).project.version -replace "-SNAPSHOT", ""
$jar = Get-ChildItem target\text-refinery-*.jar | Select-Object -First 1
if (-not $jar) { throw "No existe el jar en target\. Ejecuta mvn package primero (o quita -SkipBuild)." }

# jpackage exige que el jar principal esté dentro de --input
$staging = "target\jpackage-input"
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $staging | Out-Null
Copy-Item "target\libs\*" $staging
Copy-Item $jar.FullName $staging

$args = @(
    "--type", $Type,
    "--name", "Text Refinery",
    "--app-version", $version,
    # Windows muestra FileDescription como nombre del programa (Administrador de tareas, avisos): debe ser corto y ser la marca.
    "--description", "Text Refinery",
    "--copyright", "Text Refinery",
    "--vendor", "Text Refinery",
    "--input", $staging,
    "--main-jar", $jar.Name,
    "--main-class", "com.textrefinery.App",
    "--add-modules", "java.se,jdk.unsupported",
    "--icon", "src\main\resources\icons\app.ico",
    "--dest", "dist"
)
if ($Type -ne "app-image") {
    $args += @("--win-menu", "--win-shortcut", "--win-dir-chooser")
}

jpackage @args
if ($LASTEXITCODE -ne 0) { throw "jpackage falló" }
Write-Host "Listo: carpeta dist\"
