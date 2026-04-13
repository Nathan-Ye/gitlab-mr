# AGENTS.md

This file is the current repo-specific map for coding agents working in `gitlab-mr`.

---

## Project Snapshot

- Project type: IntelliJ IDEA plugin
- Plugin name: `GitLab MR`
- Plugin ID: `com.nathan.gitlabmr`
- Current Gradle project version: `2.0.0`
- Main language: Kotlin
- Target JVM: Java 17
- Primary purpose: view and operate GitLab Merge Requests inside the IDE tool window

UI text is primarily Chinese. Some files may render as garbled text in a terminal because of encoding/display differences; do not assume source corruption until verified in IDEA.

---

## Verified Stack

From `build.gradle.kts`, `gradle.properties`, and local build execution:

- Kotlin: `2.1.0`
- IntelliJ Platform Gradle Plugin: `2.11.0`
- IntelliJ platform target: `IC 2024.2`
- Gson: `2.10.1`
- OkHttp: `4.12.0`
- Bundled plugin: `Git4Idea`
- Gradle wrapper distribution: `8.13`

Compatibility metadata is now aligned:

- `build.gradle.kts` sets `untilBuild` to `253.*`
- `src/main/resources/META-INF/plugin.xml` should match `until-build="253.*"`

---

## Current Repository Layout

Key source roots:

- `src/main/kotlin/com/nathan/gitlabmr`
- `src/main/resources/META-INF`
- `src/main/resources/icons`

Notable Kotlin modules:

- `GitLabPlugin.kt`
  - plugin companion helpers
  - `GitLabApplicationService`
  - `GitLabProjectService`
- `api/GitLabApiClient.kt`
  - all REST calls
  - auth fallback logic
  - DTO to model mapping
  - MR change list loading
  - repository file content loading
- `api/MRDiffContentLoader.kt`
  - resolves before / after content for MR diffs
- `config/`
  - application-level and project-level persistent config
  - settings UI
- `model/GitLabServer.kt`
  - core models, including MR, changed files, diff refs, diff payloads
- `toolwindow/GitLabToolWindowContent.kt`
  - top-level coordinator
  - MR selection, change loading, diff opening
- `toolwindow/MRDiffService.kt`
  - integrates with IntelliJ native diff viewer
- `toolwindow/components/MRChangesTreePanel.kt`
  - MR changed file tree
  - expand / collapse helpers
- `toolwindow/components/MRDetailsPanel.kt`
  - MR details tabs
  - change tab toolbar visibility and actions
- `toolwindow/components/MRActionToolbar.kt`
  - MR action buttons
  - change-tree expand / collapse actions
- `util/`
  - Git helpers
  - notification helpers

There is still no `src/test` tree.

---

## How The Plugin Actually Works

### Entry points

`plugin.xml` registers:

- tool window: `com.nathan.gitlabmr.toolwindow.GitLabToolWindowFactory`
- application configurable: `GitLabConfigurable`
- project configurable: `GitLabProjectConfigurable`
- application service: `GitLabApplicationService`
- project service: `GitLabProjectService`
- notification group: `GitLab.Notification.Group`

### Tool window flow

`GitLabToolWindowContent` is the main coordinator.

It maintains four card states:

- empty
- error
- loading
- main

Initialization flow:

1. Read project-level selected server first.
2. Else use app-level selected server or first default server.
3. Else fall back to the first project-level server.
4. Else show empty state.

### Project detection flow

`loadData(server)` currently does this:

1. Create `GitLabApiClient`.
2. Call `GitUtil.getMainRepository(project)`.
3. This only succeeds when the project has exactly one Git repo.
4. Read the `origin` remote URL.
5. Extract the GitLab project path from that remote.
6. Call `apiClient.getProject(projectPath)`.
7. Load page 1 of merge requests.

Current fallback behavior:

- No implemented fallback to a configured project path.
- No implemented fallback to “first accessible project”.
- Multi-repo projects currently degrade to an error because `getMainRepository()` returns `null` unless there is exactly one repo.

If project resolution changes, update this document and user-facing docs.

---

## Supported MR Features

Verified from `GitLabToolWindowContent`, `MRListPanel`, `MRDetailsPanel`, `MRActionToolbar`, `MRChangesTreePanel`, and `CreateMRDialog`:

- MR list loading with server-side pagination
- Infinite scroll style load-more
- State filter: `OPENED`, `CLOSED`, `LOCKED`, `MERGED`
- Scope filter: all, created by me, assigned to me
- Title keyword search
- MR details panel
- Open MR in browser
- Close MR
- Merge MR
- Delete MR
- Create MR
- Optional remove-source-branch when creating or merging
- Prefill MR title/description from latest commit on selected source branch
- “merge current branch” helper in create dialog
- Preload branches and members before opening the create dialog
- “变更” tab for MR changed files
- Changed-file tree grouped by module
- Double-click changed file to open IntelliJ native Diff
- Diff support for `ADDED`, `MODIFIED`, `DELETED`, `RENAMED`
- Toolbar actions on the “变更” tab:
  - expand all
  - collapse to module level only

Behavior details:

- `LOCKED` is not a direct GitLab state. The client maps `OPENED + has_conflicts=true` to `MergeRequestState.LOCKED`.
- Delete is enabled only for `OPENED` and `CLOSED`.
- Merge and close are enabled only for `OPENED`.
- Expand/collapse actions are visible only while the “变更” tab is active.
- Collapse should leave only module nodes visible.

---

## API Layer Notes

`GitLabApiClient` is still the single integration point.

Implemented endpoint groups:

- `/user`
- `/projects`
- `/projects/:id`
- `/projects/:id/merge_requests`
- `/projects/:id/merge_requests/:iid`
- `/projects/:id/merge_requests/:iid/changes`
- `/projects/:id/merge_requests/:iid/merge`
- `/projects/:id/repository/files/:path/raw`
- `/projects/:id/repository/branches`
- `/projects/:id/members/all`

