![WorldGuard Logo](https://cdn.modrinth.com/data/cached_images/a0ae1044eb63255f85ca419f7be9033e61905570.png)
# WorldGuard for NeoForge 1.21.1 (Port)
This is an unofficial port for the Bukkit WorldGuard-plugin. You can read through the sections below and will likely
find answers to all these question marks above your head.
## Basic FAQ
**Is this like the real WorldGuard?**  
*For the most part, yes. It only misses low relevancy features like sponge simulation or conduit-effect controls.
Every major feature works and it behaves exactly like the original WorldGuard.*  

**Can I use this in my mod or modpack?**  
*Yes. You are free to use it in your mod and modpacks.*  

**Is this working with many mods?**  
*Performance-wise: Yes. Compatibility-wise: Depends. It is impossible to make a mod like this fully compatible with
mods that add their own interaction methods etc. However, it should generally provide protection and full functionality
in most cases.*
## Functionality & Compatibility
This is a direct port to the NeoForge mod architecture. It covers **almost** every feature of the original WorldGuard
plugin and will be updated regularly to feature new features, fixes and changes.  

It offers all flags and protection features that the original offers. It works essentially the same way. Functionality
of this mod should be identical to WorldGuard itself, since this is a direct port of its architecture.

As described above, **compatibility** heavily depends on the mods you use. Some mods may implement their own methods and
functions that cannot be hooked by WorldGuard's protection system. However, it should cover most if not all protection
scenarios, even with a heavily modded server environment.
## Performance
Performance has been tested intensively with multiple test scenarios and environments.  

This mod should theoretically offer roughly **similar performance compared to the original WorldGuard**, since it uses the
same exact engine, methods and code structure, and all test scenarios resulted in very good performance.

This port should be **very performant**, even on servers with high load, many mods and players.
## License
This project is a port and thus goes under the same license as the original WorldGuard.  
*This project is licensed under the **LGPLv3** (GNU Lesser General Public License v3).*
## Credit
EngineHub - Creators of WorldGuard.  
You can find the original repository [here](https://github.com/enginehub/worldguard).
