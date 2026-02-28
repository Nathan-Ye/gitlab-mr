# AGENTS.md

This file provides guidance to AI coding agents when working with this repository.

---

## Project Overview

This is an **IntelliJ IDEA plugin** that integrates GitLab Merge Request (MR) functionality directly into the IDE. It provides a tool window for viewing and managing GitLab MRs without leaving the development environment.

**Plugin Name:** GitLab MR  
**Plugin ID:** `com.gitlab.idea.integration`  
**Version:** 1.0.0  
**Language:** 中文 (Chinese)

### Key Features
- View and manage GitLab Merge Requests in a dedicated tool window
- Filter MRs by state (Opened, Closed, Locked, Merged) and username
- Auto-detect GitLab projects from Git remote URLs
- Multi-server support (GitLab.com and self-hosted instances)
- Create new Merge Requests from IDE
- View detailed MR information including branches, author, assignees, reviewers

---

## Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 2.1.0 | Programming language |
| IntelliJ Platform | 2024.2+ (build 241+) | Plugin SDK |
| Gradle | 8.4+ | Build tool with Kotlin DSL |
| JDK | 17 | Target JVM version |
| OkHttp | 4.12.0 | HTTP client for API calls |
| Gson | 2.10.1 | JSON serialization/deserialization |
| kotlinx-coroutines | Bundled with IntelliJ | Async operations |

### Platform Compatibility
- **Since Build:** 241 (IntelliJ IDEA 2024.2)
- **Until Build:** 253.* (IntelliJ IDEA 2025.3)
- **IDE Compatibility:** IntelliJ IDEA Community/Ultimate 2024.2+
- **Optional Dependency:** Git4Idea (for Git integration features)

---

## Build and Development Commands

### Prerequisites
- JDK 17 installed and configured
- IntelliJ IDEA 2023.2 or later (for development)

### Building

```bash
# Windows
gradlew.bat buildPlugin

# macOS/Linux
./gradlew buildPlugin
```

**Output:** `build/distributions/gitlab-idea-plugin-1.0.0.zip`

### Other Commands

```bash
# Clean build artifacts
gradlew.bat clean

# Verification build (Windows only)
verify.bat
```

### Development Workflow

1. **Open project in IntelliJ IDEA**
2. **Configure SDK:** File → Project Structure → Project → SDK: Java 17
3. **Run Configuration:**
   - Run → Edit Configurations → Add "Plugin" configuration
   - VM options (optional): `-Xmx2g`
4. **Run/Debug:** Click Run (Shift+F10) or Debug (Shift+F9)
   - This launches a sandbox IDEA instance with the plugin loaded

---

## Project Structure

```
src/
└── main/
    ├── kotlin/com/gitlab/idea/           # Kotlin source code
    │   ├── GitLabPlugin.kt               # Plugin main entry point
    │   ├── actions/                      # User actions
    │   │   ├── AddServerAction.kt        # Add GitLab server action
    │   │   └── RefreshAction.kt          # Refresh MR data action
    │   ├── api/                          # GitLab API layer
    │   │   └── GitLabApiClient.kt        # REST API client (OkHttp + Gson)
    │   ├── config/                       # Configuration management
    │   │   ├── GitLabConfigurable.kt     # Global settings UI
    │   │   ├── GitLabConfigService.kt    # Persistent config storage
    │   │   ├── GitLabProjectConfigurable.kt  # Project-level settings
    │   │   └── GitLabProjectConfigService.kt # Project config storage
    │   ├── model/                        # Data models
    │   │   └── GitLabServer.kt           # Server, MR, User, Project data classes
    │   ├── toolwindow/                   # Tool window UI
    │   │   ├── GitLabToolWindowFactory.kt    # Tool window factory
    │   │   ├── GitLabToolWindowContent.kt    # Content manager (CardLayout)
    │   │   ├── GitLabServerDialog.kt         # Add/edit server dialog
    │   │   ├── CreateMRDialog.kt             # Create MR dialog
    │   │   ├── ToolWindowMutexManager.kt     # UI state management
    │   │   └── components/                   # UI components
    │   │       ├── EmptyStatePanel.kt        # No servers configured
    │   │       ├── ErrorStatePanel.kt        # Error display
    │   │       ├── LoadingStatePanel.kt      # Loading indicator
    │   │       ├── MRListPanel.kt            # MR list with filters
    │   │       ├── MRDetailsPanel.kt         # MR detail view
    │   │       ├── MRActionToolbar.kt        # MR action buttons
    │   │       └── ToolWindowSideToolbar.kt  # Side toolbar
    │   └── util/                         # Utility classes
    │       ├── GitLabNotifications.kt    # Notification helpers
    │       └── GitUtil.kt                # Git repository utilities
    └── resources/META-INF/               # Plugin resources
        ├── plugin.xml                    # Plugin configuration
        └── gitlab-git.xml                # Git4Idea dependency config
```

