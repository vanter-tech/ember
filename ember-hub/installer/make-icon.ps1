<#
Wraps frontend/src/assets/ember.png (resized to 256x256) into a single-image
.ico (PNG-in-ICO, valid on Windows Vista+), so no external image tool is needed.
#>
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$src = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "frontend\src\assets\ember.png"
$out = Join-Path $PSScriptRoot "ember-hub.ico"

$img = [System.Drawing.Image]::FromFile($src)
$bmp = New-Object System.Drawing.Bitmap 256, 256
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, 256, 256)
$g.Dispose(); $img.Dispose()

$pngStream = New-Object System.IO.MemoryStream
$bmp.Save($pngStream, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$png = $pngStream.ToArray()

$fs = [System.IO.File]::Create($out)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]1)      # ICONDIR: reserved, type=icon, count=1
$bw.Write([Byte]0);   $bw.Write([Byte]0)                              # width/height 0 => 256
$bw.Write([Byte]0);   $bw.Write([Byte]0)                              # colors, reserved
$bw.Write([UInt16]1); $bw.Write([UInt16]32)                          # planes, bpp
$bw.Write([UInt32]$png.Length)                                        # bytes of image data
$bw.Write([UInt32]22)                                                 # offset (6 + 16)
$bw.Write($png)
$bw.Dispose(); $fs.Dispose()
Write-Host "wrote $out ($($png.Length) bytes)"
