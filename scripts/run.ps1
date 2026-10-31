# Ejecuta Text Refinery desde el paquete nativo (Windows).
#   Con "java -jar" el Administrador de tareas y el sistema muestran "OpenJDK Platform binary";
#   el paquete se llama "Text Refinery" porque su ejecutable lleva ese nombre.
#   -Rebuild   vuelve a generar el paquete aunque ya exista
#   -Settings  abre la ventana de ajustes al arrancar
# Si el paquete no existe (o el jar es más nuevo), lo genera antes con scripts\package.ps1 -Type app-image.
# Tras cambiar el código ejecuta antes "mvn package" para que el jar esté al día.
param(
    [switch]$Rebuild,
    [switch]$Settings
)

$ErrorActionPreference = "Continue"
$root = Join-Path $PSScriptRoot ".."
Set-Location $root

$exe = "dist\Text Refinery\Text Refinery.exe"
$jar = Get-ChildItem "target\text-refinery-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
$stale = (Test-Path $exe) -and $jar -and ((Get-Item $exe).LastWriteTime -lt $jar.LastWriteTime)

if ($Rebuild -or $stale -or -not (Test-Path $exe)) {
    Write-Host "Generando el paquete (la primera vez tarda un poco)..."
    Remove-Item "dist\Text Refinery" -Recurse -Force -ErrorAction SilentlyContinue
    # Con un jar ya compilado se reutiliza (-SkipBuild); si no hay, package.ps1 lo compila con Maven.
    if ($jar) { & (Join-Path $PSScriptRoot "package.ps1") -Type app-image -SkipBuild }
    else { & (Join-Path $PSScriptRoot "package.ps1") -Type app-image }
    if (-not (Test-Path $exe)) { throw "No se pudo generar $exe" }
}

$launchArgs = @()
if ($Settings) { $launchArgs += "--settings" }
Start-Process (Resolve-Path $exe).Path -ArgumentList $launchArgs
Write-Host "Text Refinery iniciado (icono en la bandeja del sistema)."