---

## Architecture Details

### Plugin Entry Points

1. **GitLabPlugin.kt** - Main plugin class with version info and static helpers
2. **GitLabApplicationService** - Application-level service (initialized on plugin load)
3. **GitLabProjectService** - Project-level service, provides `hasGitRepository()`

### Tool Window System

The tool window uses a **CardLayout** to switch between states:

1. **EmptyStatePanel** - No servers configured
2. **ErrorStatePanel** - Loading/connection failures
3. **LoadingStatePanel** - Data loading in progress
4. **MainContentPanel** - MR list + details split view (from `GitLabToolWindowContent`)

### API Layer

**GitLabApiClient.kt** - Single-class REST API client:
- Uses OkHttp with 30-second timeouts
- Supports two authentication methods:
  - URL parameter: `?private_token={token}` (browser-compatible)
  - Header: `PRIVATE-TOKEN: {token}` (standard, more reliable)
- Uses Kotlin coroutines (`suspend` functions) for async operations
- Key methods:
  - `testConnection()` - Validates credentials via `/user` endpoint
  - `getProject(projectPath)` - Gets project by path (URL-encoded)
  - `getMergeRequests()` - Paginated MR listing
  - `getAllMergeRequests()` - Auto-paginates to fetch all MRs
  - `createMergeRequest()` - Creates new MR

### Configuration System

**GitLabConfigService.kt** - Application-level persistent storage:
- Implements `PersistentStateComponent`
- Storage: `GitLabConfig.xml` in IDEA config directory
- Methods: `addServer()`, `removeServer()`, `getServers()`, `getSelectedServer()`

**GitLabProjectConfigService.kt** - Project-level configuration:
- Per-project server associations

### Data Models

Located in `model/GitLabServer.kt`:

| Class | Description |
|-------|-------------|
| `GitLabServer` | Server config (id, name, url, token, isDefault) |
| `GitLabProject` | Project info (id, name, path, webUrl, etc.) |
| `GitLabMergeRequest` | MR with all fields (state, branches, author, assignees, reviewers, etc.) |
| `MergeRequestState` | Enum: OPENED, CLOSED, LOCKED, MERGED |
| `GitLabUser` | User details (id, username, name, avatarUrl, etc.) |
| `GitLabBranch` | Branch information |
| `GitLabCommit` | Commit details |
| `CreateMergeRequestRequest/Response` | MR creation DTOs |
| `GitLabApiResponse<T>` | Generic API response wrapper |

### Configuration Loading Flow

1. Plugin loads → `GitLabToolWindowFactory.createToolWindowContent()`
2. `GitLabToolWindowContent.initialize()` called
3. Checks `GitLabConfigService` for servers:
   - No servers → Show `EmptyStatePanel`
   - Has selected server → Call `loadData(server)`
4. `loadData()` attempts multiple strategies:
   1. Use configured project path if available
   2. Extract project path from Git remote URL via `GitUtil`
   3. Fallback to user's first accessible project
5. On success → Fetch MRs → Show main content
6. On error → Show `ErrorStatePanel`

---

## GitLab API Integration

- **Base URL:** `{serverUrl}/api/v4`
- **Authentication:** Private Token via header or URL parameter
- **Project Path Encoding:** Must URL-encode paths: `group/subgroup/project` → `group%2Fsubgroup%2Fproject`

### API Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/user` | GET | Get current user info |
| `/projects` | GET | List user's projects |
| `/projects/:id` | GET | Get project details |
| `/projects/:id/merge_requests` | GET | List MRs |
| `/projects/:id/merge_requests/:iid` | GET | Get single MR |
| `/projects/:id/merge_requests` | POST | Create MR |
| `/projects/:id/merge_requests/:iid/merge` | PUT | Merge MR |
| `/projects/:id/merge_requests/:iid` | DELETE | Close/Delete MR |

