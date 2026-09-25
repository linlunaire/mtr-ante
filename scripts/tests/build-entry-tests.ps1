param([string]$JavaHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$isWindowsHost = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
$entry = Join-Path $PSScriptRoot $(if ($isWindowsHost) { '../../gradlew.bat' } else { '../../gradlew' })
$checks = 0

foreach ($scriptPath in @('../../gradlew', '../../versions/26.2/gradlew', '../build-target.sh')) {
    $shellText = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $scriptPath))
    if ($shellText.Contains("`r")) { throw "POSIX entry must use LF line endings: $scriptPath" }
    $checks++
}

function Assert-Target {
    param([string[]]$EntryArguments, [string]$Version, [string[]]$GradleArguments)
    $output = & $entry -ShowTarget @EntryArguments
    if ($LASTEXITCODE -ne 0) { throw "Target selection failed: $EntryArguments" }
    $target = ($output -join "`n") | ConvertFrom-Json
    if ($target.minecraftVersion -ne $Version) { throw "Expected $Version, received $($target.minecraftVersion)." }
    $expectedJava = if ($Version -eq '1.21.1') { 21 } else { 25 }
    $expectedGradle = if ($Version -eq '1.21.1') { '8.14.5' } else { '9.5.1' }
    if ($target.javaVersion -ne $expectedJava -or $target.gradleVersion -ne $expectedGradle) { throw "Incorrect Java / Gradle target: $output" }
    if (($target.gradleArguments -join '|') -ne ($GradleArguments -join '|')) { throw "Gradle arguments changed: $($target.gradleArguments -join ' ')" }
    $expectedRoot = if ($Version -eq '1.21.1') { '../..' } else { '../../versions/26.2' }
    $expectedRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $expectedRoot))
    if ($target.projectDirectory -ne $expectedRoot) { throw "Incorrect project directory: $($target.projectDirectory)" }
    $expectedWrapper = Join-Path $expectedRoot $(if ($isWindowsHost) { 'gradlew.bat' } else { 'gradlew' })
    if ($target.wrapper -ne $expectedWrapper -or -not (Test-Path -LiteralPath $target.wrapper -PathType Leaf)) { throw "Incorrect or missing Gradle wrapper: $($target.wrapper)" }
    $wrapperProperties = Get-Content -LiteralPath (Join-Path $expectedRoot 'gradle/wrapper/gradle-wrapper.properties') -Raw
    if ($wrapperProperties -notmatch ('gradle-' + [regex]::Escape($expectedGradle) + '-bin.zip')) { throw "Wrapper distribution does not match Gradle $expectedGradle." }
    $script:checks++
}

function Assert-Rejected {
    param([string[]]$EntryArguments, [string]$Message)
    $output = & { $ErrorActionPreference = 'Continue'; & $entry @EntryArguments 2>&1 }
    if ($LASTEXITCODE -ne 2 -or ($output -join "`n") -notmatch $Message) { throw "Expected validation failure '$Message': $output" }
    $script:checks++
}

