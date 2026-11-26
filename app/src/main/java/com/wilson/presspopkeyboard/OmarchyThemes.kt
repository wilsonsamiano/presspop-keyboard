package com.wilson.presspopkeyboard

/**
 * The stock Omarchy themes' colors, so the keyboard on Android can match
 * KeyboardPOP on an Omarchy laptop. Copied from each theme's colors.toml in
 * Omarchy (/usr/share/omarchy/themes/<theme>/colors.toml); the keys are the
 * same names Omarchy uses. KeyboardPalette.omarchy() turns one into key
 * colors the same way KeyboardPOP's theme.cpp does.
 */
data class OmarchyTheme(val id: String, val name: String, val colors: Map<String, String>)

object OmarchyThemes {
    const val DEFAULT = "tokyo-night"

    val all: List<OmarchyTheme> = listOf(
        OmarchyTheme("catppuccin-latte", "Catppuccin Latte", mapOf("background" to "#EFF1F5", "foreground" to "#4C4F69", "accent" to "#1E66F5", "lighter_background" to "#DCE0E8", "dark_background" to "#E3E4E8", "darker_background" to "#D7D8DC", "bright_foreground" to "#4C4F69", "dark_foreground" to "#9CA0B0", "red" to "#D20F39")),
        OmarchyTheme("catppuccin", "Catppuccin", mapOf("background" to "#1E1E2E", "foreground" to "#CDD6F4", "accent" to "#89B4FA", "lighter_background" to "#313244", "dark_background" to "#161622", "darker_background" to "#101019", "bright_foreground" to "#CDD6F4", "dark_foreground" to "#6C7086", "red" to "#F38BA8")),
        OmarchyTheme("ethereal", "Ethereal", mapOf("background" to "#060B1E", "foreground" to "#FFCEAD", "accent" to "#7D82D9", "lighter_background" to "#131A3A", "dark_background" to "#040816", "darker_background" to "#030610", "bright_foreground" to "#FFCEAD", "dark_foreground" to "#6D7DB6", "red" to "#ED5B5A")),
        OmarchyTheme("everforest", "Everforest", mapOf("background" to "#2D353B", "foreground" to "#D3C6AA", "accent" to "#7FBBB3", "lighter_background" to "#343F44", "dark_background" to "#21272C", "darker_background" to "#181D20", "bright_foreground" to "#D3C6AA", "dark_foreground" to "#4F585E", "red" to "#E67E80")),
        OmarchyTheme("flexoki-light", "Flexoki Light", mapOf("background" to "#FFFCF0", "foreground" to "#100F0F", "accent" to "#205EA6", "lighter_background" to "#E6E4D9", "dark_background" to "#F2EFE4", "darker_background" to "#E5E2D8", "bright_foreground" to "#100F0F", "dark_foreground" to "#878580", "red" to "#D14D41")),
        OmarchyTheme("gruvbox", "Gruvbox", mapOf("background" to "#282828", "foreground" to "#D4BE98", "accent" to "#7DAEA3", "lighter_background" to "#3C3836", "dark_background" to "#1E1E1E", "darker_background" to "#161616", "bright_foreground" to "#D4BE98", "dark_foreground" to "#7C6F64", "red" to "#EA6962")),
        OmarchyTheme("hackerman", "Hackerman", mapOf("background" to "#0B0C16", "foreground" to "#DDF7FF", "accent" to "#82FB9C", "lighter_background" to "#151828", "dark_background" to "#080910", "darker_background" to "#06060C", "bright_foreground" to "#DDF7FF", "dark_foreground" to "#6A6E95", "red" to "#50F872")),
        OmarchyTheme("jade-horse", "Jade Horse", mapOf("background" to "#142620", "foreground" to "#DFEDE5", "accent" to "#8BCBB1", "lighter_background" to "#203B31", "dark_background" to "#0F1E19", "darker_background" to "#0A1713", "bright_foreground" to "#F0FAF4", "dark_foreground" to "#91AD9F", "red" to "#D88D91")),
        OmarchyTheme("kanagawa", "Kanagawa", mapOf("background" to "#1F1F28", "foreground" to "#DCD7BA", "accent" to "#DCD7BA", "lighter_background" to "#223249", "dark_background" to "#17171E", "darker_background" to "#111116", "bright_foreground" to "#DCD7BA", "dark_foreground" to "#727169", "red" to "#C34043")),
        OmarchyTheme("last-horizon", "Last Horizon", mapOf("background" to "#0C0B0C", "foreground" to "#FAFCFB", "accent" to "#B59790", "lighter_background" to "#0C0B0C", "dark_background" to "#090809", "darker_background" to "#060606", "bright_foreground" to "#E2DDDC", "dark_foreground" to "#584E51", "red" to "#C38B7B")),
        OmarchyTheme("lumon", "Lumon", mapOf("background" to "#16242D", "foreground" to "#D6E2EE", "accent" to "#8BC9EB", "lighter_background" to "#1B2D40", "dark_background" to "#101B21", "darker_background" to "#0B1216", "bright_foreground" to "#F2FCFF", "dark_foreground" to "#4D86B0", "red" to "#4D86B0")),
        OmarchyTheme("lupine", "Lupine", mapOf("background" to "#FAFAFA", "foreground" to "#212121", "accent" to "#3264EB", "lighter_background" to "#F5F5F5", "dark_background" to "#ECECEC", "darker_background" to "#DEDEDE", "bright_foreground" to "#000000", "dark_foreground" to "#757575", "red" to "#C900C4")),
        OmarchyTheme("matte-black", "Matte Black", mapOf("background" to "#121212", "foreground" to "#BEBEBE", "accent" to "#E68E0D", "lighter_background" to "#1E1E1E", "dark_background" to "#0D0D0D", "darker_background" to "#090909", "bright_foreground" to "#BEBEBE", "dark_foreground" to "#555555", "red" to "#D35F5F")),
        OmarchyTheme("miasma", "Miasma", mapOf("background" to "#222222", "foreground" to "#C2C2B0", "accent" to "#78824B", "lighter_background" to "#2C2C2C", "dark_background" to "#191919", "darker_background" to "#121212", "bright_foreground" to "#C2C2B0", "dark_foreground" to "#555555", "red" to "#685742")),
        OmarchyTheme("nord", "Nord", mapOf("background" to "#2E3440", "foreground" to "#D8DEE9", "accent" to "#81A1C1", "lighter_background" to "#3B4252", "dark_background" to "#222730", "darker_background" to "#191C23", "bright_foreground" to "#D8DEE9", "dark_foreground" to "#667080", "red" to "#BF616A")),
        OmarchyTheme("retro-82", "Retro 82", mapOf("background" to "#05182E", "foreground" to "#F6DCAC", "accent" to "#FAA968", "lighter_background" to "#0A2540", "dark_background" to "#031222", "darker_background" to "#020C17", "bright_foreground" to "#F6DCAC", "dark_foreground" to "#3F8F8A", "red" to "#F85525")),
        OmarchyTheme("ristretto", "Ristretto", mapOf("background" to "#2C2525", "foreground" to "#E6D9DB", "accent" to "#F38D70", "lighter_background" to "#3D2F2A", "dark_background" to "#211B1B", "darker_background" to "#181414", "bright_foreground" to "#E6D9DB", "dark_foreground" to "#72696A", "red" to "#FD6883")),
        OmarchyTheme("rose-pine", "Rosé Pine", mapOf("background" to "#FAF4ED", "foreground" to "#575279", "accent" to "#56949F", "lighter_background" to "#F2E9E1", "dark_background" to "#EDE7E1", "darker_background" to "#E1DBD5", "bright_foreground" to "#575279", "dark_foreground" to "#9893A5", "red" to "#B4637A")),
        OmarchyTheme("solitude", "Solitude", mapOf("background" to "#101315", "foreground" to "#CACCCC", "accent" to "#798186", "lighter_background" to "#101315", "dark_background" to "#0C0E10", "darker_background" to "#080A0B", "bright_foreground" to "#A5AEB4", "dark_foreground" to "#4B4E55", "red" to "#565D60")),
        OmarchyTheme("starship", "Starship", mapOf("background" to "#1C1C1E", "foreground" to "#F5F5F7", "accent" to "#FF9F0A", "lighter_background" to "#2C2C2E", "dark_background" to "#161618", "darker_background" to "#0E0E10", "bright_foreground" to "#FFFFFF", "dark_foreground" to "#98989D", "red" to "#FF5F4A")),
        OmarchyTheme("tokyo-night", "Tokyo Night", mapOf("background" to "#1A1B26", "foreground" to "#A9B1D6", "accent" to "#7AA2F7", "lighter_background" to "#24283B", "dark_background" to "#13141C", "darker_background" to "#0E0E14", "bright_foreground" to "#C0CAF5", "dark_foreground" to "#565F89", "red" to "#F7768E")),
        OmarchyTheme("vantablack", "Vantablack", mapOf("background" to "#000000", "foreground" to "#FFFFFF", "accent" to "#8D8D8D", "lighter_background" to "#1A1A1A", "dark_background" to "#090909", "darker_background" to "#070707", "bright_foreground" to "#FFFFFF", "dark_foreground" to "#505050", "red" to "#A4A4A4")),
        OmarchyTheme("white", "White", mapOf("background" to "#FFFFFF", "foreground" to "#000000", "accent" to "#6E6E6E", "lighter_background" to "#C0C0C0", "dark_background" to "#F5F5F5", "darker_background" to "#E8E8E8", "bright_foreground" to "#000000", "dark_foreground" to "#C0C0C0", "red" to "#2A2A2A")),
    )

    fun byId(id: String?): OmarchyTheme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT }

    fun next(id: String?): OmarchyTheme {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}
