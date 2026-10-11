# Bank Steward

Sorts your bank into categories, shows you the finished layout instantly, and guides you through tidying it with as few moves as possible.

<table align="center">
  <tr>
    <td align="center" width="50%"><b>Side panel</b><br><img src="https://raw.githubusercontent.com/iotaaa/bank-steward/main/images/sidebar.png" alt="Side panel"></td>
    <td align="center" width="50%"><b>Organising</b><br><img src="https://raw.githubusercontent.com/iotaaa/bank-steward/main/images/move1.png" alt="Organising"><br><br><b>Move this here</b><br><img src="https://raw.githubusercontent.com/iotaaa/bank-steward/main/images/move2.png" alt="Move this here"></td>
  </tr>
</table>

## Preview

Press **Preview** to see your whole bank already organised, with each category starting on a new row. It opens as a tag tab, so you can withdraw from it straight away without moving anything.

## Organise

Press **Organise** and Bank Steward shows you one move at a time: the item to move, where to drop it, and an arrow between them. It plans the whole bank. Items go into the right tab first, then each tab is put in order, using the fewest moves possible. If you do something different, it just works out the next move.

- Turn on **Insert** mode at the bottom of the bank. Moves inside a tab need it, and the guide reminds you if it's off.
- If the drop target is scrolled out of view, the guide points at the edge of the bank. Hold the item there and the bank scrolls to it.
- The first item in each tab is the tab's icon, so it's left where it is and marked in gold. You can turn this off.
- A progress bar shows how far through you are.

## Categories

There are 11 to start with: Money & clues, Gear, Teleports, Runes & ammo, Potions, Food, Skilling, Resources, Cosmetic, Quest & misc, and High alch. You can rename, recolour, reorder and delete them, add your own, and choose which bank tab each one uses. A category with no tab leaves its items where they are.

You can also give a category name patterns, like `*bones, dragon *`, to catch whole groups of items.

Right-click a bank item to pick its category, or choose **Leave where it is**. Right-click a bank tab to choose which category uses it, or to put every item in it into one category. Items with exactly the same name always move together.

## How items are ordered

- **Gear**: by combat style. Weapons are grouped by kind, armour by set (head to boots), then amulets, rings and capes, strongest first.
- **Skilling and Resources**: skill by skill, with each skill's tools, outfit, bags and materials together. For example: pickaxes, prospector outfit, coal bag, ores, then bars.
- **Teleports**: skill capes, then jewellery with the most charges first, tablets, and anything else with a teleport option.
- **Potions**: in a sensible order, highest dose first.
- **Food**: by how much it heals.
- **Runes & ammo**: runes in spellbook order, then arrows, bolts, darts and so on, from bronze to dragon.
- **Everything else**: items with similar names sit together, like all the cannonballs or all the burnt food.

## Alch Ledger

If you also use [Alch Ledger](https://runelite.net/plugin-hub/show/alch-ledger), the two work together:

- Alch Ledger's bank outlines hide while you're organising, so only the guide's highlights show.
- The guide switches to colours Alch Ledger doesn't use.
- Turn on **File profitable alchs** to put items Alch Ledger shows as profitable into the High alch category, best profit first.

## Settings

- **Bank display:** the category marker style and opacity, a dashed box on items in the wrong tab, the gold mark on tab icons, colour bars under the tab headers, and the right-click menu
- **Guide:** highlight colours, the arrow, and keeping each tab's first item as its icon
- **Sorting:** gear by set and weapon kind, or all of one slot together
- **Alch Ledger:** file profitable alchs

## Author

You can find my socials at [iotaaa.co.uk](https://www.iotaaa.co.uk/).
