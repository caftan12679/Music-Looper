# Music Looper

Keeps Minecraft's music **playing forever** — and lets you play your own songs.

> Minecraft **1.20.1** + Forge **47.x** · Client-side only, no need to install on the server

---

## What it does

**1. Continuous vanilla music**

Vanilla background music goes silent for a few minutes after each track.
With this mod, one song flows straight into the next — you barely notice a gap.
Track selection works just like vanilla: it picks music based on the biome
and dimension you're in.

**2. Play your own music**

The mod creates this folder for you:

```
.minecraft/config/musiclooper/music/
```

Just drop your songs in (subfolders work too) and they'll be added to the
random playlist, alternating with the vanilla music.

Supported formats:

| Format | Notes |
| --- | --- |
| `.ogg` | Most reliable — uses Minecraft's built-in decoder |
| `.mp3` | Decoder is bundled, plays out of the box |
| `.wav` / `.aiff` / `.au` | Uses Java's built-in decoders |

After adding songs, run this command in-game to rescan:

```
/musiclooper reload
```

**3. Biome-specific music**

Want one biome to always play a particular song? You can assign tracks to a
biome, or to a whole category of biomes — see [Configuration](#configuration).

**4. Shut the vanilla music up**

If you only want to hear your own songs, open the config file:

```
.minecraft/config/musiclooper-client.toml
```

And set this to `true`:

```toml
[vanilla]
    # Completely disable vanilla music once there is enough custom music
    disableVanillaMusicWhenEnoughCustom = true

    # How many tracks count as "enough" (default: 5)
    customMusicThreshold = 5
```

Once your track count reaches the threshold, vanilla music stops entirely.

---

## Commands

| Command | What it does |
| --- | --- |
| `/musiclooper play <file name>` | **Immediately** stop the current song and play the one you name |
| `/musiclooper biome` | Show the biome you're standing in, and which rule it matched |
| `/musiclooper reload` | Rescan the music folder |
| `/musiclooper status` | Show how many tracks were found, plus current config |

---

## Configuration

Everything lives in one file:

```
.minecraft/config/musiclooper-client.toml
```

It has three sections:

```toml
[vanilla]     # vanilla music behaviour
[custom]      # your own music
[biomeMusic]  # per-biome rules
```

### `[vanilla]`

```toml
[vanilla]
    # Keep the vanilla music going forever instead of leaving gaps
    loopVanillaMusic = true

    # Fully disable vanilla music once enough custom tracks exist
    disableVanillaMusicWhenEnoughCustom = false

    # How many tracks count as "enough" (default: 5)
    customMusicThreshold = 5
```

### `[custom]`

```toml
[custom]
    # Play the tracks found in config/musiclooper/music
    playCustomMusic = true

    # Volume multiplier for your own music (0.0 ~ 1.0)
    customMusicVolume = 1.0
```

### `[biomeMusic]`

Play specific songs in specific biomes.

**Five steps to set it up:**

1. Put your music into `config/musiclooper/music/`
2. Open `config/musiclooper-client.toml`
3. Under `[biomeMusic]`, set `enabled = true`
4. Write your rules in `rules`
5. Run `/musiclooper reload` in-game

**Rule format** — every rule looks like `biome ID=file1,file2`:

```toml
[biomeMusic]
    enabled = true

    rules = [
        # 1. A single biome: only these two play in plains
        "minecraft:plains=calm.mp3,morning.mp3",

        # 2. A biome tag: shared by every forest biome (note the leading #)
        "#minecraft:is_forest=forest_theme.mp3",

        # 3. Fallback: used when nothing above matched
        "*=fallback.mp3",
    ]

    # ONLY_CUSTOM  only your music plays here, vanilla stays quiet (default)
    # MIX          your music and vanilla alternate
    mode = "ONLY_CUSTOM"
```

**Matching order:** exact biome → tag → `*` fallback.

**Biome IDs** are written `namespace:name`, and the namespace defaults to
`minecraft`:

```toml
"minecraft:plains=..."                # full form
"plains=..."                          # same thing
"biomesoplenty:lavender_field=..."    # biomes from other mods work too
```

Not sure what the biome ID is? Stand in that biome and run:

```
/musiclooper biome
```

It tells you the biome ID, whether a rule matched, and which tracks got picked.

**Tags** cover a whole category at once — just prefix with `#`:

```toml
"#minecraft:is_forest=forest.mp3"      # all forests
"#minecraft:is_taiga=taiga.mp3"        # all taigas
"#minecraft:is_jungle=jungle.mp3"      # all jungles
"#minecraft:is_ocean=ocean.mp3"        # all oceans
"#minecraft:is_mountain=mountain.mp3"  # all mountains
"#minecraft:is_nether=nether.mp3"      # the whole Nether
```

Also handy: `is_beach`, `is_river`, `is_badlands`, `is_hill`, `is_savanna`,
`is_overworld`.

> These are Minecraft's own groupings, so biomes added by future game updates
> are covered automatically.

**File names** are matched against `config/musiclooper/music/` and are
**case-insensitive**:

```toml
"minecraft:plains=Calm.MP3"     # matches calm.mp3
```

If a file sits in a subfolder, write just the file name — no path needed.

**Two things worth knowing:**

**1. What if a file can't be found?**
If every file named in a rule is missing or undecodable, that biome falls back
to the full random playlist. You never end up with a silent biome — the reason
is written to the log instead.

**2. Which setting wins on a conflict?**
Biome rules win. Even with "disable vanilla music when there's enough custom
music" turned on, a matching `ONLY_CUSTOM` rule takes precedence.

---

## Installation

1. Install **Forge 47.x** (Minecraft 1.20.1)
2. Drop `musiclooper-1.1.0.jar` into `.minecraft/mods/`
3. Launch the game

---

## Building from source

```bash
./gradlew build
```

The jar lands in `build/libs/musiclooper-1.1.0.jar`. Requires JDK 17 or newer.

---

## License

[GPL-3.0](LICENSE) · By **hemjj,蓝色大肥鱼**
