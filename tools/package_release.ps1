param(
    [string]$Version = "0.6.0-alpha.17"
)

$ErrorActionPreference = "Stop"
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$dist = Join-Path $projectRoot "dist"
[IO.Directory]::CreateDirectory($dist) | Out-Null

$builtJar = Join-Path $projectRoot "build/libs/MonaBowling-$Version.jar"
$releaseJar = Join-Path $dist "MonaBowling-$Version.jar"
if (-not [IO.File]::Exists($builtJar)) {
    throw "Built plugin not found: $builtJar. Run Gradle shadowJar first."
}
Copy-Item -LiteralPath $builtJar -Destination $releaseJar -Force

Add-Type -AssemblyName System.IO.Compression

function New-FilteredZip {
    param(
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string[]]$Inputs
    )

    $destinationPath = [IO.Path]::GetFullPath($Destination)
    if (-not $destinationPath.StartsWith($dist, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to create a release outside dist: $destinationPath"
    }

    $fileStream = [IO.File]::Open($destinationPath, [IO.FileMode]::Create)
    try {
        $archive = [IO.Compression.ZipArchive]::new(
            $fileStream,
            [IO.Compression.ZipArchiveMode]::Create,
            $false
        )
        try {
            foreach ($input in $Inputs) {
                $inputPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $input))
                $files = if ([IO.Directory]::Exists($inputPath)) {
                    Get-ChildItem -LiteralPath $inputPath -File -Recurse
                } else {
                    Get-Item -LiteralPath $inputPath
                }

                foreach ($file in $files) {
                    $relative = [IO.Path]::GetRelativePath($projectRoot, $file.FullName)
                    if ($relative -match '(^|[\\/])\.pydeps([\\/]|$)' -or
                        $relative -match '(^|[\\/])__pycache__([\\/]|$)') {
                        continue
                    }
                    $entryName = $relative.Replace('\', '/')
                    $entry = $archive.CreateEntry(
                        $entryName,
                        [IO.Compression.CompressionLevel]::Optimal
                    )
                    $entry.LastWriteTime = [DateTimeOffset]::new(
                        2026, 11, 21, 20, 0, 0, [TimeSpan]::Zero
                    )
                    $source = [IO.File]::OpenRead($file.FullName)
                    $target = $entry.Open()
                    try {
                        $source.CopyTo($target)
                    } finally {
                        $target.Dispose()
                        $source.Dispose()
                    }
                }
            }
        } finally {
            $archive.Dispose()
        }
    } finally {
        $fileStream.Dispose()
    }
}

New-FilteredZip `
    -Destination (Join-Path $dist "MonaBowling-LaneBlueprints-$Version.zip") `
    -Inputs @("schematics")

New-FilteredZip `
    -Destination (Join-Path $dist "MonaBowling-Source-$Version.zip") `
    -Inputs @(
        "gradle",
        "src",
        "docs",
        "schematics",
        "tools",
        ".gitignore",
        "build.gradle.kts",
        "gradlew",
        "gradlew.bat",
        "README.md",
        "settings.gradle.kts",
        "TEST_RESULTS.md"
    )

$hashes = Get-FileHash -Algorithm SHA256 `
    (Join-Path $dist "MonaBowling-$Version.jar"), `
    (Join-Path $dist "MonaBowling-Source-$Version.zip"), `
    (Join-Path $dist "MonaBowling-LaneBlueprints-$Version.zip")

$hashes | ForEach-Object {
    "{0}  {1}" -f $_.Hash.ToLowerInvariant(), (Split-Path $_.Path -Leaf)
} | Set-Content -LiteralPath (Join-Path $dist "SHA256SUMS-$Version.txt") -Encoding utf8NoBOM

$hashes | Select-Object Hash, @{Name = "Name"; Expression = { Split-Path $_.Path -Leaf }}
