(ns component.jetty
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.stuartsierra.component :as component]
   [ring.util.servlet :as servlet])
  (:import
   [org.eclipse.jetty.server Server]
   [org.eclipse.jetty.servlet DefaultServlet ServletHolder ServletMapping]
   [org.eclipse.jetty.util.resource Resource ResourceCollection]
   [org.eclipse.jetty.webapp Configuration WebAppContext WebAppClassLoader
    WebInfConfiguration WebXmlConfiguration MetaInfConfiguration
    FragmentConfiguration JettyWebXmlConfiguration]))

(def | (System/getProperty "file.separator"))
(def delim-pattern (re-pattern (format "(?:^%s)|(?:%s$)" | |)))
(def web-app-ignore #{"/WEB-INF" #_"/META-INF" "/.DS_Store"})

(defn join-path [& args]
  {:pre [(every? (comp not nil?) args)]}
  (let [ensure-no-delims #(string/replace % delim-pattern "")]
    (str (when (.startsWith (first args) |) |)
         (string/join | (map ensure-no-delims args)))))

(defn meta-inf-resource [file]
  (Resource/newResource
   (str "jar:file:" (.getCanonicalPath file) "!/META-INF/resources")))

(defn str-path [dir? x]
  (str "/" x (when (dir? x) (if (.endsWith x "/") "*" "/*"))))

(defn gen-mappings* [context meta-conf cloader files]
  (apply concat
         (for [file files
               :let [resource (Resource/newResource file)
                     filename (.getName file)]
               :when (.endsWith filename ".jar")]
           (do
             (.addJars cloader resource)
             (let [meta-inf-resource (meta-inf-resource file)]
               (when (.exists meta-inf-resource)
                 (.addResource meta-conf
                               context
                               WebInfConfiguration/RESOURCE_URLS
                               meta-inf-resource)
                 (map (partial str-path
                               #(.isDirectory (Resource/newResource (str meta-inf-resource %))))
                      (.list meta-inf-resource))))))))

(defn gen-mappings [context meta-conf cloader]
  (concat
   (gen-mappings*
    context meta-conf cloader
    (map io/file (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
   (gen-mappings*
    context meta-conf cloader
    (.listFiles (io/file "META-INF")))))

(defn mappings [resource-paths context meta-conf class-loader]
  (let [root (.getCanonicalPath (io/file "."))]
    (->> resource-paths
         (mapcat (fn [path]
                   (let [path (io/file path)
                         files (.listFiles path)]
                     (map (fn [file]
                            (let [f (io/file file)]
                              (str-path
                               #(.isDirectory (io/file path %))
                               (-> (.getPath f)
                                   (string/replace (.getPath path) "")
                                   (string/replace #"^/" "")))))
                          files))))
         (concat (gen-mappings context meta-conf class-loader))
         (remove nil?)
         (remove web-app-ignore)
         (distinct))))

(defn ^java.net.URL resources
  "Returns the URL for a named resource. Use the context class loader
  if no loader is specified."
  {:added "1.2"}
  ([n] (resources n (.getContextClassLoader (Thread/currentThread))))
  ([n ^ClassLoader loader] (enumeration-seq (.getResources loader n))))

(defn public-resources []
  (for [r (resources "public")
        :let [f (try (io/file r) (catch Exception _))]
        :when f]
    (-> (.getCanonicalPath f)
        (string/replace (.getCanonicalPath (io/file ".")) "")
        (string/replace #"^/" ""))))

(defn web-app-context [public]
  (doto (WebAppContext.)
    (.setContextPath "/")
    (.setBaseResource (ResourceCollection. (into-array String public)))
    (.setInitParameter "aliases" "True")))

(defn ->servlet-mapping [servlet mappings]
  {:pre [(sequential? mappings) (every? string? mappings)]}
  (doto (ServletMapping.)
    (.setServletName servlet)
    (.setPathSpecs (into-array String mappings))))

(defrecord Webserver [port]
  component/Lifecycle
  (start [component]
    (prn "Starting Webserver...")
    (let [handler (-> component :app :handler)
          [path :as public] (public-resources)
          context (web-app-context public)
          class-loader (WebAppClassLoader. context)
          meta-conf (MetaInfConfiguration.)
          mappings (mappings public context meta-conf class-loader)
          server (Server. (or port 0))]
      (println "Mapping default handler:" mappings)
      (.setConfigurationDiscovered context true)
      (.setConfigurations context
                          (into-array Configuration
                                      [(WebInfConfiguration.)
                                       (WebXmlConfiguration.)
                                       meta-conf
                                       (FragmentConfiguration.)
                                       (JettyWebXmlConfiguration.)]))
      (.setClassLoader context class-loader)
      (.addServlet context (ServletHolder. (servlet/servlet handler)) "/*")
      (.addServlet (.getServletHandler context)
                   (doto (ServletHolder. (DefaultServlet.))
                     (.setName "default")))
      (.addServletMapping (.getServletHandler context)
                          (->servlet-mapping "default" mappings))
      (doto server
        (.stop)
        (.setHandler context)
        (.start))
      (let [port (.getLocalPort (first (.getConnectors server)))]
        (println "Started server on port: " port)
        (spit "target/.boot-port" port)
        (assoc component :server server :port port))))
  (stop [component]
    (when-let [server (:server component)]
      (.stop server))))

(defn web-server [port]
  (->Webserver port))
