# jetty-component

A Component[1] that runs a ring-servlet with Servlet 3 API.

[1] https://github.com/stuartsierra/component

## Artifacts

`jetty-component` artifacts are [released to Clojars](https://clojars.org/com.andrewmcveigh/jetty-component).

If you are using Maven, add the following repository definition to your `pom.xml`:

``` xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

### The Most Recent Release

With Leiningen:

``` clj
[com.andrewmcveigh/jetty-component "0.1.1"]
```

The most recent release [can be found on Clojars](https://clojars.org/com.andrewmcveigh/jetty-component).

## Usage

A component is a discrete piece of a system. This component needs a
port, and a ring handler. The :app should assoc a :handler to the
component map.

```clojure
(require '[com.stuartsierra.component :as component])
(require '[component.jetty :as jetty])

(defrecord RingApp []
  component/Lifecycle
  (start [component]
    (assoc component :handler ring-handler))
  (stop [component] ...))

(component/system-map
  :schedule ...
  :app (component/using (->RingApp) [:schedule :db])
  :server (component/using (jetty/web-server 8080) [:app])
  :db ...)
```

## License

Copyright © 2014 Andrew Mcveigh

Distributed under the Eclipse Public License, the same as Clojure.
