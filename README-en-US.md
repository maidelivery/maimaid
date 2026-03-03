# maimaid

A modern, native iOS companion for maimai DX players. Track your scores, analyze your progress, and discover new challenges with ease.

## Features

- **Score Tracker & Rating Analytics**: Real-time DX Rating calculation based on your best scores
- **ML Scanner**: Quick score entry via machine learning powered score picture recognition.
- **Randomizer**: A slot-machine style random song picker for when you can't decide what to play.
- **Smart Recommendations**: Discovery engine based on your current rating and chart constants.
- **Plate Progress**: Visualize your journey towards version-specific completion plates.
- **Data Sync**: Seamlessly import your data from Diving Fish and LXNS.

## Tech Stack

- **UI Framework**: SwiftUI
- **Persistence**: SwiftData
- **Machine Learning**: YOLOv11 and CoreML

## Getting Started

### Prerequisites

- macOS with Xcode 15+
- iOS 17.0+ (for SwiftData and modern SwiftUI features)

### Build

1. Clone the repository:
   ```bash
   git clone https://github.com/shikochin/maimaid.git
   ```
2. Open `maimaid.xcodeproj` in Xcode.
3. Select your target device/simulator and press `Cmd+R` to run.

## Special Thanks

- **Diving Fish**: For the invaluable community data and API support.
- **LXNS Coffee House**: For providing the song aliases and score APIs.
- **maimai**: Developed by SEGA. All game assets and trademarks belong to their respective owners.
- [**arcade-songs**](https://arcade-songs.zetaraku.dev/): Provided the song data.
- Google Antigravity.
- Ultralytics Platform helped training model.
- charaDiana assisted in labeling images.
