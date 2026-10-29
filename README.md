# FlipSell

Paper 1.21.11 through 26.3 selling GUI and limited-use Sell Wand plugin.

## Version 1.0.5

- Adds and refreshes worth lore for items across every online player's inventory, armor/offhand slots, cursor, and ender chest.
- Worth updates when stacks change and is maintained periodically so consumed, split, or merged stacks show the correct total.
- Worth line is one money-only line, `&a$ &f12.00` for example. The item's displayed name does not affect its price.
- Inventory click, drag, pickup, and drop handling temporarily removes plugin-generated worth lore before vanilla stacking logic, so items can still merge and move normally.
- Sell button stays a lime stained-glass pane in the bottom-right with exactly one abbreviated payout lore line (for example `&a$ &f12.01k`).
- Keeps the six-row sell GUI, editable `price.yml`, Vault payouts, and left/right-click limited-use Sell Wand.

## Requirements

- Paper 1.21.11 through 26.3
- Java 21 for Paper 1.21.11, Java 25 for Paper 26.x servers
- Vault and a Vault-compatible economy plugin

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sell` | Open the sell GUI | `flipsell.sell` (default: true) |
| `/sellwand give <player> <uses>` | Give a Sell Wand with a number of successful chest-sale uses | `flipsell.sellwand.give` (default: op) |
| `/flipsell reload` | Reload config and prices | `flipsell.admin` (default: op) |

## Prices

Edit `plugins/FlipSell/price.yml`. Prices are per item; stack worth is unit price multiplied by amount. For example, `CARROT: 1.0` makes two carrots worth $2.00 and 64 worth $64.00. Renamed items are priced using their material. Set a material to `0.0` to disable selling it.

Install the JAR in `plugins/` and restart the server.