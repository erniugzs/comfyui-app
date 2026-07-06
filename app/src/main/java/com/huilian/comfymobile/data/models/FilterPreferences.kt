package com.huilian.comfymobile.data.models

data class FilterPreferences(
    val selectedNodeTypes: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showOnlyEditable: Boolean = false
)
