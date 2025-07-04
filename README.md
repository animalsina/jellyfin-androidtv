# Jellyfin Android TV - Modern UI Overhaul

## A Fresh Take on Home Entertainment

This fork focuses on giving Jellyfin Android TV a modern streaming service look and feel. The goal is to create a more polished, contemporary interface that feels familiar and makes you want to scroll through your collection.

![Demo of the new interface](Jellyfin_Home_UI_Demo.gif)

## What I'm Building

### Modern Home Interface
I'm redesigning the home screen to match the visual language of modern streaming services - clean layouts, elegant typography, and smooth interactions that feel natural on TV.

### Key Features Added
- **Redesigned top navigation** - Media libraries moved to a clean, accessible navigation bar with complete visual overhaul
- **Vertical content cards** - Switched from horizontal to vertical card layout for better content display and modern streaming service feel
- **Streaming service-style previews** - Hover over content to see large preview in the top half of the screen with details and backdrop
- **Enhanced content discovery** - More content rows on the home page including recommendations, genres, and other discovery sections for easy browsing
- **Glassmorphic design elements** for a premium, layered look throughout the interface

### Why These Changes?
The streaming landscape has evolved a lot in recent years. Users expect certain patterns and interactions from their TV interfaces. This project brings those modern conventions to Jellyfin while keeping all the functionality you love.

## Current Progress

### Completed
- **Home page redesign** - Complete overhaul with modern streaming service aesthetics
- **Top navigation overhaul** - Media libraries repositioned for cleaner, more accessible layout
- **Vertical card redesign** - Switched to vertical content cards for better display and modern feel
- **Interactive previews** - Hover previews with large content display, details, and backdrop integration
- **Expanded content discovery** - Multiple content rows including recommendations, genres, and discovery sections
- **Remote control optimization** - Better focus handling and navigation flow

### Coming Next
This is just the beginning! The home page was the starting point, but I'm planning to extend this design language throughout the app:
- Settings and preferences screens
- Player controls
- Media browsing views
- Content selection within media

## Installation

### Building from Source
```bash
git clone https://github.com/ShivPatel123/jellyfin-androidtv.git
cd jellyfin-androidtv
./gradlew assembleDebug
./gradlew installDebug
```

### Requirements
- Android TV device or emulator
- Jellyfin server (10.8.0+ recommended)

## The Vision

The idea is to create an interface that feels as polished and intuitive as commercial streaming services, while maintaining all the power and flexibility that makes Jellyfin great. Every design decision is made with TV viewing in mind - proper spacing for 10-foot interfaces, clear focus indicators, and smooth remote control navigation.

## Development Approach

I'm taking this one section at a time, starting with the home page since that's what users see first. Each update maintains backward compatibility while gradually introducing the new design language.

The technical implementation uses modern Android TV development practices with proper Material Design principles adapted for television interfaces.

## Contributing

If you're interested in helping with the design overhaul, I'd love to collaborate! Areas where contributions would be especially valuable:
- UI/UX feedback and suggestions
- Testing on different Android TV devices
- Performance optimization
- Additional streaming service-inspired features

## Acknowledgments

This project builds upon the excellent foundation provided by the Jellyfin team and community. Special thanks to all contributors who make open-source media streaming possible.

## Feedback Welcome

This is an ongoing project, and I'm always interested in hearing how the new interface works for different users and setups. Feel free to open issues with suggestions, bug reports, or just general feedback.

---

**Goal: Bring Jellyfin Android TV's interface up to modern streaming service standards while keeping everything that makes it powerful and flexible.**
