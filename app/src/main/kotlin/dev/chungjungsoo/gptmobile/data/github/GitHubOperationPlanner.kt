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
            add(step("repo_status", "Resolve repository, default branch, recent PR state and checks."))
            add(step("search", "Search the repository index/code for the smallest relevant file set."))
            add(step("read", "Read only required line ranges and keep source paths and SHAs."))
            if (wantsWrite) {
                add(step("branch_head", "Read the working-branch head immediately before preparing writes."))
                add(step("commit", "Commit an atomic reviewed change set with expected_head_sha; never force-update."))
                add(step("diff", "Inspect the resulting compare data and changed-file statistics."))
                add(step("checks", "Inspect CI/check results for the new commit."))
                add(step("draft_pr", "Create or update a draft pull request for review."))
            }
            if (wantsMerge) add(step("merge_gate", "Merge only after explicit user intent and required checks/reviews pass."))
            if (wantsRelease) {
                add(step("release_status", "Check whether the requested tag/release already exists and identify the repository release workflow."))
                add(step("publish_release", "Use the native publish_release action. Prefer the repository release workflow; use direct release creation only when no release workflow applies."))
                add(step("release_verify", "Call release_status after publication to verify the workflow result, final release and assets."))
            }
        }
    }

    private fun step(id: String, description: String) = buildJsonObject {
        put("id", id)
        put("description", description)
    }
}
