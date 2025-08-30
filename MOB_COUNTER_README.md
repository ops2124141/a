# ZombiesExplorer Mob Counter System

## Implementation Overview

A dynamic mob counter system has been added to the ZombiesExplorer mod that displays the current number of living hostile mobs on screen with a moveable and resizable GUI overlay.

## New Files Added

### Core Implementation
- `src/main/java/com/seosean/zombiesexplorer/MobCounterHandler.java`
  - Handles real-time mob tracking and counting
  - Syncs with existing MobHighlightHandler for wave detection
  - Uses same mob filtering logic for consistency

- `src/main/java/com/seosean/zombiesexplorer/MobCounterGui.java`
  - Renders the moveable/resizable overlay
  - Handles mouse input for drag/resize functionality
  - Saves position and size to configuration

- `src/main/java/com/seosean/zombiesexplorer/utils/NotificationRenderer.java`
  - Utility class for displaying toggle notifications
  - Provides user feedback for keybind actions

### Modified Files
- `src/main/java/com/seosean/zombiesexplorer/ZombiesExplorer.java`
  - Added mob counter configuration option
  - Added M key binding for toggle functionality
  - Integrated new handlers into event system
  - Added debug chat commands

- `src/main/java/com/seosean/zombiesexplorer/MobHighlightHandler.java` 
  - Added public method to access current round information
  - Enables wave synchronization with mob counter

## Key Features

### Real-time Mob Counting
- Updates every client tick for immediate accuracy
- Counts all hostile mobs using existing mod logic
- Excludes boss mobs (handled by separate system)
- Excludes scoreboard entities (300 HP filter)

### Interactive GUI
- **Draggable**: Click and drag anywhere on counter to move
- **Resizable**: Drag bottom-right corner to resize
- **Constraints**: Minimum 80x25px, maximum 300x100px
- **Bounds Checking**: Stays within screen boundaries
- **Persistence**: Position and size saved to config file

### Visual Design
- **Color Coding**: Green text when mobs present, red when none
- **Wave Display**: Shows current zombie round/wave
- **Transparency**: Semi-transparent background for visibility
- **Hover Effects**: Yellow outline when dragging/resizing
- **Tooltips**: Shows interaction help on hover

### Configuration
- **Toggle Key**: M key (configurable)
- **Config Integration**: Uses existing ZombiesExplorer config system
- **Persistent Settings**: Position, size, and enabled state saved
- **Debug Commands**: Chat commands for troubleshooting

## Technical Integration

### Event System
- Registers with MinecraftForge event bus
- Uses same tick events as existing handlers
- Integrates with existing rendering pipeline

### Mob Detection
- Reuses mob filtering logic from MobHighlightHandler
- Consistent with existing ESP/Chams functionality
- Excludes same entities (players, armor stands, withers)

### Wave Synchronization
- Links with MobHighlightHandler for round detection
- Automatic wave number updates
- Fallback to manual wave tracking if sync unavailable

### Error Handling
- Graceful degradation on errors
- Console logging for debugging
- State reset on world changes

## Usage Instructions

1. **Enable**: Press M key to toggle mob counter on/off
2. **Move**: Click and drag the counter to desired position
3. **Resize**: Drag the white square in bottom-right corner
4. **Debug**: Use `/zemobdebug` for detailed information
5. **Quick Toggle**: Use `/zetoggle mobcounter` as alternative

## Configuration Options

Located in ZombiesExplorer config file:
- `Mob Counter` (boolean) - Enable/disable feature
- `mobCounterX` (int) - Horizontal position
- `mobCounterY` (int) - Vertical position  
- `mobCounterWidth` (int) - Counter width
- `mobCounterHeight` (int) - Counter height
- `keyToggleMobCounter` (int) - Key binding for toggle

## Compatibility

- **Minecraft Version**: 1.8.9
- **Forge Version**: As per existing mod requirements
- **Dependencies**: Same as ZombiesExplorer mod
- **Conflicts**: None known - integrates seamlessly with existing features