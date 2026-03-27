# AGENTS.md

This file gives coding agents a current, repo-specific map of the project. It is intended to be more accurate than older docs when they disagree.

---

## Project Snapshot

- Project type: IntelliJ IDEA plugin
- Plugin name: `GitLab MR`
- Plugin ID: `com.gitlab.idea.integration`
- Current Gradle project version: `1.0.3`
- Main language: Kotlin
- Target JVM: Java 17
- Primary purpose: view and operate GitLab Merge Requests inside the IDE tool window

The UI text and product copy are primarily Chinese. Some source files appear garbled when viewed in a terminal because of encoding/display issues; do not assume the source itself is broken until verified in IDEA.

---

## Verified Stack

From `build.gradle.kts` and `gradle.properties`:

- Kotlin: `2.1.0`
- IntelliJ Platform Gradle Plugin: `2.11.0`
- IntelliJ platform target: `IC 2024.2`
- Gson: `2.10.1`
- OkHttp: `4.12.0`
- Bundled plugin: `Git4Idea`
- Gradle distribution in wrapper properties: `8.13`

Important compatibility detail:

- `build.gradle.kts` sets `untilBuild` to `253.*`
- `src/main/resources/META-INF/plugin.xml` currently declares `until-build="251.*"`

If you touch compatibility metadata, update both places or decide which one is the source of truth and align them.

---

## Current Repository Layout

Key source roots:

- `src/main/kotlin/com/gitlab/idea`
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
- `config/`
  - application-level and project-level persistent config
  - settings UI
- `model/GitLabServer.kt`
  - all core models live here, not just server models
- `toolwindow/`
  - tool window factory
  - main card-layout content controller
  - server dialog
  - create MR dialog
  - confirmation dialog for destructive MR actions
- `toolwindow/components/`
  - empty/loading/error panels
  - MR list
  - MR details
  - toolbars
- `util/`
  - Git helpers
  - notification helpers

There is no `src/test` tree at the moment.

---

## How The Plugin Actually Works

### Entry points

`plugin.xml` registers:

- tool window: `com.gitlab.idea.toolwindow.GitLabToolWindowFactory`
- application configurable: `GitLabConfigurable`
- project configurable: `GitLabProjectConfigurable`
- application service: `GitLabApplicationService`
- project service: `GitLabProjectService`
- notification group: `GitLab.Notification.Group`

### Tool window flow

`GitLabToolWindowContent` is the real coordinator.

It maintains four card states:

- empty
- error
- loading
- main

Initialization flow today:

1. Read project-level selected server first.
2. Else use app-level selected server or first default server.
3. Else fall back to the first project-level server.
4. Else show empty state.

### Project detection flow

The current implementation is narrower than older docs suggest.

`loadData(server)` does this:

1. Create `GitLabApiClient`.
2. Call `GitUtil.getMainRepository(project)`.
3. This only returns a repository when the project has exactly one Git repo.
4. Read the `origin` remote URL.
5. Extract GitLab project path from remote URL.
6. Call `apiClient.getProject(projectPath)`.
7. Load page 1 of merge requests.

Current fallback behavior:

- There is no implemented fallback to "configured project path".
- There is no implemented fallback to "user's first accessible project".
- Multi-repo projects currently degrade to an error because `getMainRepository()` returns `null` unless there is exactly one repo.

If you change project resolution, update this document and the user docs.

---

## Supported MR Features

Verified from `GitLabToolWindowContent`, `MRListPanel`, `MRDetailsPanel`, `MRActionToolbar`, and `CreateMRDialog`:

- MR list loading with server-side pagination
- Infinite scroll style "load more"
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
- "merge current branch" helper in create dialog
- Preload branches and members before opening the create dialog

Behavior detail:

- `LOCKED` is not a direct GitLab state mapping. The client maps `OPENED + has_conflicts=true` to `MergeRequestState.LOCKED`.
- Delete is enabled only for `OPENED` and `CLOSED` in the toolbar logic.
- Merge and close are enabled only for `OPENED`.

---

## API Layer Notes

`GitLabApiClient` is the single integration point.

Implemented endpoint groups:

- `/user`
- `/projects`
- `/projects/:id`
- `/projects/:id/merge_requests`
- `/projects/:id/merge_requests/:iid`
- `/projects/:id/merge_requests/:iid/merge`
- `/projects/:id/repository/branches`
- `/projects/:id/members/all`

Auth behavior:

- Most methods use URL query auth via `private_token`
- Some methods also send `PRIVATE-TOKEN`
- `testConnection()` and `getCurrentUser()` explicitly try URL auth first, then header auth

Implementation caveat:

- `apiBaseUrl` is built from `server.url` directly
- `normalizeUrl()` exists but is not used
- if a stored server URL ends with `/`, requests can become `...//api/v4`
- `GitLabServerDialog.parseServerUrl()` currently normalizes host/protocol, so dialog-created servers are usually safe
- manually edited config through `GitLabConfigurable` can still store unnormalized URLs

---

## Configuration Model

Two persistent services exist:

- app-level: `GitLabConfigService`
- project-level: `GitLabProjectConfigService`

Storage files:

- `GitLabConfig.xml`
- `GitLabProjectConfig.xml`

Behavior differences matter:

- `GitLabConfigService.addServer()` only persists servers where `isDefault == true`
- `GitLabProjectConfigService.addServer()` stores non-default/project-scoped servers
- both services dedupe by `url`, not by `id`

Practical consequence:

- the add/edit server flow in the tool window is the main path that correctly routes servers to app or project storage
- `GitLabConfigurable` is less aligned with that model and may surprise you if you extend it without reading service logic first

---

## Build And Verification Reality

Expected commands:

```powershell
gradlew.bat buildPlugin
gradlew.bat clean
verify.bat
```

Current repository issue:

- `gradle/wrapper/gradle-wrapper.properties` exists
- `gradle/wrapper/gradle-wrapper.jar` is missing
- `./gradlew.bat buildPlugin` currently fails with `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`

That means agents cannot rely on the checked-in wrapper as-is. Before promising build verification, either:

- restore `gradle-wrapper.jar`, or
- use a separately installed Gradle if available and acceptable

Do not describe the wrapper build as verified unless you have fixed that first.

---

## Known Documentation Drift

Older docs in the repo may be stale. Verified mismatches include:

- project version is `1.0.3`, not `1.0.0`
- plugin XML change notes say "Version 1.0.3" but still describe an initial release
- `plugin.xml` and Gradle `patchPluginXml` disagree on `untilBuild`
- old docs mention broader project loading fallbacks that are not implemented now
- old docs imply Linux/macOS wrapper usage, but only `gradlew.bat` is present in the repo

Treat `build.gradle.kts`, `plugin.xml`, and the Kotlin source as authoritative over prose docs.

---

## Working Guidelines For Agents

Before changing behavior:

1. Read `build.gradle.kts`, `plugin.xml`, and the directly affected Kotlin file.
2. Check whether a similar action already exists in the tool window flow.
3. Check both config services before changing persistence behavior.

When editing UI code:

- Prefer IntelliJ platform Swing components already used in the repo.
- Keep light/dark theme compatibility.
- Preserve current action toolbar patterns unless there is a clear reason to refactor.

When editing async code:

- UI updates should stay on EDT via `ApplicationManager.getApplication().invokeLater`.
- Background network work currently uses either coroutines or `Task.Backgroundable`; follow the local style in the touched file.

When editing Git-related behavior:

- Read `util/GitUtil.kt` first.
- Be careful with the single-repository assumption in `getMainRepository()`.

When editing MR state behavior:

- Check both `MergeRequestState` in `model/GitLabServer.kt` and the DTO mapping in `GitLabApiClient`.
- Check enable/disable rules in `MRActionToolbar`.

---

## Recommended Manual Test Pass

If build/run becomes possible, validate at least these cases:

- add a default server from the tool window
- add a project-scoped server
- load MRs from a project with exactly one Git repo
- filter by state, scope, and title keyword
- open MR details and open in browser
- create MR with preloaded branches/members
- use "merge current branch"
- close, merge, and delete an MR
- verify behavior on invalid token
- verify behavior on project with zero repos
- verify behavior on project with multiple repos

---

## Files Worth Reading First

- `build.gradle.kts`
- `src/main/resources/META-INF/plugin.xml`
- `src/main/kotlin/com/gitlab/idea/toolwindow/GitLabToolWindowContent.kt`
- `src/main/kotlin/com/gitlab/idea/api/GitLabApiClient.kt`
- `src/main/kotlin/com/gitlab/idea/config/GitLabConfigService.kt`
- `src/main/kotlin/com/gitlab/idea/config/GitLabProjectConfigService.kt`
- `src/main/kotlin/com/gitlab/idea/util/GitUtil.kt`

---

## Last Updated

- Date: `2026-03-27`
- Basis: direct scan of repository files plus attempted local build execution