Auth behavior:

- Most methods use URL query auth via `private_token`
- Some methods also send `PRIVATE-TOKEN`
- `testConnection()` and `getCurrentUser()` explicitly try URL auth first, then header auth

Implementation notes:

- `apiBaseUrl` now normalizes `server.url` before composing `/api/v4`
- `encodeProjectId()` is used where numeric IDs and path IDs both need support
- Repository file content loading includes basic UTF-8 decode and text/binary detection
- Final binary handling for diffs is based on loaded file content, not just MR patch metadata

---

## Configuration Model

Two persistent services exist:

- app-level: `GitLabConfigService`
- project-level: `GitLabProjectConfigService`

Storage files:

- `GitLabMRConfig.xml`
- `GitLabMRProjectConfig.xml`

Migration note:

- Current behavior does **not** provide compatibility migration from old file names.
- Existing `GitLabConfig.xml` / `GitLabProjectConfig.xml` are not auto-loaded by current code.

Behavior differences:

- `GitLabConfigService.addServer()` only persists servers where `isDefault == true`
- `GitLabProjectConfigService.addServer()` stores non-default / project-scoped servers
- both services dedupe by `url`, not by `id`

Practical consequence:

- the add/edit server flow in the tool window is the main path that correctly routes servers to app or project storage
- `GitLabConfigurable` is less aligned with that model and should be extended carefully

---

## Build And Verification Reality

Expected commands:

```powershell
gradlew.bat buildPlugin
gradlew.bat clean
verify.bat
```

Current verified state:

- `./gradlew.bat buildPlugin` succeeds in this repository
- Local verification basis includes successful builds on `2026-03-31`

Important caveat:

- `buildPlugin` may emit IntelliJ/Gradle warnings during `buildSearchableOptions`
- treat compile success and produced plugin artifact as the actual verification signal unless the warnings indicate a direct regression in touched code

---

## Known Documentation Drift

Older prose docs may still be stale. Verified mismatches that should no longer be repeated:

- old versions such as `1.0.0`, `1.0.3`, or `1.0.4`
- old statements that file diff is not supported
- old statements that change-tree expand/collapse is not supported
- old statements that `apiBaseUrl` uses raw `server.url` without normalization
- old statements that the Gradle wrapper is unusable because of a missing wrapper jar
- old compatibility notes that mention `plugin.xml` and Gradle disagreeing on `untilBuild`

Treat `build.gradle.kts`, `plugin.xml`, and current Kotlin source as authoritative over older prose.

---

## Working Guidelines For Agents

Before changing behavior:

1. Read `build.gradle.kts`, `plugin.xml`, and the directly affected Kotlin file.
2. Check whether a similar action already exists in the tool window flow.
3. Check both config services before changing persistence behavior.

When editing UI code:

- Prefer IntelliJ platform Swing components already used in the repo.
- Keep light/dark theme compatibility.
- Preserve current toolbar/action patterns unless there is a clear reason to refactor.
- If touching the changed-file tree UX, verify both default-expanded and collapsed-to-module behaviors.

When editing async code:

- UI updates should stay on EDT via `ApplicationManager.getApplication().invokeLater`.
- Background network work currently uses either coroutines or `Task.Backgroundable`; follow the local style in the touched file.

When editing Git-related behavior:

- Read `util/GitUtil.kt` first.
- Be careful with the single-repository assumption in `getMainRepository()`.

When editing MR state or diff behavior:

- Check `MergeRequestState` in `model/GitLabServer.kt`.
- Check DTO mapping in `GitLabApiClient`.
- Check enable/disable rules in `MRActionToolbar`.
- Check diff content resolution in `MRDiffContentLoader`.

---

## Recommended Manual Test Pass

If running the plugin in an IDE sandbox, validate at least:

- add a default server from the tool window
- add a project-scoped server
- load MRs from a project with exactly one Git repo
- filter by state, scope, and title keyword
- open MR details and open in browser
- create MR with preloaded branches/members
- use “merge current branch”
- close, merge, and delete an MR
- load the “变更” tab
- double-click a changed text file and verify native Diff opens
- verify added / modified / deleted / renamed file cases
- verify “全部展开”
- verify “全部收起” leaves only module nodes visible
- verify behavior on invalid token
- verify behavior on project with zero repos
- verify behavior on project with multiple repos

---

## Files Worth Reading First

- `build.gradle.kts`
- `src/main/resources/META-INF/plugin.xml`
- `src/main/kotlin/com/nathan/gitlabmr/toolwindow/GitLabToolWindowContent.kt`
- `src/main/kotlin/com/nathan/gitlabmr/api/GitLabApiClient.kt`
- `src/main/kotlin/com/nathan/gitlabmr/api/MRDiffContentLoader.kt`
- `src/main/kotlin/com/nathan/gitlabmr/toolwindow/MRDiffService.kt`
- `src/main/kotlin/com/nathan/gitlabmr/toolwindow/components/MRActionToolbar.kt`
- `src/main/kotlin/com/nathan/gitlabmr/toolwindow/components/MRChangesTreePanel.kt`
- `src/main/kotlin/com/nathan/gitlabmr/util/GitUtil.kt`

---

## Last Updated

- Date: `2026-04-13`
- Basis: direct scan of repository files, review of the latest three commits, and successful local `./gradlew.bat buildPlugin`

## Package Rename (2026-04-13)

Package structure changed from `com.gitlab.idea` to `com.nathan.gitlabmr`. All Kotlin source files moved to new package structure. Updated:

- `build.gradle.kts` group
- `plugin.xml` all class references
- `AGENTS.md` all path references
