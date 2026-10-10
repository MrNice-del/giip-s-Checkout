# GiipsCheckout

GiipsCheckout adds recurring item checkouts to Paper servers. It gives players a personal goal and a reason to return: meet the checkout deadline by collecting and submitting the requested items, or receive a temporary server ban.

## Features

- Individual randomized item requirements for each player
- Built-in item pool and probabilities, kept out of `config.yml`
- Separate option to include or exclude End-related items
- Configurable checkout interval, fallback payment, and ban duration
- A protected checkout menu with a dedicated payment slot
- Automatic deadline enforcement, including for offline players
- Action-bar reminders during the final 24 hours
- English messages by default, with an optional German translation
- Staff payment overview and checkout testing commands

## Requirements

- Paper 1.21 through 26.3
- Java 21 for Paper 1.21-1.21.11; Java 25 for Paper 26.1 and newer

The plugin is compiled against the Paper 1.21 API and targets Java 21 bytecode. Newer Paper versions require the appropriate Java version to run the server.

## Installation

1. Download the latest `giipscheckout` JAR from the project's Modrinth page.
2. Place it in your server's `plugins` folder.
3. Start or restart the server.
4. Edit `plugins/giipscheckout/config.yml` as needed.

## Commands

| Command | Alias | Description |
| --- | --- | --- |
| `/payout` | `/bezahlen` | Opens the player's checkout menu. |
| `/checkouttime` | `/zeit` | Shows the time remaining until the next checkout. |
| `/checkoutpayments` | `/zahlungen` | Lists tracked players, payment status, deadlines, and requirements. |
| `/checkoutreload` | None | Reloads the plugin configuration. |
| `/checkouttest` | None | Immediately triggers a checkout ban for testing. |

The test and staff commands are restricted to operators by default.

## Configuration

```yaml
# Available languages: en (default) or de.
language: en

checkout:
  # Time between successful checkouts.
  interval-days: 7
  # Used when randomized requirements are disabled.
  amount: 5
  item: EMERALD
  randomized:
    enabled: true
    # Include End-themed requirements in the built-in pool.
    include-end-items: false

ban:
  duration-days: 7
```

Set `language` to `de` to use German messages. When randomized requirements are disabled, players owe the configured fallback item and amount. When randomization is enabled, each player receives an individual requirement from the built-in pool. The pool, item ranges, and probability weights are not exposed in the configuration.

Players receive their first checkout deadline when the plugin begins tracking them. Successful payments reset the deadline and assign the next requirement. The plugin enforces expired deadlines automatically, so a player does not have to join for an overdue checkout to be processed.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `giipscheckout.payments` | Operators | Use `/checkoutpayments`. |
| `giipscheckout.reload` | Operators | Use `/checkoutreload`. |
| `giipscheckout.test` | Operators | Use `/checkouttest`. |

## Building from source

The project uses Gradle and a Java 21 toolchain. Build the plugin with:

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`. The plugin JAR is written to `build/libs/`.

## License

GiipsCheckout is distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the full license text.
