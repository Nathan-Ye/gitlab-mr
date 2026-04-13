# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an IntelliJ IDEA plugin that integrates GitLab Merge Request (MR) functionality directly into the IDE. It provides a tool window for viewing and managing GitLab MRs without leaving the development environment.

**Technology Stack:**
- Kotlin 2.1.0
- IntelliJ Platform 2024.2+ (IDEA Community compatible)
- Gradle 8.13 with Kotlin DSL
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

Output: `build/distributions/gitlab-mr-[version].zip`

### Running the Plugin in Development
1. Open project in IntelliJ IDEA (2024.2+)
2. Configure SDK: File → Project Structure → Project → SDK: Java 17
3. Run → Edit Configurations → Add "Plugin" configuration
4. Click Run (Shift+F10) - launches a sandbox IDEA instance with plugin loaded

### Cleaning
```bash
gradlew.bat clean
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
  - Loading state (`LoadingStatePanel`) - Initial data loading
  - Main content (`MainContentPanel`) - MR list + details split pane
- `GitLabServerDialog.kt` - Dialog for adding/editing GitLab servers
- `CreateMRDialog.kt` - Dialog for creating new Merge Requests
- `MRDiffService.kt` - Integrates with IntelliJ's native Diff viewer
- `ToolWindowMutexManager.kt` - Manages tool window activation mutex

**API Layer:**
- `GitLabApiClient.kt` - Single-class REST API client using OkHttp
  - Supports two authentication methods: URL parameter `private_token` and Header `PRIVATE-TOKEN`
  - Uses coroutines (`suspend` functions) for async operations
  - Key methods:
    - `testConnection()` - Validates credentials via `/user` endpoint
    - `getProject(projectPath)` - Gets project by path (URL-encoded)
    - `getMergeRequests(projectId, state, page, perPage)` - Paginated MR listing
    - `getAllMergeRequests()` - Auto-paginates to fetch all MRs
    - `getMergeRequestChanges(projectId, mrIid)` - Gets MR change files
    - `getMergeRequestVersions(projectId, mrIid)` - Gets MR version history
    - `createMergeRequest()` - Creates a new MR
  - Internal DTOs (`MergeResponseDto`, `AuthorDto`) convert to domain models
- `MRDiffContentLoader.kt` - Loads diff content for MR file comparison
  - Fetches file content at specific commits for diff viewing
  - Handles binary file detection and error cases

**Configuration System:**
- `GitLabConfigService.kt` - `PersistentStateComponent` storing `GitLabServer` configs
  - Application-level storage: `GitLabMRConfig.xml` (IDEA全局配置目录)
  - Methods: `addServer()`, `removeServer()`, `getSelectedServer()`, `setSelectedServer()`, `clearAllDefaultServers()`, `getDefaultServers()`
  - Adding a default server automatically clears other defaults, ensuring only one default server
- `GitLabProjectConfigService.kt` - Project-level storage
  - Storage: `GitLabMRProjectConfig.xml` in project `.idea/` folder
  - Methods: `addServer()`, `updateServer()`, `removeServer()`, `getSelectedServer()`, `setSelectedServer()`
  - Adding a server with duplicate URL updates instead of duplicating
- `GitLabConfigurable.kt` - Global settings UI (application-level)
- `GitLabProjectConfigurable.kt` - Project settings UI

**Actions (`actions/`):**
- `AddServerAction.kt` - Action to add a new GitLab server via dialog
- `RefreshAction.kt` - Action to refresh MR data

**Data Models (`model/GitLabServer.kt`):**
- `GitLabServer` - Server config with id, name, url, token, isDefault
- `GitLabProject` - Project info (id, name, path, webUrl, etc.)
- `GitLabMergeRequest` - MR with all fields (state, branches, author, assignees, etc.)
- `MergeRequestState` - Enum: OPENED, CLOSED, LOCKED, MERGED, CHECKING
- `GitLabUser` - User details
- `GitLabBranch` - Branch info
- `GitLabMember` - Project member info
- `CreateMergeRequestResponse` - Response from MR creation

**UI Components (`toolwindow/components/`):**
- `EmptyStatePanel.kt` - "Add GitLab Server" prompt
- `ErrorStatePanel.kt` - Error display with retry/edit buttons
- `LoadingStatePanel.kt` - Loading state with spinner display
- `MRListPanel.kt` - MR list with:
  - State filter dropdown (待合并/已关闭/有冲突/已合并/全部, 90px wide)
  - Scope filter dropdown (全部/我创建的/指派给我的, 130px wide)
  - Title keyword search field
  - "Load More" button for pagination
  - Clickable MR list (selects to show details)
- `MRDetailsPanel.kt` - MR detail view with tabbed interface (Overview / Changes)
- `MRChangesTreePanel.kt` - MR change file tree view
  - Hierarchical display of changed files grouped by module
  - Expand/collapse all functionality
  - File type icons and change type indicators (Added/Modified/Deleted/Renamed)
  - Double-click to open native IntelliJ Diff viewer
- `MRActionToolbar.kt` - Toolbar with MR action buttons (close, merge, delete, refresh, open in browser)
- `ToolWindowSideToolbar.kt` - Side toolbar for quick actions
- `ChangeTreeIconResolver.kt` - Resolves icons for different file change types

**Utilities (`util/`):**
- `GitUtil.kt` - Git repository helpers: `getRemoteUrl()`, `extractProjectPathFromUrl()`
- `GitLabNotifications.kt` - IDEA notification wrapper

**Dialogs:**
- `GitLabServerDialog.kt` - Add/edit GitLab server configuration
- `CreateMRDialog.kt` - Create new MR with title, description, source/target branch, assignee
- `MRActionConfirmDialog.kt` - Confirmation dialog for destructive MR actions

### Plugin Registration (`plugin.xml`)
- Tool window: id="GitLab", anchor="bottom", secondary=true
- Configurables: Application + Project level
- Services: `GitLabApplicationService` (app), `GitLabProjectService` (project)
- Actions: Refresh, AddServer (with icons)
- Dependencies: `Git4Idea` (optional)
- Platform support: since-build="241", until-build="253.*"

### Configuration Loading Flow

1. Plugin loads → `GitLabToolWindowFactory.createToolWindowContent()`
2. `GitLabToolWindowContent.initialize()` called
3. `loadInitialState()` loads configuration with priority:
   - **Level 1**: Project-level selected server (highest priority)
   - **Level 2**: Application-level selected server or first default server
   - **Level 3**: Any available server in project config (fallback)
   - No servers → Show `EmptyStatePanel`
4. `loadData()` attempts three strategies (in order):
   1. Use `server.projectPath` if configured
   2. Extract project path from Git remote URL via `GitUtil`
   3. Fallback to `getUserProjects()` and use first project
5. On success → Fetch MRs via `getMergeRequests()` → Show `MainContentPanel`
6. On error → Show `ErrorStatePanel`

### Server Configuration Storage

- **Application-level (default server)**:
  - File: `GitLabMRConfig.xml` in IDEA options directory
  - Example: `%APPDATA%\JetBrains\IDEA2025.1\options\GitLabMRConfig.xml`
  - Shared across all projects

- **Project-level**:
  - File: `GitLabMRProjectConfig.xml` in project `.idea/` folder
  - Example: `<ProjectPath>\.idea\GitLabMRProjectConfig.xml`
  - Only for current project

- **Compatibility note**:
  - Current implementation does not migrate old names automatically.
  - Existing `GitLabConfig.xml` / `GitLabProjectConfig.xml` are not read after the rename.

- **Key behaviors**:
  - Only one default server allowed (checking "Set as default" clears other defaults)
  - Same URL server will be updated instead of duplicated
  - Editing server preserves original ID and token

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

### MR Changes Tree & Diff Viewer

- `MRChangesTreePanel` displays changed files in a tree structure grouped by directory/module
- Change types: Added, Modified, Deleted, Renamed (with different icons)
- Expand/collapse operations: "Expand All" and "Collapse to Module Level"
- Double-click on file triggers native IntelliJ Diff viewer via `MRDiffService`
- `MRDiffContentLoader` fetches file content at base and head commits for comparison
- Binary files are detected and handled gracefully

### Create MR Dialog

- Pre-fills title and description from current branch's latest commit
- Supports selecting source branch, target branch, and assignee
- Option to delete source branch after merge
- Integrates "Merge current branch" shortcut action

## Important Development Notes

1. **Project Matching**: The plugin tries multiple strategies to find the GitLab project. If a server has `projectPath` configured, it uses that. Otherwise, it extracts from Git remote URLs or falls back to user's first project.

2. **Coroutines on EDT**: All UI updates must use `ApplicationManager.getApplication().invokeLater {}` or `launch { }` with `Dispatchers.Main`. The project reference in `GitLabApiClient` enables background task execution.

3. **Disposable Pattern**: Both `GitLabApiClient` and `GitLabToolWindowContent` implement `Disposable`. Always clean up coroutine scopes in `dispose()`.

4. **Proxy Settings**: `gradle.properties` contains proxy config (http.proxyHost/Port). Remove or modify if not needed.

5. **Git4Idea Dependency**: Marked optional in `plugin.xml`. Plugin works without Git integration but features requiring it (like remote URL extraction) will be limited.

6. **Build Compatibility**: Plugin targets IntelliJ 2024.2+ (build 241) through 2025.3 (253.*). Modify `patchPluginXml` block in `build.gradle.kts` to change range.

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
- **Multi-repository projects**: Not currently supported for automatic project resolution.