---

## Important Development Notes

### 1. Coroutines and Threading

All UI updates must be on the EDT (Event Dispatch Thread):

```kotlin
// Background work with UI update
launch(Dispatchers.IO) {
    val data = fetchData()  // Network call
    withContext(Dispatchers.Main) {
        updateUI(data)      // UI update
    }
}

// Alternative using invokeLater
ApplicationManager.getApplication().invokeLater {
    updateUI(data)
}
```

### 2. Disposable Pattern

Both `GitLabApiClient` and `GitLabToolWindowContent` implement `Disposable`. Always clean up coroutine scopes in `dispose()`:

```kotlin
override fun dispose() {
    coroutineScope.cancel()
}
```

### 3. Project Path Encoding

Always URL-encode project paths containing slashes:

```kotlin
import java.net.URLEncoder
val encodedPath = URLEncoder.encode(path, "UTF-8")
```

### 4. Authentication Fallback

`testConnection()` tries both URL parameter and header methods. Header method is more reliable for self-hosted instances.

### 5. Error Handling

API errors return `GitLabApiResponse` with `success=false`. Network exceptions should be caught and converted to error responses.

### 6. Git4Idea Dependency

Marked optional in `plugin.xml`. Plugin works without Git integration, but features like remote URL extraction will be limited.

---

## Code Style Guidelines

### Kotlin Style
- Follow Kotlin coding conventions
- Use official Kotlin code style (`kotlin.code.style=official` in gradle.properties)
- Prefer immutable data classes (`val` over `var`)
- Use Kotlin coroutines for async operations

### Naming Conventions
- Classes: PascalCase
- Functions/Variables: camelCase
- Constants: UPPER_SNAKE_CASE
- Package: `com.gitlab.idea.*`

### UI Code
- Use IntelliJ Platform UI components (JBPanel, JBLabel, etc.)
- Follow IDEA's visual design guidelines
- Support both Light and Dark themes

---

## Testing Instructions

### Manual Testing Steps

1. **Create GitLab Access Token:**
   - Go to GitLab → Settings → Access Tokens
   - Scopes needed: `api`, `read_api`, `read_repository`, `read_milestone`, `read_issue`, `read_merge_request`

2. **Configure Plugin in Sandbox:**
   - Run plugin (creates sandbox IDEA)
   - Open any project
   - View → Tool Windows → GitLab
   - Click "+" to add server
   - Enter GitLab URL and token
   - Test connection

3. **Verify Functionality:**
   - MR list loads automatically
   - State filters work
   - Username filter works
   - Click MR to see details
   - Refresh button works

### Common Test Scenarios

- Self-hosted GitLab instance
- GitLab.com
- Project with many MRs (pagination)
- No Git repository in project
- Invalid/expired token
- Network disconnection

---

## Security Considerations

1. **Token Storage:** API tokens are stored in IDEA's secure storage via `PersistentStateComponent`. Never log tokens.

2. **HTTPS Only:** Always use HTTPS URLs for production GitLab instances.

3. **Token Scope:** Document minimum required scopes for API tokens.

4. **No Token in Logs:** Ensure API tokens are not included in error messages or logs.

---

## Troubleshooting

### Build Issues

| Problem | Solution |
|---------|----------|
| Gradle sync fails | Check JDK 17 is set in Project Structure |
| Dependencies not found | Check network connection, consider adding Maven mirrors |
| Build fails with memory error | Increase heap size in `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m` |

### Runtime Issues

| Problem | Solution |
|---------|----------|
| Plugin not loading | Check IDEA version >= 2024.2, check `Help → Show Log` |
| Connection fails | Verify URL format (no trailing slash), check token permissions |
| MRs not loading | Check Git remote URL points to GitLab, verify project access |
| UI looks wrong | Check theme compatibility, clear IDEA caches |

---

## Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | User-facing documentation (中文) |
| `DEVELOPMENT.md` | Detailed development guide (中文) |
| `QUICKSTART.md` | Quick start guide (中文) |
| `PROJECT_SUMMARY.md` | Project status and roadmap (中文) |
| `CLAUDE.md` | Claude Code specific guidance (English) |
| `AGENTS.md` | This file - AI agent guidance |

---

## Related Resources

- [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/)
- [GitLab REST API Docs](https://docs.gitlab.com/ee/api/)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

---

**Last Updated:** 2026-02-27
