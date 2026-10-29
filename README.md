# FlipSell

Paper 1.21.11 through 26.3 selling GUI and limited-use Sell Wand plugin.

## Version 1.0.6

- Fixed armor-slot duplication risk by separating player storage, armor, and offhand updates and avoiding bulk PlayerInventory slot rewrites during click events.
- Worth lore now replaces known category lore such as `Building Blocks` at that same lore position, instead of making category and worth lines swap back and forth.
- Removed legacy appended worth lines and migrates them to category slots when detected.
- Worth lore is refreshed on stack amount changes across storage, armor, offhand, cursor and ender chest.
- Inventory click, drag, pickup and drop handlers strip worth metadata only from items that may participate in the operation, avoiding unrelated armor slot updates.
- Preserves renamed-item selling, editable prices, the six-row GUI, lime glass sell button with a single abbreviated payout line, and left/right-click Sell Wand.

## Requirements

- Paper 1.21.11 through 26.3
- Java 21 for Paper 1.21.11; Java 25 for Paper 26.x servers
- Vault and a Vault-compatible economy plugin

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sell` | Open the sell GUI | `flipsell.sell` (default: true) |
| `/sellwand give <player> <uses>` | Give a Sell Wand with that many successful chest-sale uses | `flipsell.sellwand.give` (default: op) |
| `/flipsell reload` | Reload config and prices | `flipsell.admin` (default: op) |

## Prices

Edit `plugins/FlipSell/price.yml`. Prices are per item; stack worth equals unit price multiplied by amount. For example, `CARROT: 1.0` makes two carrots worth $2.00 and 64 worth $64.00. Renamed items are priced by material. Set a material to `0.0` to disable selling it.

Install the JAR in `plugins/` and restart the server.