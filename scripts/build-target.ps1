# Internal target runner for gradlew.bat. Examples: .\gradlew.bat build -Version="1.21.1"; .\gradlew.bat build -Version="26.2"
# Other arguments are passed to Gradle. -ShowTarget prints the selection without starting Java or Gradle.
$ErrorActionPreference = 'Stop'
$selectedVersion = $null
$selectedJavaHome = $null
$showTarget = $false
$gradleArguments = [System.Collections.Generic.List[string]]::new()
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

try {
    for ($index = 0; $index -lt $args.Count; $index++) {
        $argument = [string]$args[$index]
        if ($argument -eq '--') {
            for ($index++; $index -lt $args.Count; $index++) { $gradleArguments.Add([string]$args[$index]) }
            break
        }
        if ($argument -match '^-(Version|JavaHome|PbuildVersion)(?:=(.*))?$') {
            $option = $Matches[1]
            if ($option -eq 'PbuildVersion') { $option = 'Version' }
            if ($Matches.ContainsKey(2)) {
                $value = $Matches[2]
            } else {
                $index++
                if ($index -ge $args.Count -or [string]$args[$index] -like '-*') { throw "-$option requires a value." }
                $value = [string]$args[$index]
            }
            if ([string]::IsNullOrWhiteSpace($value)) { throw "-$option requires a value." }
            if ($option -eq 'Version') {
                if ($null -ne $selectedVersion) { throw '-Version must be specified only once.' }
                $selectedVersion = $value
            } else {
                if ($null -ne $selectedJavaHome) { throw '-JavaHome must be specified only once.' }
                $selectedJavaHome = $value
            }
        } elseif ($argument -eq '-ShowTarget') {
            $showTarget = $true
        } else {
            $gradleArguments.Add($argument)
        }
    }

    $catalog = Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle/minecraft-targets.json') -Raw | ConvertFrom-Json
    if ($null -eq $selectedVersion) { $selectedVersion = $catalog.defaultVersion }
    $profileProperty = $catalog.targets.PSObject.Properties[$selectedVersion]
    if ($null -eq $profileProperty) {
        throw "Unsupported Minecraft version '$selectedVersion'. Available: $($catalog.targets.PSObject.Properties.Name -join ', ')."
    }
    $profile = $profileProperty.Value
    $projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $profile.projectDirectory))
    $isWindowsHost = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
    $wrapperName = if ($isWindowsHost) { 'gradlew.bat' } else { 'gradlew' }
    $wrapper = Join-Path $projectDirectory $wrapperName

    # Skip the values of Gradle options when deciding whether a task was provided.
    $optionsWithValue = @('-b', '--build-file', '-c', '--settings-file', '-g', '--gradle-user-home', '-I', '--init-script', '-p', '--project-dir', '-x', '--exclude-task', '--include-build', '--console', '--warning-mode', '--max-workers', '--priority', '--project-cache-dir', '-D', '-P')
    $hasTask = $false
    for ($index = 0; $index -lt $gradleArguments.Count; $index++) {
        if ($optionsWithValue -contains $gradleArguments[$index]) { $index++; continue }
        if (-not $gradleArguments[$index].StartsWith('-')) { $hasTask = $true; break }
    }
    if (-not $hasTask) { $gradleArguments.Insert(0, 'build') }

    if ($showTarget) {
        [ordered]@{
            minecraftVersion = $selectedVersion
            javaVersion = $profile.javaVersion
            gradleVersion = $profile.gradleVersion
            status = $profile.status
            projectDirectory = $projectDirectory
            wrapper = $wrapper
            gradleArguments = $gradleArguments.ToArray()
        } | ConvertTo-Json -Depth 3
        exit 0
    }

    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw "Gradle wrapper missing for Minecraft ${selectedVersion}: $wrapper" }
    $javaHome = if ($null -ne $selectedJavaHome) { $selectedJavaHome } else { $env:JAVA_HOME }
    if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
        $javaHome = [System.IO.Path]::GetFullPath($javaHome.Trim('"'))
        $javaExecutable = Join-Path $javaHome $(if ($isWindowsHost) { 'bin/java.exe' } else { 'bin/java' })
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { throw "Java executable missing: $javaExecutable" }
    } else {
        $javaExecutable = (Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1).Source
        if (-not $javaExecutable) { throw "Minecraft $selectedVersion requires JDK $($profile.javaVersion). Set JAVA_HOME or pass -JavaHome <JDK directory>." }
    }
    $javaOutput = & { $ErrorActionPreference = 'Continue'; & $javaExecutable -version 2>&1 }
    if ($LASTEXITCODE -ne 0 -or ($javaOutput -join "`n") -notmatch '\bversion\s+"(?:1\.)?(\d+)') { throw "Could not determine Java version from $javaExecutable." }
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -ne $profile.javaVersion) { throw "Minecraft $selectedVersion requires JDK $($profile.javaVersion); found JDK $javaMajor. Set JAVA_HOME or pass -JavaHome <JDK directory>." }

    Write-Host "Building Minecraft $selectedVersion with JDK $javaMajor / Gradle $($profile.gradleVersion)."
    if ($profile.status -ne 'release') { Write-Warning "Minecraft $selectedVersion is $($profile.status); the port is not a verified release." }
    $previousJavaHome = $env:JAVA_HOME
    $previousInternalWrapper = $env:ANTE_INTERNAL_GRADLE_WRAPPER
    Push-Location -LiteralPath $projectDirectory
    try {
        if (-not [string]::IsNullOrWhiteSpace($javaHome)) { $env:JAVA_HOME = $javaHome }
        if ($projectDirectory -eq $repositoryRoot) { $env:ANTE_INTERNAL_GRADLE_WRAPPER = '1' }
        & $wrapper @gradleArguments
        $buildExitCode = $LASTEXITCODE
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:ANTE_INTERNAL_GRADLE_WRAPPER = $previousInternalWrapper
        Pop-Location
    }
    exit $buildExitCode
} catch {
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    exit 2
}
