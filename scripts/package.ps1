# Genera el instalador de Windows (.exe; usa -Type msi para .msi). Requiere JDK 21+, Maven y WiX Toolset 3.x.
param([string]$Type = "exe")

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

mvn -B -DskipTests package
$version = (mvn -q help:evaluate "-Dexpression=project.version" -DforceStdout).Replace("-SNAPSHOT", "")
$jar = Get-ChildItem target\text-refinery-*.jar | Select-Object -First 1

jpackage --type $Type `
    --name "Text Refinery" `
    --app-version $version `
    --input target\libs `
    --main-jar "..\$($jar.Name)" `
    --main-class com.textrefinery.App `
    --dest dist `
    --win-menu --win-shortcut --win-dir-chooser
