(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]
            [planwise.component.runner :refer [run-external]]
            [planwise.boundary.regions :as regions]
            [clojure.string :as str]
            [planwise.util.str :refer [trim-to-int]]
            [digest :as digest]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def scale-saturation
  1001.0)

(defn mapserver-url
  [{config :config}]
  (:mapserver-url config))

(defn calculate-demand?
  [{config :config}]
  (boolean (:calculate-demand config)))

(defn- demand-map-key
  [region-id polygons-with-capacities]
  (digest/sha-256
    (str/join "_" (cons region-id polygons-with-capacities))))

(defn default-capacity
  [{config :config}]
  (:facilities-capacity config))

(defn- data-path
  [{config :config} & args]
  (apply str (:data config) args))

(defn- demands-path
  [service & args]
  (apply data-path service (cons "demands/" args)))

(defn- populations-path
  [service & args]
  (apply data-path service (cons "populations/data/" args)))

(defn- isochrones-path
  [service & args]
  (apply data-path service (cons "isochrones/" args)))

(defn- capacity-for
  [service {:keys [population population-in-region]}]
  (let [capacity (default-capacity service)
        factor   (if population-in-region (/ population-in-region population) 1)]
    (int (* factor capacity))))

(defn- region-saturation
  [{raster-pixel-area :raster-pixel-area}]
  (/
    (* scale-saturation raster-pixel-area)
    1000000.0))

(defn- setup-demands-folder
  [service]
  (let [path (demands-path service "-")]
    (clojure.java.io/make-parents path)))

(defn demand-map
  [service region-id facilities]
  (when (calculate-demand? service)
    (try
      (let [polygons (filter :polygon-id facilities)
            polygons-with-capacities (->> polygons
                                        (map (juxt
                                              #(isochrones-path service region-id "/" (:polygon-id %) ".tif")
                                              #(str (capacity-for service %))))
                                        (flatten))
            map-key (demand-map-key region-id polygons-with-capacities)
            region (regions/find-region (:regions service) region-id)
            saturation (format "%.2f" (region-saturation region))
            _ (setup-demands-folder service)
            response (apply run-external
                        (:runner service)
                        :bin
                        300000
                        "calculate-demand"
                        (demands-path service map-key ".tif")
                        (str saturation)
                        (populations-path service region-id ".tif")
                        (vec polygons-with-capacities))
            unsatisfied-count (trim-to-int response)]
          {:map-key map-key,
           :unsatisfied-count unsatisfied-count})
      (catch Exception e
        (error e "Error calculating demand map for region " region-id "with polygons" (map :polygon-id facilities))
        {}))))

(defrecord MapsService [config runner regions])

(defn maps-service
  "Construct a Maps Service component from config"
  [config]
  (map->MapsService {:config config}))
