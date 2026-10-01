package dev.chungjungsoo.gptmobile.data.github

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deterministic workflow hints for common repository tasks. The model receives
 * a short safe plan instead of rediscovering mutation order on every turn.
 */
object GitHubOperationPlanner {
    fun plan(goal: String): JsonArray {
        val normalized = goal.lowercase()
        val wantsRelease = listOf("release", "publish", "tag").any(normalized::contains)
        val wantsMerge = "merge" in normalized
        val wantsWrite = listOf("fix", "change", "implement", "update", "refactor", "merge", "release", "publish").any(normalized::contains)

        return buildJsonArray {
            addStep("repo_status", "Resolve repository, default branch, recent PR state and checks.")
            addStep("search", "Search the repository index/code for the smallest relevant file set.")
            addStep("read", "Read only required line ranges and keep source paths and SHAs.")
            if (wantsWrite) {
                addStep("branch_head", "Read the working-branch head immediately before preparing writes.")
                addStep("commit", "Commit an atomic reviewed change set with expected_head_sha; never force-update.")
                addStep("diff", "Inspect the resulting compare data and changed-file statistics.")
                addStep("checks", "Inspect CI/check results for the new commit.")
                addStep("draft_pr", "Create or update a draft pull request for review.")
            }
            if (wantsMerge) addStep("merge_gate", "Merge only after explicit user intent and required checks/reviews pass.")
            if (wantsRelease) addStep("release_gate", "Publish only after the requested build/release workflow succeeds.")
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addStep(id: String, description: String) {
        add(
            buildJsonObject {
                put("id", id)
                put("description", description)
            }
        )
    }
}
