param(
  [Parameter(Mandatory = $true)][string]$FfmpegExecutable,
  [Parameter(Mandatory = $true)][string]$OutputFile,
  [string]$Framerate = "30"
)

# Technical capture probe only. It captures the local desktop for one private
# session; it does not provide WebRTC, signaling, or playback in Android.
& $FfmpegExecutable -f gdigrab -framerate $Framerate -i desktop -c:v libx264 -preset veryfast -pix_fmt yuv420p $OutputFile
