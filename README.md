# Velocity Rivals – Racing Arcade Game.

**Velocity Rivals** is a high‑speed Java racing game built with Swing and AWT. It features two distinct game modes, local multiplayer, custom power‑ups, dynamic visual themes, and a strict **“any collision = game over”** rule that keeps every race intense.

![Java Version](https://img.shields.io/badge/Java-17%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)

## 🎮 Features

- **Two Game Modes**  
  - **Survival** – Endless auto‑scrolling road. Avoid AI cars, collect power‑ups and coins, and survive as long as possible.  
  - **Race** – Reach 5000 m before the 90‑second timer runs out. Accelerate and brake to control your speed.  
- **Local Multiplayer** – Two players on the same keyboard (split‑screen).  
- **Three Visual Themes** – Desert, Night City, Neon Highway – each changes road color, background, speed and traction.  
- **Power‑ups**  
  - 🚀 **Boost** – Temporary nitro refill and speed boost.  
  - 🛡️ **Shield** – Blocks one collision or missile.  
  - 💣 **Missile** – Homing projectile that instantly destroys the target (player or AI).  
  - 🛢️ **Oil** – Deploys a slippery patch that slows down any car.  
  - 🔄 **Swap** – (Race mode only) Swaps positions and race distance between the two players.  
- **Coins** – Scattered on the road, each gives +10 score.  
- **Crates** – Hitting a crate reduces your health (20 in Race mode, 30 in Survival). Reinforced crates look different but deal the same damage.  
- **Difficulty Progression** – Every 30 seconds the difficulty level increases (up to 5), making AI faster and spawning more frequent.  
- **Leaderboard** – Top scores per game mode stored in a custom priority queue.  
- **Visual & Audio Feedback** – Particles (sparks, smoke, explosions), screen shake, damage flash, and an event log.

## 🕹️ Controls

| Action               | Single Player       | Multiplayer P1      | Multiplayer P2      |
|----------------------|---------------------|---------------------|---------------------|
| Steer Left / Right   | ← / →               | A / D               | J / L               |
| Accelerate (Race)    | ↑                   | W                   | I                   |
| Brake / Reverse      | ↓                   | S                   | K                   |
| Nitro                | SPACE               | SPACE               | ENTER               |
| Use Power‑up         | Z                   | Z                   | M                   |

> **Note:** In Survival mode, the car scrolls automatically – the up/down keys are not used.

## ⚙️ Installation & Running

### Prerequisites
- **Java Development Kit (JDK) 17 or later** – [Download](https://adoptium.net/)
- Any terminal / command prompt

### Steps
1. **Clone the repository** (or download the two source files):
   ```bash
   git clone https://github.com/yourusername/velocity-rivals.git
   cd velocity-rivals
   ```
2. **Compile** both files:
   ```bash
   javac VelocityRivalsEngine.java VelocityRivalsGUI.java
   ```
3. **Run** the game:
   ```bash
   java VelocityRivalsGUI
   ```

> The game automatically adapts its window size to your screen (maximum 1200×700).

## 🗂️ Project Structure

```
velocity-rivals/
├── VelocityRivalsEngine.java   # Data structures, enums, entity classes (Car, AI, Pickup, …)
├── VelocityRivalsGUI.java      # GUI, game loop, input handling, drawing, game logic
└── README.md
```

All game logic and custom collections (`CustomQueue`, `CustomStack`, `CustomHashMap`, `CustomPriorityQueue`) are implemented from scratch – no external libraries.

## 💡 How to Play – Quick Tips

- **Any collision** (with an AI car, another player, or a crate) **ends the game immediately** – unless you have an active Shield.
- In **Race mode**, you can accelerate (↑/W) and brake (↓/S) to manage your speed. The distance bar at the top shows your progress.
- **Swap Booster** (purple pickup) only works in multiplayer Race mode – use it to instantly switch positions with your opponent!
- **Missiles** home in on the nearest opponent (or AI in single player). A shielded car blocks the missile.
- **Oil slicks** stay on the road for a few seconds and slow down anyone who drives over them.
- The **event log** in the bottom‑left corner shows the latest game events (power‑up usage, collisions, difficulty changes).

## 🧪 Known Behaviours

- Collisions are **disabled for the first second** after the countdown to prevent immediate deaths at start.
- The **shield** protects against one collision or one missile hit.
- In **single‑player Race mode**, the game ends when you either finish the distance or the time runs out.
- The **leaderboard** stores scores only at game end (not every frame).

## 🛠️ Troubleshooting

| Problem                     | Solution |
|-----------------------------|----------|
| `javac` not found           | Install JDK and add `bin/` folder to your PATH. |
| Game window doesn’t appear  | Make sure you run `java VelocityRivalsGUI` (the class with `main`). |
| Keys don’t work             | Click inside the game window to give it keyboard focus. |
| Compilation errors          | Ensure both `.java` files are in the same folder and use the exact names shown above. |

## 🤝 Contributing

This project was built as a demonstration of custom data structures and real‑time game development in pure Java. Suggestions and bug reports are welcome – feel free to open an issue or submit a pull request.

## 📄 Screenshots : 


<img width="1197" height="716" alt="image" src="https://github.com/user-attachments/assets/831a6585-7064-4bd4-87d1-5892566e6831" />

 <img width="1198" height="717" alt="image" src="https://github.com/user-attachments/assets/657b2077-8bdd-476f-b11f-1b1f93cb2b1f" />


<img width="371" height="137" alt="image" src="https://github.com/user-attachments/assets/4aa686f5-b2e5-4728-99ff-153ce3b49a5f" />
<img width="1193" height="715" alt="image" src="https://github.com/user-attachments/assets/b371b249-be3f-4e7f-b3d4-b4a05a3c0e2c" />



<img width="1200" height="722" alt="Screenshot 2026-05-01 235548" src="https://github.com/user-attachments/assets/a3bd270b-4e2f-4fd2-8f5d-91fe749e20cb" />

<img width="1195" height="687" alt="Screenshot 2026-05-01 235709" src="https://github.com/user-attachments/assets/fcbceb6a-8fc1-40d9-b22d-0cfd9f594d24" />





--

**Made with ☕ and Java** 

