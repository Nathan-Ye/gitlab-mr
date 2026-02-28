# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an IntelliJ IDEA plugin that integrates GitLab Merge Request (MR) functionality directly into the IDE. It provides a tool window for viewing and managing GitLab MRs without leaving the development environment.

**Technology Stack:**
- Kotlin 2.1.0
- IntelliJ Platform 2024.2+ (IDEA Community compatible)
- Gradle 8.4+ with Kotlin DSL
- OkHttp 4.12.0 for HTTP
- Gson 2.10.1 for JSON
- Kotlin Coroutines for async operations

## Build and Development Commands

### Building the Plugin
```bash
# Windows
gradlew.bat buildPlugin

# macOS/Linux
./gradlew buildPlugin
```

Output: `build/distributions/gitlab-idea-plugin-1.0.0.zip`

### Running the Plugin in Development
1. Open project in IntelliJ IDEA (2023.2+)
2. Configure SDK: File → Project Structure → Project → SDK: Java 17
3. Run → Edit Configurations → Add "Plugin" configuration
4. Click Run (Shift+F10) - launches a sandbox IDEA instance with plugin loaded

### Cleaning
```bash
gradlew.bat clean
```

### Verifying Build
```bash
verify.bat    # Windows script that runs clean buildPlugin
```

## Architecture

### Core Components

**Entry Point:**
- `GitLabPlugin.kt` - Main plugin class with version info and static helpers
- `GitLabApplicationService` - Application-level service (initialized on plugin load)
- `GitLabProjectService` - Project-level service, provides `hasGitRepository()`

**Tool Window System:**
- `GitLabToolWindowFactory.kt` - Factory registered in `plugin.xml`, creates tool window instances
- `GitLabToolWindowContent.kt` - Main content manager using CardLayout to switch between:
  - Empty state (`EmptyStatePanel`) - No servers configured
  - Error state (`ErrorStatePanel`) - Loading/connection failures
  - Main content (`MainContentPanel`) - MR list + details split pane
- `GitLabServerDialog.kt` - Dialog for adding/editing GitLab servers

**API Layer:**
- `GitLabApiClient.kt` - Single-class REST API client using OkHttp
  - Supports two authentication methods: URL parameter `private_token` and Header `PRIVATE-TOKEN`
  - Uses coroutines (`suspend` functions) for async operations
  - Key methods:
    - `testConnection()` - Validates credentials via `/user` endpoint
    - `getProject(projectPath)` - Gets project by path (URL-encoded)
    - `getMergeRequests(projectId, state, page, perPage)` - Paginated MR listing
    - `getAllMergeRequests()` - Auto-paginates to fetch all MRs
  - Internal DTOs (`MergeResponseDto`, `AuthorDto`) convert to domain models

**Configuration System:**
- `GitLabConfigService.kt` - `PersistentStateComponent` storing `GitLabServer` configs
  - Application-level storage: `GitLabConfig.xml`
  - Methods: `addServer()`, `removeServer()`, `getSelectedServer()`, `setSelectedServer()`
- `GitLabConfigurable.kt` - Global settings UI (application-level)
- `GitLabProjectConfigurable.kt` - Project settings UI

**Data Models (`model/GitLabServer.kt`):**
- `GitLabServer` - Server config with id, name, url, token, isProjectLevel, projectPath
- `GitLabProject` - Project info (id, name, path, webUrl, etc.)
- `GitLabMergeRequest` - MR with all fields (state, branches, author, assignees, etc.)
- `MergeRequestState` - Enum: OPENED, CLOSED, LOCKED, MERGED
- `GitLabUser` - User details

**UI Components (`toolwindow/components/`):**
- `EmptyStatePanel.kt` - "Add GitLab Server" prompt
- `ErrorStatePanel.kt` - Error display with retry/edit buttons
- `MRListPanel.kt` - MR list with:
  - State filter dropdown (Opened/Closed/Locked/Merged/All)
  - Username filter text field
  - "Load More" button for pagination
  - Clickable MR list (selects to show details)
- `MRDetailsPanel.kt` - MR detail view

