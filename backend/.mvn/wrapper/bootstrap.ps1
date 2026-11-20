$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$properties = Join-Path $PSScriptRoot 'maven-wrapper.properties'
$line = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
if (-not $line) { throw 'distributionUrl ausente em maven-wrapper.properties' }
$url = ($line -split '=', 2)[1].Trim()
$uri = [Uri]$url
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'repo.maven.apache.org') {
    throw 'O Maven Wrapper aceita somente a distribuição HTTPS do Maven Central.'
}
$version = [regex]::Match($url, '/apache-maven/([0-9.]+)/').Groups[1].Value
if (-not $version) { throw 'Versão do Maven não encontrada em distributionUrl.' }
$destination = Join-Path $PSScriptRoot ("dists\apache-maven-" + $version)
$maven = Join-Path $destination ("apache-maven-" + $version + '\bin\mvn.cmd')
if (-not (Test-Path -LiteralPath $maven)) {
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $archive = Join-Path $destination 'maven.zip'
    Invoke-WebRequest -Uri $url -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
}
if (-not (Test-Path -LiteralPath $maven)) { throw 'A distribuição Maven não contém bin\mvn.cmd.' }
& $maven @args
exit $LASTEXITCODE
