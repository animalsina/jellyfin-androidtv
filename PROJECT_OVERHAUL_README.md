# Jellyfin Android TV - Modern UI Overhaul

## A Fresh Take on Home Entertainment

This fork focuses on giving Jellyfin Android TV a modern streaming service look and feel. The goal is to create a more polished, contemporary interface that feels familiar and intuitive for today's users.

![Demo of the new interface](Jellyfin_Home_UI_Demo.gif)

## What I'm Building

### Modern Home Interface
I'm redesigning the home screen to match the visual language of modern streaming services - clean layouts, elegant typography, and smooth interactions that feel natural on TV.

### Key Features Added
- **Glassmorphic design elements** for a premium, layered look
- **Smooth navigation pills** with hover animations
- **Enhanced content cards** with quality badges and better imagery
- **Improved content discovery** with Continue Watching, Next Up, and genre-based sections
- **Better visual hierarchy** to help users find what they want quickly

### Why These Changes?
The streaming landscape has evolved a lot in recent years. Users expect certain patterns and interactions from their TV interfaces. This project brings those modern conventions to Jellyfin while keeping all the functionality you love.

## Current Progress

### Completed
- **Home page redesign** - Complete overhaul with modern streaming service aesthetics
- **Navigation system** - New pill-based navigation with smooth animations  
- **Content organization** - Continue Watching, Next Up, Recommended, and Genre sections
- **Visual polish** - Glassmorphic backgrounds, quality indicators, enhanced cards
- **Remote control optimization** - Better focus handling and navigation flow

### Coming Next
This is just the beginning! The home page was the starting point, but I'm planning to extend this design language throughout the app:
- Settings and preferences screens
- Player controls
- Media browsing views

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
