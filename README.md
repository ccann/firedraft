# 🔥 Firedraft

Firedraft (codename) is a realtime web application for 2-player Magic: The
Gathering draft formats written in Clojure and ClojureScript.

## Prerequisites

* JDK 8+
* Clojure build tool, [Leiningen][1] version 2.0 or higher

[1]: https://github.com/technomancy/leiningen

If you have homebrew and cask setup:

```shell
brew cask install java11
brew install leiningen
```

## Running

To start a web server for the application, run:

    lein run

To start a development server for automatic ClojureScript recompilation, run:

    lein figwheel

Visit http://localhost:3000 and your changes will be automatically recompiled and updated in the browser.

## Libraries

Firedraft was created with the Clojure web framework [Luminus](https://luminusweb.com/docs/guestbook), which is really just a collection of libraries. I generated the project via: `lein new luminus firedraft +reagent +site +http-kit`.

Beyond the standard libraries chosen and pulled in by Luminus, these are the libraries I added / chose:

* [HTTP Kit](https://github.com/http-kit/http-kit): A pure clojure web server. I chose this over Jetty because it has
  websocket support and I hit an issue with Aleph that I didn't feel like
  resolving.
* [Reagent](https://holmsand.github.io/reagent/): a CLJS React wrapper. I chose not to use ReFrame because the scope of
  this application is quite small and I don't know it as well.
* [Sente](https://github.com/ptaoussanis/sente): a websockets and HTTP long-polling library. Chosen over Server Sent Events
  because Sente is a rather mature API.


It's also worth mentioning that Luminus comes with the [Bulma](https://bulma.io/) framework for CSS.

## External Resources

* [MTGJSON](https://mtgjson.com/#how-it-works) is used to get the scryfall IDs and other metadata about cards in a set.
* [Scryfall](https://scryfall.com/) is used to get card images and set metadata.

## Backlog

This project is tracked in a [Notion Kanban board](https://www.notion.so/be9a274990b344edb06729bb4a629cef?v=a0a7e1c7895d4bf2bc444c20ac5232dd).

## Contributing

Please follow the official [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide) when writing code.

I would also recommend installing CLJ Kondo:

```shell
brew install borkdude/brew/clj-kondo
```

And subsequently integrating it with your editor.

If you'd like to spin up a local REPL for development please refer to the Luminus
documentation. You can start with `lein repl` (or `lein rebl` for an enhanced
version via Rebel Readline).
