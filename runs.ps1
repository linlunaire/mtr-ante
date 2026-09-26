$bf = ".\build"
$dp = "F:\mc\server\1.18.2_fabric\mods"
$ep = "F:\mc\server\1.18.2_fabric\run.bat"

$sp = Get-ChildItem -Path $bf -Filter "*1.18.2.jar" | 
      Sort-Object LastWriteTime -Descending | 
      Select-Object -First 1 -ExpandProperty FullName

if ( $sp ) {
    Remove-Item -Path "$dp\MTR-ANTE*" -Force
    Copy-Item -Path "$sp" -Destination "$dp" -Recurse -Force
    Start-Process -FilePath powershell.exe -ArgumentList "-Command `& `'$ep`'"
} else {
    Write-Host "No new build found."
}