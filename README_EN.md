# GitLab MR

**English** · [简体中文](./README.md)

View and manage GitLab Merge Requests directly within IntelliJ IDEA — no need to switch to your browser.

---

## Download & Installation

### Packages

Download the pre-built ZIP from the [Releases](https://github.com/Nathan-Ye/gitlab-mr/releases) page.

Plugin artifact: `build/distributions/gitlab-mr-[version].zip`

**How to install**:
1. Download the ZIP package
2. IntelliJ IDEA: `Settings → Plugins → Install Plugin from Disk...`
3. Restart IDEA

### Requirements

- IntelliJ IDEA **2024.2** or later (Community or Ultimate edition)
- A GitLab server (gitlab.com or self-hosted)
- GitLab **Personal Access Token** with recommended scopes:
  ```
  api, read_api, read_repository, read_milestone, read_issue, read_merge_request
  ```

---

## Features

### MR List & Filtering
- View all Merge Requests in a project
- Filter by state: Open, Closed, Locked, Merged
- Filter by scope: All / Created by me / Assigned to me
- Keyword search by title
- Paginated loading with "Load More"

### MR Details & Actions
- View full MR info: title, description, author, branches, state, timestamps
- Open MR in browser
- Close / Merge / Delete MR
- Create new MR, with title and description pre-filled from the current branch's latest commit

### Changed Files Diff
- Browse changed files tree grouped by module in the "Changes" tab
- Double-click a file to open IntelliJ's native Diff viewer for before/after comparison
- Supports Added, Modified, Deleted, and Renamed text files
- Expand All / Collapse to Module Level

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.0 |
| IntelliJ Platform | IC 2024.2 (Build 242 - 253.*) |
| OkHttp | 4.12.0 |
| Gson | 2.10.1 |
| Java | 21 |
| Gradle | 8.13 |

> Depends on the optional bundled plugin `Git4Idea` for auto-detecting the project from Git remote URL.

---

## Getting Started

### 1. Configure GitLab Server

After the plugin is enabled (`View → Tool Windows → GitLab`), click **+** in the toolbar to add a server:

- **URL**: Your GitLab URL, e.g. `https://gitlab.com`
- **Token**: Personal Access Token
- **Scope**: Choose Application-level (shared across all projects) or Project-level (current project only)

> The plugin prioritizes project-level configuration, then falls back to application-level.

### 2. Auto-detect Project

For single-repository projects, the plugin automatically resolves the GitLab project from the Git remote URL and loads the MR list.

### 3. Start Using

- Click an MR in the list to view details
- Use the filter bar at the top to quickly find what you need
- Close, merge, or delete MRs directly from the detail panel
- Double-click a changed file to view the Diff

---

## Project Structure

```
src/main/kotlin/com/nathan/gitlabmr/
├── GitLabPlugin.kt              # Plugin main class & lifecycle
├── GitLabApplicationService.kt  # Application-level service
├── GitLabProjectService.kt      # Project-level service
├── api/
│   ├── GitLabApiClient.kt       # GitLab REST API client
│   ├── MRDiffContentLoader.kt   # Diff content loader
│   └── MRDiffService.kt         # IntelliJ Diff integration
├── config/
│   ├── GitLabConfigService.kt   # Application-level config persistence
│   ├── GitLabProjectConfigService.kt  # Project-level config persistence
│   └── GitLabServerDialog.kt    # Server config dialog
├── model/
│   └── GitLabServer.kt          # Data models
└── toolwindow/
    ├── GitLabToolWindowFactory.kt    # Tool window factory
    ├── GitLabToolWindowContent.kt    # Tool window content manager
    └── components/
        ├── MRListPanel.kt       # MR list & filtering
        ├── MRDetailsPanel.kt    # MR detail panel
        ├── MRChangesTreePanel.kt # Changed files tree
        └── MRActionToolbar.kt    # MR action toolbar
```

---

## Build from Source

```bash
# Windows
gradlew.bat buildPlugin

# macOS / Linux
./gradlew buildPlugin
```

Output: `build/distributions/gitlab-mr-[version].zip`

Clean: `./gradlew clean`

---

## License

[MIT License](LICENSE)
