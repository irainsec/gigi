const fs = require('fs');
const filePath = 'c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt';
let content = fs.readFileSync(filePath, 'utf8');

// Fix refreshLocalCategories
content = content.replace(
    /private fun refreshLocalCategories\(\) \{[\s\S]+?\.map \{ it\.packageName \}\s+\.filter \{ it\.isNotBlank\(\) \}\s+\.distinct\(\)\s+\/\/ Merge with server-sourced apps \(if any\)\s+val merged = \(localApps \+ _notificationApps\.value\)\.distinct\(\)\s+_notificationApps\.value = merged\s+\}/,
    `private fun refreshLocalCategories() {
        val localApps = _remoteNotifications.value
            .map { it.packageName }
            .filter { it?.isNotBlank() == true }
            .distinct()
        // Merge with server-sourced apps (if any)
        val merged = (localApps + _notificationApps.value).distinct()
        _notificationApps.value = merged.filterNotNull()
    }`
);

// Fix searchNotifications
content = content.replace(
    /fun searchNotifications\(query: String, packageName: String\? = null\) \{[\s\S]+?val q = query\.trim\(\)\s+val matchText = q\.isEmpty\(\) \|\|\s+n\.title\.contains\(q, ignoreCase = true\) \|\|\s+n\.text\.contains\(q, ignoreCase = true\)[\s\S]+?if \(_partnerConnectionId\.value != null\) \{[\s\S]+?syncManager\.sendSearchCommand\(_partnerConnectionId\.value!!, query, packageName\)[\s\S]+?\}/,
    `fun searchNotifications(query: String, packageName: String? = null) {
        // 1. Apply local filter instantly for fast feedback
        val localFiltered = _remoteNotifications.value.filter { n ->
            val matchPkg = packageName == null || n.packageName == packageName
            val q = query.trim()
            val matchText = q.isEmpty() ||
                n.title?.contains(q, ignoreCase = true) == true ||
                n.text?.contains(q, ignoreCase = true) == true
            matchPkg && matchText
        }
        _searchResults.value = localFiltered

        // 2. Also query server for historical results (merges on response)
        val conn = selectedConnection.value
        if (conn != null) {
            _isSearching.value = true
            syncManager.sendSearchCommand(conn.connectionId, query, packageName)
        }
    }`
);

fs.writeFileSync(filePath, content);
console.log('✅ Patched ScreensaverViewModel compilation errors');
