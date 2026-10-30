# PROJECT JARVIS — Native Android Personal AI Assistant

**JARVIS** is a native Android personal AI assistant built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase AI Logic (Gemini 3.8 Flash & Gemini 3.8 Live API)**, **Google Workspace OAuth 2.0**, **Unlimited Custom Model Context Protocol (MCP) Server Integration**, **Room Database**, and **Android Keystore AES-GCM Encryption**.

---

## 1. System Architecture Overview

- **UI Layer (`com.example.ui`)**:
  - **Home HUD (`HomeScreen.kt`)**: Animated 60fps canvas Voice Core Visualizer, live audio RMS ring, status pills (`IDLE`, `WAKE_WORD_STANDBY`, `LISTENING`, `PROCESSING`, `SPEAKING`, `GEMINI_LIVE_STREAMING`), quick-action command bar, multimodal file/photo/screen attachment cards, and mandatory confirmation dialogs for sensitive phone, Google Workspace, and MCP actions.
  - **Dedicated AI Chat (`ChatScreen.kt`)**: Multi-session conversation drawer, streaming Markdown renderer with code copy buttons, voice input trigger, spoken response playback, and web research source citations.
  - **Device Control, Tasks, Google Integrations & MCP (`ControlAndTasksScreen.kt` & `IntegrationsAndMcpScreen.kt`)**:
    - **Multi-Step AI Task Planner**: Breaks complex goals into step-by-step plans with `Execute`, `Pause/Resume`, and `Cancel` controls.
    - **Google & Third-Party App Integrations (`GoogleIntegrationsSection`)**: Official Google OAuth 2.0 connection manager, Google Drive Explorer & AI Document Summarizer, Google Calendar Upcoming Events, Gmail Search & Confirmed Drafts, Google Contacts Lookup, YouTube Playlists, and Zero-Permission Google Photos Picker.
    - **Unlimited Custom MCP Server Manager (`McpServerManagementSection`)**: Add, edit, enable/disable, test, disconnect, delete, and import/export unlimited MCP servers (`Streamable HTTP`, `HTTP + SSE`, and `Remote STDIO Gateway Bridge`), inspect discovered tools (`READ_ONLY`, `WRITE`, `DESTRUCTIVE`), toggle per-tool approvals, and review the MCP Security Audit Log.
    - **Phone & Apps, Timers & Alarms, and Notification Assistant**.
  - **Memory Vault & System Settings (`MemoryAndSettingsScreen.kt`)**:
    - **User-Controlled Memory Vault**: Strictly separated from chat history; personal facts require explicit user approval (`AWAITING YOUR APPROVAL` -> `Approve`).
    - **System Settings**: Language mode (`Auto`, `English`, `Hindi`, `Hinglish`), Wake-Word & Floating Orb controls, Gemini model & encrypted API key vault, ElevenLabs & Android TTS voice controls, dedicated **Integrations** view, dedicated **MCP Server Manager** view, and one-tap **Purge All Local Data & Credentials**.
- **AI Brain (`com.example.ai.JarvisAiBrain`)**:
  - **Primary Provider**: Provisioned **Firebase AI Logic (`Firebase.ai(backend = GenerativeBackend.googleAI())`)** with **Firebase App Check** (Debug provider in debug builds, Play Integrity in release builds).
  - **Optional Custom Key Mode**: Direct HTTPS streaming and non-streaming requests to `generativelanguage.googleapis.com` using a user-supplied Gemini API key stored in Android Keystore (`AES/GCM/NoPadding`).
  - **Gemini 3.8 Live Voice**: Real-time bidirectional voice sessions using `liveModel("gemini-2.0-flash-live-preview-04-09")`.
