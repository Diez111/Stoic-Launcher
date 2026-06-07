package com.diez.stoiclauncher.domain.model

data class CategoryGroup(
    val name: String,
    val apps: List<AppModel>,
    val originalName: String = name
)