(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST PUT]]
            [planwise.client.api :refer [json-request]]))

(defn- process-filters [filters]
  ; TODO: Make the set->vector conversion generic and move it into an interceptor
  (let [processed-filters (into {} (for [[k v] filters] [k (if (set? v) (apply vector v) v)]))]
    (if (seq (:type filters))
      processed-filters
      (assoc processed-filters :type ""))))


(defn load-project [id with-data & handlers]
  (let [url (str "/api/projects/" id)
        params {:id id
                :with (some-> with-data name)}]
    (GET url (json-request params handlers))))

(defn load-projects [& handlers]
  (GET
    "/api/projects/"
    (json-request {} handlers)))

(defn create-project [params & handlers]
  (POST
    "/api/projects/"
    (json-request params handlers)))

(defn fetch-facilities [filters & handlers]
  (GET
     "/api/facilities/"
     (json-request (process-filters filters) handlers)))

(defn fetch-facilities-with-isochrones [filters isochrone-options & handlers]
  (GET
    "/api/facilities/with-isochrones"
    (json-request (merge isochrone-options (process-filters filters)) handlers)))

(defn fetch-facility-types [& handlers]
  (GET "/api/facilities/types" (json-request {} handlers)))

(defn update-project [project-id filters with-data & handlers]
  (let [url (str "/api/projects/" project-id)
        params {:id project-id
                :filters filters
                :with (some-> with-data name)}]
    (PUT url (json-request params handlers))))
