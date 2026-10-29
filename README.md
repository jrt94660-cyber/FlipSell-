# FlipSell

Paper selling GUI and limited-use Sell Wand plugin.

## Version 1.0.4

- Compiled against Paper API `1.21.11` and Java 21 to support Paper versions from 1.21.11 through 26.3 (newer versions use Java 25 to run their server; Java 21 bytecode remains compatible).
- The sell button shows exactly one lore line, with a green dollar sign and white abbreviated payout, such as `&a$ &f12.01k`. No count, description, or extra lore lines.
- Uses compact amount suffixes `k`, `m`, `b`, and `t`.
- The six-row GUI has no filler panes and keeps a lime stained-glass sell button at the bottom-right.
- Each item in the sell menu gets a total stack worth. Material prices apply even when an item has a custom name.
- Shift-clicking supports moving items into and out of the sell area.
- The small-text Sell Wand sells priced chest contents on left- or right-click and preserves unsellable items.

## Requirements

- Paper 1.21.11 through Paper 26.3
- Java 21 to compile/run on Paper 1.21.11; Paper 26.x servers themselves require Java 25.
- Vault and a Vault-compatible economy plugin

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sell` | Open the sell GUI | `flipsell.sell` (default: true) |
| `/sellwand give <player> <uses>` | Give a Sell Wand with a number of successful chest-sale uses | `flipsell.sellwand.give` (default: op) |
| `/flipsell reload` | Reload config and prices | `flipsell.admin` (default: op) |

## Prices

Edit `plugins/FlipSell/price.yml`. Prices are per item. The stack worth is its unit price multiplied by the stack amount. For example, `CARROT: 1.0` makes two carrots worth $2.00 and 64 worth $64.00. Renamed items are priced using material type. Set a material to `0.0` to disable selling it.

Install the JAR in the server's `plugins/` folder and restart.