# Use a different working directory to check that routing is relative to the script.
Push-Location -LiteralPath ([System.IO.Path]::GetTempPath())
try {
    Assert-Target @() '26.2' @('build')
    Assert-Target @('-Version=1.21.1') '1.21.1' @('build')
    Assert-Target @('-Version', '26.2') '26.2' @('build')
    Assert-Target @('build', '-Version=1.21.1') '1.21.1' @('build')
    Assert-Target @('build', '-Version', '26.2') '26.2' @('build')
    Assert-Target @('build', '-PbuildVersion=1.21.1') '1.21.1' @('build')
    Assert-Target @('-Version', '1.21.1', ':fabric:build', '--stacktrace', '-Pexample=a b') '1.21.1' @(':fabric:build', '--stacktrace', '-Pexample=a b')
    Assert-Target @('--console', 'plain', '--stacktrace') '26.2' @('build', '--console', 'plain', '--stacktrace')
    Assert-Target @('-JavaHome', 'C:\Java\jdk-25', 'help') '26.2' @('help')
    Assert-Target @('-Version=26.2', '-JavaHome=C:\Java\jdk-25', 'check') '26.2' @('check')
    Assert-Target @('-Version=26.2', '--max-workers', '2', '-I', 'init script.gradle') '26.2' @('build', '--max-workers', '2', '-I', 'init script.gradle')
    Assert-Target @('-Version=26.2', '--', 'help', '-PbuildVersion=custom') '26.2' @('help', '-PbuildVersion=custom')
    Assert-Rejected @('-Version=1.21.4', '-ShowTarget') 'Unsupported Minecraft version'
    Assert-Rejected @('-Version=') 'requires a value'
    Assert-Rejected @('-Version') 'requires a value'
    Assert-Rejected @('-Version', '1.21.1', '-Version=26.2') 'only once'
    Assert-Rejected @('-Version=1.21.1', '-PbuildVersion=26.2') 'only once'
    Assert-Rejected @('-JavaHome=') 'requires a value'
    Assert-Rejected @('-JavaHome') 'requires a value'
    Assert-Rejected @('-JavaHome=a', '-JavaHome=b') 'only once'
    Assert-Rejected @('-Version', '1.21.1', '-JavaHome', 'C:\missing-ante-test-jdk') 'Java executable missing'
} finally {
    Pop-Location
}

# Optional execution check: a small fixture exercises the real batch entry,
# Java validation and delegated exit code without compiling Minecraft.
if ($isWindowsHost -and -not [string]::IsNullOrWhiteSpace($JavaHome)) {
    $javaExecutable = Join-Path $JavaHome 'bin/java.exe'
    $javaOutput = & { $ErrorActionPreference = 'Continue'; & $javaExecutable -version 2>&1 }
    if ($LASTEXITCODE -ne 0 -or ($javaOutput -join "`n") -notmatch '\bversion\s+"(?:1\.)?(\d+)') { throw 'Could not determine the integration-test Java version.' }
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -eq 25) { Assert-Rejected @('-Version=1.21.1', '-JavaHome', $JavaHome, 'help') 'requires JDK 21; found JDK 25' }
    if ($javaMajor -eq 21) { Assert-Rejected @('-Version=26.2', '-JavaHome', $JavaHome, 'help') 'requires JDK 25; found JDK 21' }
    $fixture = Join-Path ([System.IO.Path]::GetTempPath()) ('ante-build-entry-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts'), (Join-Path $fixture 'gradle'), (Join-Path $fixture 'versions/26.2') | Out-Null
        Copy-Item -LiteralPath $entry -Destination (Join-Path $fixture 'gradlew.bat')
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../build-target.ps1') -Destination (Join-Path $fixture 'scripts/build-target.ps1')
        $catalog = @{
            defaultVersion = '26.2'
            targets = @{ '26.2' = @{ javaVersion = $javaMajor; projectDirectory = 'versions/26.2'; gradleVersion = 'fixture'; status = 'release' } }
        } | ConvertTo-Json -Depth 4
        [System.IO.File]::WriteAllText((Join-Path $fixture 'gradle/minecraft-targets.json'), $catalog)
        [System.IO.File]::WriteAllText((Join-Path $fixture 'versions/26.2/gradlew.bat'), "@echo off`r`necho FORWARDED: %*`r`nexit /b 37`r`n")
        $output = & (Join-Path $fixture 'gradlew.bat') build -JavaHome $JavaHome '-Pexample=a b' --stacktrace
        if ($LASTEXITCODE -ne 37) { throw "Delegated Gradle exit code changed: $LASTEXITCODE" }
        if (($output -join "`n") -notmatch 'FORWARDED: build "-Pexample=a b" --stacktrace') { throw "Delegated Gradle arguments changed: $output" }
        $checks++
    } finally {
        $resolvedFixture = [System.IO.Path]::GetFullPath($fixture)
        $resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
        if ([System.IO.Path]::GetDirectoryName($resolvedFixture) -ne $resolvedTemp -or [System.IO.Path]::GetFileName($resolvedFixture) -notlike 'ante-build-entry-*') { throw "Unexpected fixture cleanup target: $resolvedFixture" }
        if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
    }
}
Write-Host "Build entry checks passed: $checks"
exit 0