- **Google Workspace & Third-Party Integrations (`com.example.integrations.GoogleWorkspaceClient`)**:
  - Uses Jetpack `CredentialManager` (`GetGoogleIdOption` with `context.getString(R.string.default_web_client_id)`) + `GoogleAuthUtil.getTokenWithNotification` to obtain OAuth 2.0 access tokens for authorized scopes:
    - `https://www.googleapis.com/auth/drive` (Google Drive API v3: search, list, read/export Google Docs/Sheets/PDFs, summarize with Gemini, upload files, create folders, rename/move, download, and confirmation-gated delete)
    - `https://www.googleapis.com/auth/gmail.readonly` & `https://www.googleapis.com/auth/gmail.compose` (Gmail API v1: search messages by sender/query, confirmation-gated draft creation)
    - `https://www.googleapis.com/auth/calendar.events` (Google Calendar API v3: list upcoming events)
    - `https://www.googleapis.com/auth/contacts.readonly` (Google People API v1: search contacts)
    - `https://www.googleapis.com/auth/youtube.readonly` (YouTube Data API v3: list playlists)
  - Never requests or stores Google passwords.
- **Model Context Protocol (MCP) Client Engine (`com.example.mcp.McpClientEngine`)**:
  - Implements official MCP JSON-RPC 2.0 (`2025-03-26` & `2024-11-05`) lifecycle: `initialize` -> `notifications/initialized` -> `tools/list` -> `tools/call`.
  - Supports **Streamable HTTP** (`Mcp-Session-Id` header + JSON or `text/event-stream` responses), **HTTP + SSE**, and **Remote STDIO Gateway Bridge**.
  - Enforces per-server concurrency limits (`Semaphore(2)`), automatic tool safety classification (`READ_ONLY`, `WRITE`, `DESTRUCTIVE`), prompt-injection detection & sanitization on tool metadata and outputs, mandatory user confirmation before `WRITE`/`DESTRUCTIVE` tool execution, and full audit logging.

---

## 2. How to Connect Google Drive & Google Services

1. Open **JARVIS** and go to **Settings → Integrations (Google & Apps)** (or **Control → Google & Apps**).
2. Tap **Connect Google Account** to select your Google account via Android's official Credential Manager and approve the requested OAuth 2.0 permissions.
3. Tap **Test Connection** to verify live token validity and granted scopes.
4. Switch to the **Google Drive** tab (or use voice/text commands such as *"Hey JARVIS, find my PDF in Drive"*, *"Summarize my selected Google Drive document"*, *"Create a folder in Drive"*, *"Show my upcoming Calendar events"*, or *"Find the latest email from [sender]"*).
5. If testing on a headless cloud emulator without Google Play Services sign-in configured, tap **OAuth Token Fallback** to paste a temporary OAuth 2.0 Bearer access token (never your password) to test live Drive/Calendar/Gmail API calls.

---

## 3. How to Add Unlimited Custom MCP Servers

1. Open **Settings → MCP Server Manager** (or **Control → MCP Servers**).
2. Tap **Add Custom MCP Server** and enter:
   - **Server Name** (e.g., `GitHub MCP`, `Filesystem Gateway`, `Custom Knowledge Base`)
   - **Endpoint URL** (`https://your-mcp-server.example.com/mcp`)
   - **Transport Type**:
     - `Streamable HTTP (MCP 2025-03-26)`
     - `HTTP + SSE (Server-Sent Events)`
     - `Remote STDIO Gateway Bridge (HTTP)`
   - **Authentication**: `None`, `Bearer Token`, `API Key Header`, `OAuth 2.0 Access Token`, or `Custom Header` (stored in Android Keystore AES-GCM).
3. Tap **Save Server**, then tap **Connect & Discover Tools** on the server card.
4. Switch to **Discovered Tools** to inspect input schemas, safety badges (`READ_ONLY`, `WRITE`, `DESTRUCTIVE`), toggle per-tool approval switches, or invoke tools directly.
5. **Android OS Limitation on Local Desktop STDIO Servers**:
   - Android sandboxes mobile apps and prohibits spawning arbitrary desktop Node.js (`npx`), Python (`uvx`), or Docker STDIO subprocesses inside a standard mobile APK.
   - To use a desktop STDIO-only MCP server with JARVIS on Android, run it behind an HTTP/SSE bridge (such as `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-..."`) and enter its `https://` gateway URL in JARVIS.
