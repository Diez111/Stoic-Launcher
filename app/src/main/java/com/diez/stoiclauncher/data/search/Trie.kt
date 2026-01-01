package com.diez.stoiclauncher.data.search

import com.diez.stoiclauncher.domain.model.AppModel
import java.util.Locale

class Trie {
    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        val apps = mutableListOf<AppModel>()
    }

    private val root = TrieNode()

    fun insert(app: AppModel) {
        // Normalize label to lowercase for case-insensitive search
        val label = app.label.lowercase(Locale.getDefault())
        var currentNode = root

        for (char in label) {
            currentNode = currentNode.children.getOrPut(char) { TrieNode() }
            // Optional: Store app at every node for "instant" partial matches if we want
            // extremely aggressive "contains" logic, but standard Trie is prefix-based.
            // For this launcher, we might want to store it at the leaf or intermediate.
            // Let's store efficiently: valid apps for this prefix are found by traversing down.
            // Actually, for a small set of apps (200-500), standard prefix search is fine.
        }
        currentNode.apps.add(app)
    }

    fun search(query: String): List<AppModel> {
        val normalizedQuery = query.lowercase(Locale.getDefault())
        var currentNode = root

        for (char in normalizedQuery) {
            currentNode = currentNode.children[char] ?: return emptyList()
        }

        return collectAllApps(currentNode)
    }

    private fun collectAllApps(node: TrieNode): List<AppModel> {
        val results = mutableListOf<AppModel>()
        results.addAll(node.apps)
        for (child in node.children.values) {
            results.addAll(collectAllApps(child))
        }
        return results
    }
    
    fun clear() {
        root.children.clear()
        root.apps.clear()
    }
}
