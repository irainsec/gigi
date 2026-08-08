$path = "app/src/main/java/com/aman/gigi/ui/Screensaver.kt"
$content = Get-Content $path -Raw
$content = $content.Replace("onBack = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) }", "onBack = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST) },`n                            onEmojiClick = { showEmojiPicker = true }")
Set-Content -Path $path -Value $content -NoNewline