**Utilities:**
- `GitUtil.kt` - Git repository helpers: `getRemoteUrl()`, `extractProjectPathFromUrl()`
- `GitLabNotifications.kt` - IDEA notification wrapper

### Plugin Registration (`plugin.xml`)
- Tool window: id="GitLab", anchor="bottom", secondary=true
- Configurables: Application + Project level
- Services: `GitLabApplicationService` (app), `GitLabProjectService` (project)
- Actions: Refresh, AddServer (with icons)
- Dependencies: `Git4Idea` (optional)
- Platform support: since-build="241", until-build="251.*"

### Configuration Loading Flow

1. Plugin loads → `GitLabToolWindowFactory.createToolWindowContent()`
2. `GitLabToolWindowContent.initialize()` called
3. Checks `GitLabConfigService` for servers:
   - No servers → Show `EmptyStatePanel`
   - Has selected server → Call `loadData(server)`
4. `loadData()` attempts three strategies (in order):
   1. Use `server.projectPath` if configured
   2. Extract project path from Git remote URL via `GitUtil`
   3. Fallback to `getUserProjects()` and use first project
5. On success → Fetch MRs via `getMergeRequests()` → Show `MainContentPanel`
6. On error → Show `ErrorStatePanel`

### GitLab API Integration

- Base URL: `{serverUrl}/api/v4`
- Authentication preference order:
  1. URL parameter: `?private_token={token}` (browser-compatible)
  2. Header: `PRIVATE-TOKEN: {token}` (standard)
- Project paths must be URL-encoded: `group/subgroup/project` → `group%2Fsubgroup%2Fproject`
- All API calls are suspend functions (coroutine-based)
- Pagination: 100 items per page for `getAllMergeRequests()`, 20 for regular calls

### State Management

- Tool window content uses `CardLayout` for state switching
- MR data stored in `GitLabToolWindowContent`: `mergeRequests` (full list) + `filteredMergeRequests`
- Filtering is client-side after data loads
- Pagination state: `currentPage`, `hasMore`, `isLoadingMore`

## Important Development Notes

1. **Project Matching**: The plugin tries multiple strategies to find the GitLab project. If a server has `projectPath` configured, it uses that. Otherwise, it extracts from Git remote URLs or falls back to user's first project.

2. **Coroutines on EDT**: All UI updates must use `ApplicationManager.getApplication().invokeLater {}` or `launch { }` with `Dispatchers.Main`. The project reference in `GitLabApiClient` enables background task execution.

3. **Disposable Pattern**: Both `GitLabApiClient` and `GitLabToolWindowContent` implement `Disposable`. Always clean up coroutine scopes in `dispose()`.

4. **Proxy Settings**: `gradle.properties` contains proxy config (http.proxyHost/Port). Remove or modify if not needed.

5. **Git4Idea Dependency**: Marked optional in `plugin.xml`. Plugin works without Git integration but features requiring it (like remote URL extraction) will be limited.

6. **Build Compatibility**: Plugin targets IntelliJ 2024.2+ (build 241) through 2025.1 (251.*). Modify `patchPluginXml` block in `build.gradle.kts` to change range.

## Testing the Plugin

1. Use a test GitLab account (e.g., gitlab.com free tier)
2. Create a personal access token with scopes: `api`, `read_api`, `read_repository`, `read_milestone`, `read_issue`, `read_merge_request`
3. In sandbox IDEA: View → Tool Windows → GitLab
4. Click "+" and add server with URL `https://gitlab.com` and your token
5. Tool window should auto-detect project from Git remote or prompt for configuration

## Known Issues / Gotchas

- **Project Path Encoding**: Always URL-encode project paths containing slashes: `URLEncoder.encode(path, "UTF-8")`
- **Authentication Fallback**: `testConnection()` tries both URL parameter and header methods. Header method is more reliable for self-hosted instances.
- **Empty State on Startup**: If no servers are configured, the tool window shows empty state. User must click "+" to add.
- **Git Remote Matching**: Requires Git4Idea plugin. If not available, falls back to manual project selection.
