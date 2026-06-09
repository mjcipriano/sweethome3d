param(
  [Parameter(Mandatory = $true)]
  [string]$Version,

  [string]$OutputDirectory = "release"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command ant -ErrorAction SilentlyContinue)) {
  throw "Apache Ant is required and must be available on PATH."
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage is required. Use JDK 17 or newer."
}
if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
  throw "CMake is required to build the model LOD native library."
}

$platform = if ($IsWindows) {
  "windows-x64"
} elseif ($IsMacOS) {
  "macos-x64"
} elseif ($IsLinux) {
  "linux-x64"
} else {
  throw "Unsupported operating system."
}

& cmake -S native/model-lod -B build/native/model-lod -DCMAKE_BUILD_TYPE=Release
if ($LASTEXITCODE -ne 0) {
  throw "CMake configure failed."
}
& cmake --build build/native/model-lod --config Release --parallel
if ($LASTEXITCODE -ne 0) {
  throw "Model LOD native build failed."
}

$nativePlatform = $platform.Split("-", 2)[0]
$nativeResourceDir = Join-Path $root "build/model-lod-native/native/$nativePlatform/x64"
New-Item $nativeResourceDir -ItemType Directory -Force | Out-Null
$nativeLibrary = if ($IsWindows) {
  "build/native/model-lod/Release/sweethome3d_model_lod.dll"
} elseif ($IsMacOS) {
  "build/native/model-lod/libsweethome3d_model_lod.dylib"
} else {
  "build/native/model-lod/libsweethome3d_model_lod.so"
}
if (-not (Test-Path $nativeLibrary)) {
  throw "Expected native model LOD library was not produced: $nativeLibrary"
}
Copy-Item $nativeLibrary $nativeResourceDir -Force
$nativeStaging = Join-Path ([System.IO.Path]::GetTempPath()) "sweethome3d-model-lod-$platform"
Remove-Item $nativeStaging -Recurse -Force -ErrorAction SilentlyContinue
New-Item $nativeStaging -ItemType Directory -Force | Out-Null
Copy-Item $nativeLibrary $nativeStaging -Force

& ant "-Dversion=$Version" jarExecutable
if ($LASTEXITCODE -ne 0) {
  throw "Ant build failed."
}

$jarName = "SweetHome3D-$Version.jar"
$jarPath = Join-Path $root "install/$jarName"
if (-not (Test-Path $jarPath)) {
  throw "Expected executable JAR was not produced: $jarPath"
}

$output = Join-Path $root $OutputDirectory
$staging = Join-Path $output "staging-$platform"
$input = Join-Path $staging "input"
$appImages = Join-Path $staging "app-images"
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item $input -ItemType Directory -Force | Out-Null
New-Item $appImages -ItemType Directory -Force | Out-Null
Copy-Item $jarPath (Join-Path $input $jarName)

# jpackage accepts numeric application versions only. Keep the full semantic
# version in artifact names and use the numeric core for native metadata.
$appVersion = $Version.Split("-", 2)[0]
if ($appVersion -notmatch "^\d+\.\d+\.\d+$") {
  throw "Version must start with a numeric semantic version such as 7.5.1."
}

$jpackageArgs = @(
  "--type", "app-image",
  "--name", "Sweet Home 3D",
  "--dest", $appImages,
  "--input", $input,
  "--main-jar", $jarName,
  "--main-class", "com.eteks.sweethome3d.SweetHome3DBootstrap",
  "--app-version", $appVersion,
  "--vendor", "Space Mushrooms",
  "--description", "Interior design application",
  "--java-options", "-XX:MaxRAMPercentage=50.0",
  "--java-options", "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
  "--java-options", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
  "--java-options", "--add-opens=java.desktop/com.apple.eio=ALL-UNNAMED",
  "--java-options", "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
  "--java-options", "-Djogamp.gluegen.UseTempJarCache=false",
  "--java-options", "-Dcom.eteks.sweethome3d.applicationId=SweetHome3D#GitHubRelease",
  "--java-options", "-Dcom.eteks.sweethome3d.applicationVersion=$Version"
)

if ($IsWindows) {
  $jpackageArgs += @("--icon", (Join-Path $root "install/windows/SweetHome3D.ico"))
} elseif ($IsMacOS) {
  $jpackageArgs += @("--icon", (Join-Path $root "install/macosx/Sweet Home 3D/Contents/Resources/SweetHome3D.icns"))
} else {
  $jpackageArgs += @("--icon", (Join-Path $root "src/com/eteks/sweethome3d/swing/resources/aboutIcon.png"))
}

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
  throw "jpackage failed."
}

$appImageName = if ($IsMacOS) { "Sweet Home 3D.app" } else { "Sweet Home 3D" }
$appImage = Join-Path $appImages $appImageName
if (-not (Test-Path $appImage)) {
  throw "Expected application image was not produced: $appImage"
}

Get-ChildItem $root -Filter "*LICENSE*" -File | ForEach-Object {
  Copy-Item $_.FullName $appImage
}
Copy-Item (Join-Path $root "COPYING.TXT") $appImage

New-Item $output -ItemType Directory -Force | Out-Null
$archiveBase = "SweetHome3D-$Version-$platform"
if ($IsWindows) {
  $archive = Join-Path $output "$archiveBase.zip"
  Remove-Item $archive -Force -ErrorAction SilentlyContinue
  Compress-Archive -Path $appImage -DestinationPath $archive -CompressionLevel Optimal
} else {
  $archive = Join-Path $output "$archiveBase.tar.gz"
  Remove-Item $archive -Force -ErrorAction SilentlyContinue
  & tar -C $appImages -czf $archive $appImageName
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create release archive."
  }
}

Copy-Item $jarPath (Join-Path $output $jarName) -Force
$releaseNativeDir = Join-Path $output "native/$nativePlatform/x64"
New-Item $releaseNativeDir -ItemType Directory -Force | Out-Null
Copy-Item (Join-Path $nativeStaging (Split-Path $nativeLibrary -Leaf)) $releaseNativeDir -Force
Write-Host "Created $archive"
Write-Host "Created $(Join-Path $output $jarName)"
