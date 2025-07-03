# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jellyfin Android TV is a Kotlin-based Android TV application for the Jellyfin media server. It uses a multi-module architecture with modern Android development practices including Kotlin coroutines, Flow, and partial Jetpack Compose adoption.

## Essential Commands

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

### Testing and Code Quality
```bash
# Run all tests
./gradlew test

# Run linting (Detekt + Android Lint)
./gradlew detekt lint

# Run specific module tests
./gradlew :app:test
./gradlew :playback:core:test
```

### Development Workflow
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Generate APK for distribution
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/
```

## Architecture Overview

### Module Structure
- **`:app`** - Main Android TV application with UI, DI, and business logic
- **`:playback:core`** - Core playback abstractions and interfaces
- **`:playback:jellyfin`** - Jellyfin-specific playback implementation
- **`:playback:media3:exoplayer`** - ExoPlayer media player integration
- **`:playback:media3:session`** - Media session management
- **`:preference`** - Shared preference management module

### Key Architectural Patterns
1. **Hybrid Architecture**: MVVM for new features, MVP-like for legacy code
2. **Repository Pattern**: All data access through repository interfaces
3. **Dependency Injection**: Koin framework with modular configuration
4. **Reactive Programming**: Kotlin Flow and StateFlow for data streams
5. **Plugin Architecture**: Extensible playback system

### Core Components

#### Dependency Injection (`/app/src/main/java/org/jellyfin/androidtv/di/`)
- `AppModule.kt` - Core dependencies, Jellyfin SDK client, repositories
- `PlaybackModule.kt` - Media playback dependencies
- `AuthModule.kt` - Authentication components
- `PreferenceModule.kt` - User preferences
- `UtilsModule.kt` - Utility classes

#### Data Layer (`/app/src/main/java/org/jellyfin/androidtv/data/`)
- Repository interfaces with implementation classes
- Coroutine-based API calls
- Flow-based reactive data streams
- Key repositories: `SessionRepository`, `ItemMutationRepository`, `NavigationRepository`

#### Navigation System
- Custom navigation using `NavigationRepository`
- Fragment-based with back stack management
- Deep linking via `Destinations` object

#### Playback Architecture
- Modular design with plugin system
- `PlaybackManager` as central coordinator
- Queue management and media resolution
- ExoPlayer integration for media playback

### Code Style and Conventions
- **Language**: Kotlin (Java for minimal legacy code)
- **Indentation**: Tabs with width 4
- **Max line length**: 140 characters
- **Imports**: No wildcards
- **Naming**: Interface + Impl pattern for repositories
- **Async**: Coroutines with proper scope management

## Important Development Notes

### Jellyfin SDK Configuration
The project can use different SDK versions via `sdk.version` property:
- `"default"` - Published SDK version
- `"local"` - Local SDK build
- `"snapshot"` - Snapshot builds
- `"unstable-snapshot"` - Unstable snapshot builds

### Testing Device/Emulator
When testing, use an Android TV emulator or physical device. The app is optimized for TV interfaces and may not work correctly on phones/tablets.

### Key Dependencies
- **Jellyfin SDK** - Server API communication
- **AndroidX Leanback** - TV UI components
- **Koin** - Dependency injection
- **Media3/ExoPlayer** - Video playback
- **Coil** - Image loading
- **Timber** - Logging
- **Compose** - Modern UI (partial adoption)

### Common Development Tasks

#### Adding a New Feature
1. Create repository interface in `data/` if needed
2. Implement repository with proper DI registration
3. Create ViewModel for business logic
4. Build UI using Fragments (legacy) or Compose (preferred for new features)
5. Register in appropriate DI module
6. Add navigation handling if needed

#### Modifying Playback Behavior
1. Check if changes belong in `:playback:core` (abstractions) or `:playback:jellyfin` (implementation)
2. Maintain plugin architecture compatibility
3. Test with various media formats

#### Working with TV UI
- Use Leanback components for consistency
- Ensure proper focus handling for remote control navigation
- Test on actual TV or emulator with D-pad input
- Follow card-based UI patterns

### Debugging Tips
- Enable LeakCanary by setting the flag in build config
- Use Timber for logging (automatically bridges SLF4J)
- Debug builds have `.debug` suffix for parallel installation
- Check `NavigationRepository` for navigation issues

### CI/CD Considerations
- GitHub Actions run on every PR
- Tests run on Ubuntu 24.04 with Java 21
- Linting includes Detekt and Android Lint
- SARIF reports uploaded for code scanning