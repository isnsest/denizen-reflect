<div align="center">

<img src="https://github.com/user-attachments/assets/a9a6116b-1833-40d3-89aa-22a8a1892892" width="120" alt="Logo">

# Denizen-Reflect

[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/denizen-reflect)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/isnsest/denizen-reflect)
[![](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_vector.svg)](https://docs.meigo.pw/)

<br>

[![Snippets](https://img.shields.io/badge/snippets_code-reflect-orange?style=for-the-badge&logo=codeigniter&logoColor=white)](https://snippets.meigo.pw/)
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
import:
    java.lang.System as alias
    java.lang.String

task:
    type: task
    script:
    - invoke player.kick()
    - invoke System.out.println("test")
    - define hello <invoke[new String("Hello")]>
