<div align="center">

<img src="https://github.com/user-attachments/assets/a9a6116b-1833-40d3-89aa-22a8a1892892" width="120" alt="Logo">

# Denizen-Reflect

[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/denizen-reflect)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/isnsest/denizen-reflect)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_vector.svg)](https://isnsest.gitbook.io/denizen-reflect)

<br>

[![Discord](https://img.shields.io/discord/1450970030744539219?style=for-the-badge&logo=discord&label=Discord&color=5865F2)](https://discord.gg/SVwEmsvpjN)

</div>

<br>

**denizen-reflect** is an add-on for experienced scripters designed to combine the capabilities of Java directly within the Denizen development environment.

### Supported Platforms
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)](https://papermc.io/downloads/paper)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg)](https://purpurmc.org/)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/spigot_vector.svg)](https://www.spigotmc.org/)

---

### Features

* 🔹 Importing classes
* 🔹 Executing Java code (methods, fields, constructors)
* 🔹 Creating your own placeholders (PlaceholderAPI)
* 🔹 Creating custom Denizen commands & tags
* 🔹 Renaming Denizen events
* 🔹 Creating proxies
* 🔹 Lambda expressions support
* 🔹 And much more...

### Example Usage

```yaml
# only for this file
import:
    java.lang.System as alias
    java.lang.String

# for every script file (put it in any one .dsc file; a file's own import: wins on a name clash)
global_import:
    org.bukkit.Bukkit
    net.minecraft.network.protocol.game.ClientboundPingPacket as PingPacket

task:
    type: task
    script:
    - invoke player.kick()
    - invoke System.out.println("test")
    - define hello <invoke[new String("Hello")]>
```

---

### Performance

> [!NOTE]
> **denizen-reflect adds practically no load of its own.** One `invoke` costs it about **0.0003 ms** (0.3 µs), less than one of the simplest Denizen commands. In terms of load there is no visible difference from calling the same Java method from a compiled plugin: what you pay for is Denizen running the script lines and the work done by the method itself.

| What runs | Time per call |
|---|---|
| **denizen-reflect** (`invoke` overhead) | **~0.0003 ms** |
| Denizen, `- define x <[id]>` | ~0.0006 ms |
| A whole `- invoke ...` line (Denizen + denizen-reflect) | ~0.001 ms |

#### Hot loops

The cost only becomes noticeable when a line runs many times every tick, for example an `invoke` inside a loop over all online players. Even then, most of the time is spent by Denizen itself, not by denizen-reflect:

| Scenario | Calls per tick | Denizen itself | **denizen-reflect** | Total (of 50 ms) |
|---|---|---|---|---|
| A single call | 1 | ~0.0006 ms | **~0.0003 ms** | ~0.001 ms |
| Loop over 50 players | 50 | ~0.03 ms | **~0.015 ms** | ~0.05 ms |
| Loop over 200 players | 200 | ~0.12 ms | **~0.06 ms** | ~0.18 ms (0.4%) |
| 200 players × 5 `invoke` each | 1 000 | ~0.6 ms | **~0.3 ms** | ~0.9 ms (1.8%) |
| 200 players × 10 `invoke` each | 2 000 | ~1.2 ms | **~0.6 ms** | ~1.8 ms (3.7%) |

Denizen itself accounts for **55–80%** of the total. That is the time it spends interpreting the script: stepping the loop, running each line, parsing its arguments and filling tags like `<[id]>`. It is the same for any Denizen command, not specific to `invoke`, and it is CPU time on the main thread, not garbage collection. A compiled plugin does not pay this part.

> [!TIP]
> **Very hot loops:** if a loop runs hundreds of times every tick and you want to drop the Denizen part as well, move the loop into a native `.java` script. Put the `.java` file in your scripts folder: denizen-reflect compiles it on load, and it runs like regular plugin code. Call it from Denizen once per tick with a single `invoke`.

#### How the share is calculated

The total is what the whole loop costs per tick. Subtracting the same loop without `invoke` leaves the share of denizen-reflect. Measured values for 200 calls per tick:

| Setup | Total | − Denizen itself | = **denizen-reflect** | Denizen's share |
|---|---|---|---|---|
| DenizenM, `invoke ... (id)` | 0.133 ms | 0.100 ms | **0.033 ms** | 75% |
| DenizenM, `invoke ... (<[id]>)` | 0.186 ms | 0.100 ms | **0.086 ms** | 54% |
| Original Denizen, `invoke ... (id)` | 0.179 ms | 0.146 ms | **0.033 ms** | 82% |
| Original Denizen, `invoke ... (<[id]>)` | 0.232 ms | 0.146 ms | **0.086 ms** | 63% |

<details>
<summary>How it was measured</summary>

- Live Purpur 26.3 server, Java 25, denizen-reflect 2.5.0, two runs per setup.
- 50 calls per tick were measured with `srv.getServer().getPlayerList().broadcastAll(new ClientboundPingPacket(id))`; 1 000 calls per tick were measured separately with simpler expressions. Load grows linearly with the number of calls, so the other rows are scaled from these.
- The values in the tables above are typical: averaged over DenizenM and original Denizen, `invoke ... (id)` and `invoke ... (<[id]>)`. A real setup can be up to about 40% lower or higher.
- With several `invoke` lines per iteration the Denizen part is slightly overestimated, since they share one loop step.
- Absolute numbers depend on your CPU.

</details>
