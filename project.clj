(defproject jamesaddinall/jetty-component "0.1.3"
  :description "A Component that runs a ring-servlet with Servlet 3 API."
  :url "http://github.com/jamesaddinall/jetty-component"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.stuartsierra/component "0.2.2"]
                 [ring/ring-servlet "1.3.1" :exclusions [javax.servlet/servlet-api]]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [org.eclipse.jetty/jetty-webapp "8.1.16.v20140903"]]
  :sign-releases false
  )
