param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$DependencyCache,
    [Parameter(Mandatory = $true)][string]$MinecraftJar,
    [Parameter(Mandatory = $true)][string]$MixinInput,
    [ValidateSet('fabric', 'neoforge')][string]$Platform
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot/../../../..").Path
$classes = Join-Path $projectRoot 'build/classes/camera-weaving'
$dependencies = @(
    'net.fabricmc/sponge-mixin/0.17.3+mixin.0.8.7',
    'org.ow2.asm/asm/9.10.1',
    'org.ow2.asm/asm-tree/9.10.1',
    'org.ow2.asm/asm-commons/9.10.1',
    'org.ow2.asm/asm-util/9.10.1',
    'org.ow2.asm/asm-analysis/9.10.1',
    'com.google.code.gson/gson/2.14.0'
) | ForEach-Object {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $DependencyCache $_) -Recurse -Filter '*.jar')
    if ($jars.Count -ne 1) { throw "Expected exactly one cached dependency for $_, found $($jars.Count)" }
    $jars[0].FullName
}
$dependencyClasspath = $dependencies -join [IO.Path]::PathSeparator
& "$JavaHome/bin/javac.exe" -proc:none -encoding UTF-8 -cp $dependencyClasspath -d $classes "$PSScriptRoot/CameraWeavingCheck.java"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$classpath = @($classes, "$PSScriptRoot/resources", $dependencyClasspath,
    (Resolve-Path -LiteralPath $MixinInput).Path,
    (Resolve-Path -LiteralPath $MinecraftJar).Path) -join [IO.Path]::PathSeparator
$testArgs = @()
if ($Platform) { $testArgs += $Platform }
& "$JavaHome/bin/java.exe" -cp $classpath cn.zbx1425.mtrsteamloco.compatibility.CameraWeavingCheck @testArgs
exit $LASTEXITCODE
