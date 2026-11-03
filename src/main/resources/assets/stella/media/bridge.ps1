# Stella Client - Windows media bridge.
# Reads "now playing" from the Windows Global System Media Transport Controls
# (the same source the volume flyout and media keys use). No Spotify account,
# API key or login is involved. Protocol: one command per stdin line
#   poll|toggle|next|prev <preferredApp>
# and one JSON line per command on stdout.
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    $task.Result
}
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$coverPath = $args[0]
$lastKey = ''
$prefer = ''
[Console]::Out.WriteLine('{"ready":true}')
[Console]::Out.Flush()

function Get-Session {
    if ($script:prefer) {
        foreach ($s in $mgr.GetSessions()) { if ($s.SourceAppUserModelId -like "*$($script:prefer)*") { return $s } }
        return $null
    }
    return $mgr.GetCurrentSession()
}

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    $parts = $line.Split(' ', 2)
    $cmd = $parts[0]
    $script:prefer = if ($parts.Count -gt 1) { $parts[1].Trim() } else { '' }
    $s = Get-Session
    if ($null -eq $s) { [Console]::Out.WriteLine('{"active":false}'); [Console]::Out.Flush(); continue }
    switch ($cmd) {
        'toggle' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
        'next'   { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }
        'prev'   { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }
    }
    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $tl = $s.GetTimelineProperties()
    $status = $s.GetPlaybackInfo().PlaybackStatus.ToString()
    $pos = $tl.Position.TotalMilliseconds
    if ($status -eq 'Playing') { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
    $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
    if ($pos -gt $dur -and $dur -gt 0) { $pos = $dur }
    $key = "$($p.Title)|$($p.Artist)"
    $cover = ''
    if ($key -ne $lastKey) {
        if ($p.Thumbnail) {
            try {
                $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                $fs = [System.IO.File]::Create($coverPath)
                $net.CopyTo($fs)
                $fs.Close()
                $net.Close()
                $cover = $coverPath
            } catch { }
        }
        $lastKey = $key
    }
    $o = [ordered]@{
        active = $true; title = "$($p.Title)"; artist = "$($p.Artist)"; status = $status
        position = [long]$pos; duration = [long]$dur; app = "$($s.SourceAppUserModelId)"; cover = $cover; key = $key
    }
    [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}
