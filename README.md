# FlipSell

Paper 26.3 selling GUI and limited-use Sell Wand plugin.

## Version 1.0.3

- The sell button now shows exactly one lore line: green dollar sign and white abbreviated total, for example `&a$ &f12.01k`. No item count, action hint, labels, or extra lore lines.
- Large totals use compact suffixes: `k`, `m`, `b`, and `t`.
- The six-row GUI has no filler glass and keeps the lime stained-glass sell button in the bottom-right slot.
- Items in the sell area show their total worth; material prices apply even to renamed items.
- Shift-clicking supports moving items into and out of the sell area.
- Sell Wand retains small Unicode name and left/right-click chest selling.

## Requirements

- Paper 26.3
- Java 25
- Vault and a Vault-compatible economy plugin

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sell` | Open the sell GUI | `flipsell.sell` (default: true) |
| `/sellwand give <player> <uses>` | Give a Sell Wand with a number of successful chest-sale uses | `flipsell.sellwand.give` (default: op) |
| `/flipsell reload` | Reload config and prices | `flipsell.admin` (default: op) |

## Prices

Edit `plugins/FlipSell/price.yml`. Prices are per item. A stack's total is its unit price multiplied by amount. For example, `CARROT: 1.0` makes two carrots worth $2.00 and 64 worth $64.00. Renamed items are priced using their material type. Set a material to `0.0` to disable selling it.

Install the JAR in `plugins/` and restart the server.