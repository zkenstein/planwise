(ns planwise.client.mapping
  (:require [reagent.format :as fmt]
            [re-frame.utils :as c]))

(def mapbox-tile-url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}")
(def mapbox-mapid "ggiraldez.056e1919")
(def emerald-mapbox-mapid "ggiraldez.0o0gmkco")
(def gray-mapbox-mapid "ggiraldez.0nk582m0")
(def mapbox-access-token "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA")

(def layer-name
  "kenya")

(def default-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid emerald-mapbox-mapid
                :accessToken mapbox-access-token}])

(def gray-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid gray-mapbox-mapid
                :accessToken mapbox-access-token}])

(def geojson-levels
  {1 {:ub 9, :simplify 0.4, :tileSize 2.0}
   2 {:lb 9, :ub 13, :simplify 0.1, :tileSize 1.0}
   3 {:lb 13, :simplify 0.0, :tileSize 0.5}})

(def geojson-first-level
  (-> geojson-levels keys first))

(defn geojson-level->simplify
  [level]
  (get-in geojson-levels [(js/parseInt level) :simplify]))

(defn simplify->geojson-level
  [simplify]
  (->> geojson-levels
    (filter (fn [[level {s :simplify}]] (= s simplify)))
    (map first)
    first))

(defn static-image [geojson]
  (fmt/format "https://api.mapbox.com/v4/%s/geojson(%s)/auto/256x144.png?access_token=%s"
    emerald-mapbox-mapid
    (js/encodeURIComponent geojson)
    mapbox-access-token))

(defn bbox-center [[[s w] [n e]]]
  [(/ (+ s n) 2.0) (/ (+ e w) 2.0)])

(defn demand-map
  "Returns the full DATAFILE value to provide to mapserver to render a demand map,
   given a demand map key obtained from planwise.component.maps/demand-map-key"
  [map-key]
  (some->> map-key
    (str "demands/")))

(defn region-map
  "Returns the full DATAFILE value to provide to mapserver to render a population region map,
   given the id of the region"
  [region-id]
  (some->> region-id
    (str "populations/")))
