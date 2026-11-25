# Builds the NEXUS phone app (release APK) on Windows without Android Studio.
#
# The first run downloads a portable toolchain into %LOCALAPPDATA%\nexus-build (JDK 17, Gradle 8.11.1,
# Android SDK 35, about 1.5 GB); later runs reuse it. Behind Zscaler the Zscaler root certificate from the
# Windows store is added to that JDK only, so Gradle can download its libraries.
#
# Output: NEXUS-<VERSION>.apk in the project folder.
#   powershell -ExecutionPolicy Bypass -File scripts\windows\build_apk.ps1            # tests + release APK
#   powershell -ExecutionPolicy Bypass -File scripts\windows\build_apk.ps1 -SkipTests

param([switch]$SkipTests)

$ErrorActionPreference = "Stop"
$project = (Resolve-Path "$PSScriptRoot\..\..").Path
$app = Join-Path $project "android-app"
$root = Join-Path $env:LOCALAPPDATA "nexus-build"
$gradleVersion = "8.11.1"

function Get-File($url, $path) {
    if (Test-Path $path) { return }
    Write-Host "Scarico $url"
    curl.exe -L --fail --retry 2 -o "$path.part" $url
    if ($LASTEXITCODE -ne 0) { throw "Download non riuscito: $url" }
    Move-Item "$path.part" $path
}

New-Item -ItemType Directory -Force $root | Out-Null

# JDK 17 (Temurin, portable)
$jdk = Get-ChildItem $root -Directory | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1
if (-not $jdk) {
    Get-File "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" "$root\jdk17.zip"
    tar -xf "$root\jdk17.zip" -C $root
    $jdk = Get-ChildItem $root -Directory | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1
}
$env:JAVA_HOME = $jdk.FullName
$keytool = Join-Path $jdk.FullName "bin\keytool.exe"
$cacerts = Join-Path $jdk.FullName "lib\security\cacerts"

# Zscaler intercepts HTTPS: Java must trust its root like Windows does.
$zscaler = Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root |
    Where-Object { $_.Subject -like "*Zscaler*" } | Select-Object -First 1
if ($zscaler) {
    $ErrorActionPreference = "Continue"
    & $keytool -list -alias zscaler-root -keystore $cacerts -storepass changeit 2>&1 | Out-Null
    $known = $LASTEXITCODE -eq 0
    $ErrorActionPreference = "Stop"
    if (-not $known) {
        Write-Host "Aggiungo il certificato Zscaler al JDK di build"
        Export-Certificate -Cert $zscaler -FilePath "$root\zscaler.cer" | Out-Null
        & $keytool -importcert -noprompt -alias zscaler-root -keystore $cacerts -storepass changeit -file "$root\zscaler.cer"
        if ($LASTEXITCODE -ne 0) { throw "Import del certificato Zscaler non riuscito" }
    }
}

# Gradle
$gradle = Join-Path $root "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path $gradle)) {
    Get-File "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" "$root\gradle.zip"
    tar -xf "$root\gradle.zip" -C $root
}

# Android SDK: platform 35, build-tools, platform-tools
$sdk = Join-Path $root "sdk"
$env:ANDROID_HOME = $sdk
if (-not (Test-Path "$sdk\platforms\android-35")) {
    $sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
    if (-not (Test-Path $sdkmanager)) {
        Get-File "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" "$root\cmdline.zip"
        New-Item -ItemType Directory -Force "$root\cmdline-extract", "$sdk\cmdline-tools\latest" | Out-Null
        tar -xf "$root\cmdline.zip" -C "$root\cmdline-extract"
        Copy-Item -Recurse -Force "$root\cmdline-extract\cmdline-tools\*" "$sdk\cmdline-tools\latest"
    }
    1..40 | ForEach-Object { "y" } | & $sdkmanager --sdk_root="$sdk" --licenses | Out-Null
    & $sdkmanager --sdk_root="$sdk" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
    if ($LASTEXITCODE -ne 0) { throw "Installazione Android SDK non riuscita" }
}

# local.properties is gitignored: it only tells Gradle where this PC keeps the SDK.
Set-Content -Path "$app\local.properties" -Value ("sdk.dir=" + ($sdk -replace "\\", "/")) -Encoding ascii

# The project asks for a JetBrains JDK 21 for the Gradle daemon; here JDK 17 does the job.
# The file is put back exactly as it was, even if the build fails.
$daemonProps = "$app\gradle\gradle-daemon-jvm.properties"
$daemonBackup = "$root\gradle-daemon-jvm.properties.bak"
Copy-Item $daemonProps $daemonBackup -Force
$tasks = @(":app:assembleRelease")
if (-not $SkipTests) { $tasks = @(":app:testDebugUnitTest") + $tasks }
try {
    Set-Content -Path $daemonProps -Value "toolchainVersion=17" -Encoding ascii
    & $gradle -p $app @tasks --no-daemon
    $code = $LASTEXITCODE
} finally {
    Copy-Item $daemonBackup $daemonProps -Force
}
if ($code -ne 0) { throw "Build non riuscita (gradle exit $code)" }

$version = (Get-Content "$project\VERSION" -Raw).Trim()
$apk = Join-Path $project "NEXUS-$version.apk"
Copy-Item "$app\app\build\outputs\apk\release\app-release.apk" $apk -Force
& "$sdk\build-tools\35.0.0\aapt.exe" dump badging $apk | Select-String -Pattern "^package:|^native-code:"
Write-Host ("APK pronto: $apk ({0:N1} MB)" -f ((Get-Item $apk).Length / 1MB))
