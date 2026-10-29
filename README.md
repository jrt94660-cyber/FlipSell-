# FlipSell

Paper 26.3 selling GUI and limited-use Sell Wand plugin.

## Version 1.0.2

- Fixed shift-clicking so items can be moved from the player's inventory into the sell GUI; shift-clicking items in the GUI moves them back to the player's inventory.
- Every item in the sell GUI gets one worth line showing the stack total, e.g. a stack of 2 items priced at $1.00 each displays `$2.00`.
- Prices use the item's material type and ignore custom display names, so renamed items are sellable at the matching material's price.
- Keeps the six-row GUI without black filler, lime stained-glass sell button at the bottom-right, and the small-text Sell Wand supporting left- and right-click on chests.

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

Edit `plugins/FlipSell/price.yml`. Prices are per item. A stack's worth is calculated as:

`stack total = configured unit price × stack amount`

Example: `CARROT: 1.0` makes one carrot worth $1.00, two carrots worth $2.00, and 64 carrots worth $64.00. Set a material to `0.0` to make it unsellable. Existing custom prices are preserved during updates.

Install the JAR in `plugins/` and restart the server.