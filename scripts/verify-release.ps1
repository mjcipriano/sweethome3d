param(
  [Parameter(Mandatory = $true)]
  [string]$Version,

  [Parameter(Mandatory = $true)]
  [string]$ArtifactDirectory,

  [switch]$SkipArchiveCheck
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
$artifacts = Join-Path $root $ArtifactDirectory
$jar = Join-Path $artifacts "SweetHome3D-$Version.jar"

if (-not (Test-Path $jar)) {
  throw "Missing executable JAR: $jar"
}

$requiredEntries = @(
  "META-INF/MANIFEST.MF",
  "com/eteks/sweethome3d/SweetHome3DBootstrap.class",
  "java3d-1.6/j3dcore.jar",
  "java3d-1.6/vecmath.jar",
  "com/eteks/sweethome3d/io/DefaultFurnitureCatalog.properties",
  "com/eteks/sweethome3d/io/DefaultTexturesCatalog.properties"
)
$jarEntries = & jar tf $jar
foreach ($entry in $requiredEntries) {
  if ($jarEntries -notcontains $entry) {
    throw "Executable JAR is missing required entry: $entry"
  }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
  $manifestEntry = $zip.GetEntry("META-INF/MANIFEST.MF")
  if ($null -eq $manifestEntry) {
    throw "Executable JAR is missing META-INF/MANIFEST.MF."
  }
  $reader = [System.IO.StreamReader]::new($manifestEntry.Open())
  try {
    $manifest = $reader.ReadToEnd()
  } finally {
    $reader.Dispose()
  }
} finally {
  $zip.Dispose()
}
if ($manifest -notmatch "(?m)^Implementation-Version: $([regex]::Escape($Version))\r?$") {
  throw "Executable JAR manifest version does not match release version $Version."
}

if (-not $SkipArchiveCheck) {
  $archives = @(Get-ChildItem $artifacts -File | Where-Object {
    $_.Name -match "^SweetHome3D-$([regex]::Escape($Version))-(windows|linux|macos)-x64\.(zip|tar\.gz)$"
  })
  if ($archives.Count -ne 1) {
    throw "Expected exactly one platform archive, found $($archives.Count)."
  }
  if ($archives[0].Length -lt 50MB) {
    throw "Platform archive is unexpectedly small: $($archives[0].Length) bytes."
  }
  Write-Host "Verified $($archives[0].Name) and $(Split-Path $jar -Leaf)."
} else {
  Write-Host "Verified $(Split-Path $jar -Leaf)."
}
