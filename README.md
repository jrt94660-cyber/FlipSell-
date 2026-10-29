# FlipSell

A Paper 26.3 plugin for a six-row sell menu and a limited-use chest Sell Wand inspired by DonutSMP's selling tools.

## Features

- `/sell` opens a 54-slot GUI. Player items go in the first 45 slots; the entire bottom row is reserved and the sell button is always slot 53 (the final slot).
- Per-stack tooltip previews show each item's configured unit sell price and stack total. The button shows the estimated total and item count.
- Clicking **SELL ITEMS** sells only items whose configured price is greater than zero. Any unpriced items stay in the GUI; closing the menu returns remaining items to the player (dropping leftovers if their inventory is full).
- `plugins/FlipSell/price.yml` is generated with a price entry for every item Material exposed by the running Paper server, including legacy-era materials still represented by the current API. New entries are added after updates without overwriting prices you changed.
- `/sellwand give <player> <uses>` gives a netherite-axe-styled Sell Wand. Right-click a chest to sell all priced contents in one use. The chest stays in place, unpriced items stay in the chest, and double chests are supported.
- Vault economy integration and `/flipsell reload`.
- GitHub Actions Maven build on pushes/PRs to `main`.

## Requirements

- Paper 26.3
- Java 25
- Vault and a Vault-compatible economy plugin

No economy transaction is performed if Vault's economy service is missing or rejects the transaction.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sell` | Opens the six-row sell menu | `flipsell.sell` (default: true) |
| `/sellwand give <player> <uses>` | Gives a Sell Wand with the specified number of successful chest-sale uses | `flipsell.sellwand.give` (default: op) |
| `/flipsell reload` | Reloads config and prices | `flipsell.admin` (default: op) |

## Price configuration

Edit `plugins/FlipSell/price.yml`. Each key is a Bukkit Material name and its numeric value is the money paid per item. Set a price to `0` to disable selling that item. Defaults are starter values, not official DonutSMP prices; tune them to your server's economy.

Example:

```yaml
DIAMOND: 12.0
EMERALD: 9.0
WHEAT: 0.12
BEDROCK: 0.0
```

Run `/flipsell reload` after editing prices.