# ZombiesExplorer Mob Counter System - Testing Guide

## Overview
The mob counter system has been successfully implemented with all requested features. Here's how to test and validate the functionality:

## Features Implemented

### 1. Real-time Mob Counting
- **Implementation**: MobCounterHandler scans for hostile mobs every client tick
- **Test**: Enter a world with zombies/hostile mobs and observe the counter update immediately
- **Validation**: Count should match visible hostile mobs on screen

### 2. Moveable and Resizable GUI  
- **Implementation**: MobCounterGui with drag/resize functionality
- **Test**: 
  - Click and drag the counter to move it around the screen
  - Drag the bottom-right corner (white square) to resize
  - Position and size are saved automatically
- **Validation**: Counter should move smoothly and stay within screen bounds

### 3. Color-coded Display
- **Implementation**: Green text for mob count > 0, red text for count = 0
- **Test**: 
  - Kill all mobs - counter should show "0" in red
  - Spawn/encounter mobs - counter should show count in green
- **Validation**: Color changes immediately when mob count changes

### 4. Wave-based Tracking
- **Implementation**: Syncs with existing MobHighlightHandler round detection
- **Test**: Progress through zombie rounds and observe wave number updates
- **Validation**: Wave number should match current zombie round

### 5. Configurable Settings
- **Implementation**: Toggle with M key, position/size saved to config
- **Test**:
  - Press M key to toggle counter on/off
  - Move/resize counter and restart game - position should be preserved
- **Validation**: Settings persist across game sessions

### 6. Integration with Existing Mod
- **Implementation**: Uses same event system, mob filtering, and config system
- **Test**: Ensure other mod features (ESP, chams, etc.) still work
- **Validation**: No conflicts with existing functionality

## Debug Commands
- `/zemobdebug` - Prints detailed mob counter information to console
- `/zetoggle mobcounter` - Quick toggle for mob counter

## Key Bindings
- **M key** (default) - Toggle mob counter on/off
- **Configurable** through mod config or Minecraft controls menu

## Configuration Location
- Settings saved in ZombiesExplorer config file
- Position saved as: `mobCounterX`, `mobCounterY` 
- Size saved as: `mobCounterWidth`, `mobCounterHeight`
- Enabled state: `Mob Counter` boolean setting

## Known Limitations
- Requires ZombiesExplorer mod to be enabled
- Only counts hostile mobs (same filtering as MobHighlightHandler)
- Does not count boss mobs (handled separately by BossHighlightHandler)
- Minimum size: 80x25 pixels, Maximum size: 300x100 pixels

## Troubleshooting
1. **Counter not showing**: Check if mod is enabled and M key toggle is on
2. **Counter not moving**: Ensure you're dragging the main area, not just the border
3. **Counter not resizing**: Drag the white square in bottom-right corner
4. **Wrong count**: Use `/zemobdebug` to see detailed tracking information
5. **Position reset**: Check config file permissions and save location

## Testing Scenarios
1. **Basic Functionality**: Spawn zombies, observe count updates
2. **UI Interaction**: Move and resize counter multiple times
3. **Configuration Persistence**: Toggle off/on, restart game
4. **Integration**: Test with other mod features enabled
5. **Edge Cases**: Test with 0 mobs, very high mob counts, different mob types