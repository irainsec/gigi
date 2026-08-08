import os

file_path = r"C:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\viewmodel\ScreensaverViewModel.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Let's find the position of PhotoListReceived and reconstruct the following block
start_idx = -1
for i, line in enumerate(lines):
    if "is com.aman.gigi.data.sync.SyncEvent.PhotoListReceived" in line:
        start_idx = i
        break

if start_idx != -1:
    # Find the next 'is com.aman.gigi.data.sync.SyncEvent.FileDownloadReceived' to know where to stop
    end_idx = -1
    for i in range(start_idx, len(lines)):
        if "is com.aman.gigi.data.sync.SyncEvent.FileDownloadReceived" in line:
            # Wait, I need to find the line that contains it
            pass
    
    # Actually, let's just replace the broken chunk between 217 and 224 based on the previous view_file
    new_lines = lines[:217]
    new_lines.append("                    is com.aman.gigi.data.sync.SyncEvent.PhotoListReceived -> {\n")
    new_lines.append("                        _remotePhotos.value = event.photos\n")
    new_lines.append("                        _isFetchingPhotos.value = false\n")
    new_lines.append("                    }\n")
    new_lines.append("                    is com.aman.gigi.data.sync.SyncEvent.LiveVideoFrameReceived -> {\n")
    new_lines.append("                        if (!isDecodingFrame) {\n")
    new_lines.append("                            isDecodingFrame = true\n")
    new_lines.append("                            viewModelScope.launch(Dispatchers.IO) {\n")
    new_lines.append("                                try {\n")
    new_lines.append("                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(event.data, 0, event.data.size)\n")
    new_lines.append("                                    if (bitmap != null) {\n")
    new_lines.append("                                        _liveCameraFrame.value = bitmap\n")
    new_lines.append("                                    }\n")
    new_lines.append("                                } catch (e: Throwable) {\n")
    new_lines.append("                                    android.util.Log.e(\"ScreensaverVM\", \"❌ [LIVE-FRAME] Decode failed\", e)\n")
    new_lines.append("                                } finally {\n")
    new_lines.append("                                    isDecodingFrame = false\n")
    new_lines.append("                                }\n")
    new_lines.append("                            }\n")
    new_lines.append("                        }\n")
    new_lines.append("                    }\n")
    new_lines.append("                    is com.aman.gigi.data.sync.SyncEvent.PhotoDownloadReceived -> {\n")
    new_lines.append("                        saveDownloadedPhoto(event.photoId, event.data)\n")
    new_lines.append("                    }\n")
    new_lines.append("                    is com.aman.gigi.data.sync.SyncEvent.FileListReceived -> {\n")
    new_lines.append("                        _remoteFiles.value = event.files\n")
    new_lines.append("                        _currentBrowsePath.value = event.path\n")
    new_lines.append("                        _isFetchingFiles.value = false\n")
    new_lines.append("                    }\n")
    
    # Skip the broken lines 217-223 from the previous view
    # Line 224 was 'is com.aman.gigi.data.sync.SyncEvent.FileDownloadReceived'
    # Find that line in original lines to resume
    resume_idx = -1
    for i in range(start_idx, len(lines)):
        if "is com.aman.gigi.data.sync.SyncEvent.FileDownloadReceived" in lines[i]:
            resume_idx = i
            break
    
    if resume_idx != -1:
        new_lines.extend(lines[resume_idx:])
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        print("SUCCESS: Patched ScreensaverViewModel.kt")
    else:
        print("ERROR: Could not find resume point")
else:
    print("ERROR: Could not find start point")
